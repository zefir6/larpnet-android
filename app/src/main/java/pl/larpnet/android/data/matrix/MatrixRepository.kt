package pl.larpnet.android.data.matrix

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientBuilder
import org.matrix.rustcomponents.sdk.CreateRoomParameters
import org.matrix.rustcomponents.sdk.LatestEventValue
import org.matrix.rustcomponents.sdk.Membership
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.RoomPreset
import org.matrix.rustcomponents.sdk.RoomVisibility
import org.matrix.rustcomponents.sdk.SyncListenerV2
import org.matrix.rustcomponents.sdk.SyncResponseV2
import org.matrix.rustcomponents.sdk.SyncSettingsV2
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.TimelineItemContent
import pl.larpnet.android.data.auth.TokenStore
import pl.larpnet.android.network.FriendicaApi

/**
 * Owns the MatrixRustSDK `Client` lifecycle for native chat -- the Android counterpart of
 * larpnet-iOS's `MatrixClientStore`. Mirrors the web client's `client/src/matrix.js`
 * (`loginAndStart`) as closely as the native SDK's shape allows:
 *
 * - Login is a *fresh* JWT trade (`POST larpnet_matrix`) + `customLoginWithJwt` on every
 *   launch -- never a persisted access token. Reusing the same [TokenStore.matrixDeviceId]
 *   across launches makes Synapse re-issue a token for the same device rather than
 *   registering a new one, so this is cheap and safe (confirmed against the web client's
 *   identical pattern, and against larpnet-iOS's `MatrixClientStore`).
 * - The crypto/session store on disk (`sessionPaths`), keyed by the Matrix user id, DOES
 *   persist across launches -- that's what makes E2EE history survive a relaunch.
 * - No cross-signing / secret-storage bootstrap -- explicit policy carried over from web
 *   (`addon/larpnet_matrix/CLAUDE.md`'s "Why there's no device-verification UI": a single
 *   device sends/receives E2EE fine without it, and an operator-derivable recovery key is a
 *   security regression, not a convenience). `ClientBuilder.autoEnableCrossSigning`/
 *   `autoEnableBackups` are deliberately left at their defaults (off) -- do not turn them on
 *   without re-reading that policy first.
 *
 * Unlike the Swift bindings, every FFI-object-backed type here ([Client], [Room],
 * `Timeline`, [TaskHandle], `TimelineItem`) implements `Disposable`/`AutoCloseable` and leaks
 * native memory if never closed -- there's no ARC on this side. [Room] instances are always
 * used inside a `.use { }` block (or explicitly closed) once whatever's needed from them is
 * read; [Client] is long-lived and closed only in [clearSession]; [TaskHandle] is cancelled
 * *and* closed together, since `cancel()` alone stops the loop but doesn't free the wrapper.
 *
 * ## KNOWN BLOCKER, confirmed live against test.larpnet.pl on 2026-09-24 (not yet resolved)
 *
 * [ensureClient]'s `ClientBuilder().build()` call throws
 * `"Expect rustls-platform-verifier to be initialized"` on every real device/emulator run --
 * every other piece (login JWT minting, room list, timeline diffs, send) is confirmed working
 * via the iOS/web siblings and this file's own architecture, but this one call never
 * completes, so nothing downstream of it has been live-verified on Android yet.
 *
 * Root cause, as far as diagnosed: `sdk-android`'s default TLS path shells out to the
 * `rustls-platform-verifier` crate's Android backend, which needs a `JavaVM*` captured via a
 * classic JNI `JNI_OnLoad` at some point in the process's life. This SDK's own Kotlin<->Rust
 * calls all go through JNA (`com.sun.jna.Native.load` -- confirmed via the generated bindings
 * importing `com.sun.jna.Library`), which loads the `.so` via `dlopen()` directly and never
 * triggers `JNI_OnLoad`.
 *
 * Things already tried here and ruled out:
 * - Vendoring `org.rustls.platformverifier.CertificateVerifier` (the Kotlin-side JNI target
 *   class rustls-platform-verifier calls into by name) -- necessary, since Element X Android
 *   vendors this exact file for the same reason (see their `libraries/rustls-tls` module,
 *   `docs/_developer_onboarding.md`'s "rustls and platform verifier" section), and it's kept
 *   in this repo too (`org/rustls/platformverifier/`) -- but not sufficient on its own: the
 *   panic persists even with the class present, confirmed live.
 * - `ClientBuilder.addRootCertificates(<system trust store certs>)` as a way to sidestep
 *   `rustls-platform-verifier` entirely -- this changes the failure to a *different* exception
 *   (`ClientBuildException.ServerUnreachable("builder error")`), suggesting at least one
 *   internal HTTP client `ClientBuilder.build()` constructs doesn't pick up the custom root
 *   store and still hits the same underlying gap.
 * - An explicit `System.loadLibrary("matrix_sdk_ffi")` call before touching `ClientBuilder`,
 *   on the theory that *any* loader triggering `JNI_OnLoad` for the already-`dlopen()`'d `.so`
 *   would be enough, regardless of which loader (JNA vs `System.loadLibrary`) actually
 *   requested it first -- no change, panic persists.
 *
 * Element X Android's own `RustMatrixClientFactory.getBaseClientBuilder()` (confirmed via
 * their public source, 2026-09-24) calls plain `ClientBuilder()` with no TLS-related
 * workaround at all, so *something* about their setup avoids this that hasn't been identified
 * yet -- possibly a different SDK build/publishing pipeline for their AAR, a newer/older
 * `sdk-android` release, or an Android version/toolchain difference in how the native library
 * gets loaded. Re-check whether a newer `sdk-android` release fixes this before spending more
 * time on it; if not, this likely needs either an upstream fix (file against
 * matrix-org/matrix-rust-components-kotlin) or a custom native shim exporting a real
 * `JNI_OnLoad` -- real native/NDK work, out of scope for a Kotlin-only pass.
 */
class MatrixRepository(
    private val context: Context,
    private val tokenStore: TokenStore,
    private val apiProvider: () -> FriendicaApi,
) {
    private var client: Client? = null
    private var syncHandle: TaskHandle? = null

    /** nickname (lowercased) -> Friendica display name, from the same `contacts` list the web
     * client's `resolveDisplayName()` uses -- covers anyone who's never opened chat themselves
     * and so has no Matrix displayname yet. */
    private var contactsByLocalpart: Map<String, String> = emptyMap()

    var serverName: String? = null
        private set

    private val _roomListUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fires (with no payload -- just a "something changed, go re-fetch" signal) after every
     * sync response lands, so `ChatViewModel` can refresh the room list live. */
    val roomListUpdates: SharedFlow<Unit> = _roomListUpdates.asSharedFlow()

    /** Logs in if needed (idempotent -- returns the existing client on every call after the
     * first this launch) and makes sure the background sync loop is running. */
    suspend fun ensureClient(): Client {
        client?.let { return it }

        val identity = apiProvider().matrixLogin()
        contactsByLocalpart = identity.contacts.associate { it.nickname.lowercase() to it.name }
        val resolvedServerName = identity.userId.substringAfter(':', missingDelimiterValue = "")
            .ifEmpty { error("Malformed Matrix identity: ${identity.userId}") }
        serverName = resolvedServerName

        val sessionDir = sessionDirectory(identity.userId)
        val newClient = ClientBuilder()
            .homeserverUrl(identity.homeserver)
            .sessionPaths(sessionDir.absolutePath, sessionDir.absolutePath)
            .build()
        newClient.customLoginWithJwt(identity.token, "larpnet Android", deviceId())

        // One blocking sync before this call returns -- rooms()/getDmRoom()/getRoom() all
        // read local state that only exists once at least one sync has landed. Confirmed
        // against larpnet-iOS's `MatrixClientStore` (same fix, found the same way there: a
        // caller that logs in and immediately checks rooms() races an empty local store
        // without this). The continuous background loop below is what keeps that state live
        // afterward.
        newClient.syncOnceV2(SyncSettingsV2(fullState = true))

        client = newClient
        startSyncLoop(newClient)
        return newClient
    }

    suspend fun rooms(): List<ChatRoom> {
        val activeClient = ensureClient()
        val result = mutableListOf<ChatRoom>()
        for (room in activeClient.rooms()) {
            room.use {
                if (it.membership() == Membership.JOINED) {
                    val name = displayNameFor(it)
                    val (previewText, timestampMillis) = previewFor(it)
                    result.add(ChatRoom(id = it.id(), name = name, preview = previewText, timestampMillis = timestampMillis))
                }
            }
        }
        return result.sortedByDescending { it.timestampMillis ?: Long.MIN_VALUE }
    }

    /**
     * Finds the existing 1:1 room with this nickname, or creates one -- same "1:1 == exactly
     * this other member" heuristic and encrypted-by-default behavior as the web client's
     * `findOrCreateDirectRoom()`. [nickname] is a plain Friendica nickname (e.g. from
     * `Account.username`), not a full mxid -- this builds the mxid itself from [serverName],
     * same division of responsibility as `larpnet_matrix_dm_localpart()`'s doc comment
     * describes for the web client.
     */
    suspend fun openOrCreateDirectRoom(nickname: String): String {
        val activeClient = ensureClient()
        val resolvedServerName = serverName ?: error("Not logged in")
        val targetMxid = "@${nickname.lowercase()}:$resolvedServerName"

        activeClient.getDmRoom(targetMxid)?.use { return it.id() }

        return activeClient.createRoom(
            CreateRoomParameters(
                name = null,
                isEncrypted = true,
                isDirect = true,
                visibility = RoomVisibility.Private,
                preset = RoomPreset.PRIVATE_CHAT,
                invite = listOf(targetMxid),
            ),
        )
    }

    suspend fun openTimeline(roomId: String): ChatTimelineHandle {
        val activeClient = ensureClient()
        val room = activeClient.getRoom(roomId) ?: error("Room not found: $roomId")
        val timeline = try {
            room.timeline()
        } finally {
            room.close()
        }
        val handle = ChatTimelineHandle(timeline)
        handle.start()
        return handle
    }

    /**
     * Called alongside [TokenStore.clear] at logout (see `SettingsScreen`) -- deletes the
     * on-disk crypto store and the persisted device id, so a different account logging into
     * this device next doesn't inherit either. Best-effort server-side `logout()` first (so
     * the device is also cleanly removed from the account), but the local cleanup below runs
     * regardless of whether that network call succeeds.
     */
    fun clearSession() {
        syncHandle?.cancel()
        syncHandle?.close()
        syncHandle = null
        contactsByLocalpart = emptyMap()
        serverName = null

        val oldClient = client
        client = null

        CoroutineScope(Dispatchers.IO).launch {
            val userId = runCatching { oldClient?.userId() }.getOrNull()
            runCatching { oldClient?.logout() }
            oldClient?.close()
            userId?.let { sessionDirectory(it).deleteRecursively() }
        }
        tokenStore.clearMatrixDeviceId()
    }

    private fun startSyncLoop(client: Client) {
        syncHandle?.cancel()
        syncHandle?.close()
        syncHandle = client.syncV2(
            SyncSettingsV2(fullState = false),
            object : SyncListenerV2 {
                override fun onUpdate(response: SyncResponseV2) {
                    _roomListUpdates.tryEmit(Unit)
                }
            },
        )
    }

    private fun deviceId(): String {
        tokenStore.matrixDeviceId?.let { return it }
        val generated = UUID.randomUUID().toString()
        tokenStore.matrixDeviceId = generated
        return generated
    }

    /** One app-private subdirectory per Matrix user id -- reused across launches (see this
     * class's own doc comment) and removed wholesale by [clearSession]. */
    private fun sessionDirectory(userId: String): File {
        val safe = userId.removePrefix("@").replace(":", "_")
        val dir = File(context.filesDir, "matrix_session/$safe")
        dir.mkdirs()
        return dir
    }

    /** Room-list display name -- same algorithm as the web client's `roomDisplayName()`: for
     * a 1:1 DM, prefer the Friendica name we already know (covers a partner who's never
     * opened chat themselves, so has no Matrix displayname yet) over the SDK's own hero-based
     * summary; fall back to the SDK's computed name (handles group rooms) otherwise. */
    private suspend fun displayNameFor(room: Room): String {
        val heroes = room.heroes()
        if (heroes.size == 1) {
            val hero = heroes[0]
            val localpart = localpartOf(hero.userId)
            contactsByLocalpart[localpart]?.let { return it }
            hero.displayName?.takeIf { it.isNotBlank() }?.let { return it }
            return localpart ?: hero.userId
        }
        room.displayName()?.takeIf { it.isNotBlank() }?.let { return it }
        return "Rozmowa"
    }

    private suspend fun previewFor(room: Room): Pair<String?, Long?> = when (val latest = room.latestEvent()) {
        is LatestEventValue.None -> null to null
        is LatestEventValue.RemoteInvite -> null to latest.timestamp.toLong()
        is LatestEventValue.Remote -> previewText(latest.content) to latest.timestamp.toLong()
        is LatestEventValue.Local -> previewText(latest.content) to latest.timestamp.toLong()
    }

    private fun previewText(content: TimelineItemContent): String? {
        val msgLike = (content as? TimelineItemContent.MsgLike)?.content ?: return null
        return when (val kind = msgLike.kind) {
            is MsgLikeKind.Message -> kind.content.body
            is MsgLikeKind.UnableToDecrypt -> "🔒"
            else -> null
        }
    }

    private fun localpartOf(mxid: String): String? {
        if (!mxid.startsWith("@")) return null
        val colon = mxid.indexOf(':')
        if (colon < 0) return null
        return mxid.substring(1, colon).lowercase()
    }
}
