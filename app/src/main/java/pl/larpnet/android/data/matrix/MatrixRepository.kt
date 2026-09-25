package pl.larpnet.android.data.matrix

import android.content.Context
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientBuilder
import org.matrix.rustcomponents.sdk.CreateRoomParameters
import org.matrix.rustcomponents.sdk.EnableRecoveryProgress
import org.matrix.rustcomponents.sdk.EnableRecoveryProgressListener
import org.matrix.rustcomponents.sdk.LatestEventValue
import org.matrix.rustcomponents.sdk.Membership
import org.matrix.rustcomponents.sdk.MembershipState
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.RecoveryState
import org.matrix.rustcomponents.sdk.RecoveryStateListener
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.RoomMember
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
 * - `ClientBuilder.autoEnableCrossSigning`/`autoEnableBackups` are deliberately left at their
 *   defaults (off) -- no interactive device-verification (SAS/emoji) UI, same policy as web
 *   (`addon/larpnet_matrix/CLAUDE.md`'s "Why there's no device-verification UI"). This does NOT
 *   mean no recovery key at all, though: the actual policy (see that same doc, updated once web
 *   shipped user-chosen recovery passphrases) is "no *operator-derivable* key" -- a recovery
 *   key/passphrase the user generates and holds themselves, never sent to or knowable by the
 *   server, is fine and is what [setUpRecovery]/[restoreRecovery]/[resetRecovery] below
 *   implement (mirroring web's `client/src/recovery.js`). Confirmed via a live spike against
 *   test.larpnet.pl (2026-09-25, on iOS -- same underlying Rust crate/FFI shape here) that
 *   `Encryption.enableRecovery()` does NOT hit the same JWT/UIA wall `bootstrapCrossSigning()`
 *   does on web -- it's a plain secret-storage/backup operation, not a cross-signing key upload.
 *
 * Unlike the Swift bindings, every FFI-object-backed type here ([Client], [Room],
 * `Timeline`, [TaskHandle], `TimelineItem`) implements `Disposable`/`AutoCloseable` and leaks
 * native memory if never closed -- there's no ARC on this side. [Room] instances are always
 * used inside a `.use { }` block (or explicitly closed) once whatever's needed from them is
 * read; [Client] is long-lived and closed only in [clearSession]; [TaskHandle] is cancelled
 * *and* closed together, since `cancel()` alone stops the loop but doesn't free the wrapper.
 *
 * ## `rustls-platform-verifier` blocker -- RESOLVED 2026-09-25
 *
 * [ensureClient]'s `ClientBuilder().build()` used to throw
 * `"Expect rustls-platform-verifier to be initialized"` on every real device/emulator run.
 * Earlier investigation here suspected a JNA-vs-JNI_OnLoad native-loading gap and concluded it
 * likely needed real native/NDK work -- that diagnosis was a dead end. The actual root cause,
 * found by reading `matrix-rust-sdk`'s own source
 * (`bindings/matrix-sdk-ffi/src/platform/{mod,android_platform}.rs`): the SDK exposes a public
 * FFI function, `initPlatform(TracingConfiguration, useLightweightTokioRuntime: Boolean)`, that
 * must be called *once*, before building any `Client`. On Android, it internally finds the
 * already-running JVM via `JNI_GetCreatedJavaVMs` (a process-wide JNI API -- no `Context` needs
 * to be passed in, and it doesn't depend on `JNI_OnLoad` firing at all) and calls
 * `rustls_platform_verifier::android::init_hosted()` to wire up the JNI bridge to
 * `org.rustls.platformverifier.CertificateVerifier` (vendored in this repo at
 * `org/rustls/platformverifier/`). This app was simply never calling it -- see
 * [pl.larpnet.android.App]'s `initMatrixPlatform()`, called once from `Application.onCreate()`.
 *
 * Confirmed live on the Android emulator: `ensureClient()` completes, the room list loads real
 * rooms from test.larpnet.pl, an existing E2EE message decrypts to plain text (not the
 * `unableToDecrypt` "🔒" fallback), and sending a fresh message round-trips correctly.
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

    /**
     * Members (join+invite, excluding self) plus the raw room-name state event and whether
     * this is a group (more than one other member) -- mirrors the web client's
     * `RoomInfoModal.jsx` (`others`/`isGroup` computed the same way). [ChatRoomInfo.rawName],
     * not `room.displayName()`, because for a 1:1 DM `displayName()` always prefers the other
     * person's own name -- same reason `RoomInfoModal.jsx` hides rename there.
     */
    suspend fun roomInfo(roomId: String): ChatRoomInfo {
        val activeClient = ensureClient()
        val room = activeClient.getRoom(roomId) ?: error("Room not found: $roomId")
        return try {
            val selfId = activeClient.userId()
            val iterator = room.members()
            val all = mutableListOf<RoomMember>()
            while (true) {
                val chunk = iterator.nextChunk(100u)
                if (chunk.isNullOrEmpty()) break
                all.addAll(chunk)
            }
            val others = all.filter {
                (it.membership == MembershipState.Join || it.membership == MembershipState.Invite) && it.userId != selfId
            }
            val members = others.map { ChatRoomMember(userId = it.userId, displayName = resolvedName(it.userId, it.displayName)) }
            ChatRoomInfo(roomId = roomId, rawName = room.rawName().orEmpty(), isGroup = members.size != 1, members = members)
        } finally {
            room.close()
        }
    }

    suspend fun renameRoom(roomId: String, name: String) {
        val activeClient = ensureClient()
        val room = activeClient.getRoom(roomId) ?: error("Room not found: $roomId")
        try {
            room.setName(name)
        } finally {
            room.close()
        }
    }

    /** [nickname] is a plain Friendica nickname, same convention as [openOrCreateDirectRoom]. */
    suspend fun inviteMember(roomId: String, nickname: String) {
        val activeClient = ensureClient()
        val room = activeClient.getRoom(roomId) ?: error("Room not found: $roomId")
        val resolvedServerName = serverName ?: error("Not logged in")
        try {
            room.inviteUserById("@${nickname.lowercase()}:$resolvedServerName")
        } finally {
            room.close()
        }
    }

    suspend fun removeMember(roomId: String, userId: String) {
        val activeClient = ensureClient()
        val room = activeClient.getRoom(roomId) ?: error("Room not found: $roomId")
        try {
            room.kickUser(userId, null)
        } finally {
            room.close()
        }
    }

    suspend fun leaveRoom(roomId: String) {
        val activeClient = ensureClient()
        val room = activeClient.getRoom(roomId) ?: error("Room not found: $roomId")
        try {
            room.leave()
        } finally {
            room.close()
        }
    }

    /**
     * Which recovery prompt (if any) `ChatScreen` should show right after login -- mirrors the
     * web client's `getRecoveryStatus()`/`recoveryPrompt` (`recovery.js`/`App.jsx`).
     * [RecoveryPromptKind.NEEDS_SETUP] means this account has never set up recovery anywhere;
     * [RecoveryPromptKind.NEEDS_RESTORE] means recovery exists (set up on another device, or by
     * this device in a past install) but this device hasn't unlocked it yet. `null` means
     * either it's already unlocked here, or the state is still `UNKNOWN` (nothing to prompt).
     */
    enum class RecoveryPromptKind { NEEDS_SETUP, NEEDS_RESTORE }

    suspend fun recoveryPromptKind(): RecoveryPromptKind? = when (waitForRecoveryState()) {
        RecoveryState.DISABLED -> RecoveryPromptKind.NEEDS_SETUP
        RecoveryState.INCOMPLETE -> RecoveryPromptKind.NEEDS_RESTORE
        RecoveryState.ENABLED, RecoveryState.UNKNOWN -> null
    }

    /**
     * Sets up recovery for the first time on this account (`RecoveryState.DISABLED`) -- a
     * random key if [passphrase] is null, otherwise derived from the phrase. Returns the
     * encoded recovery key/phrase to show the user once (there's no way to see it again).
     */
    suspend fun setUpRecovery(passphrase: String?): String {
        val activeClient = ensureClient()
        return activeClient.encryption().enableRecovery(true, passphrase, NoOpRecoveryProgressListener)
    }

    /**
     * Unlocks this device's access to existing cross-device history, using either the raw
     * recovery key or the original passphrase -- `Encryption.recover()` accepts either as the
     * same string (the underlying Rust crate's own doc comment gives the exact example
     * `recovery.recover("my recovery key or passphrase")`).
     */
    suspend fun restoreRecovery(input: String) {
        ensureClient().encryption().recover(input)
    }

    /**
     * Resets recovery when the user has forgotten their key/phrase -- same scope as web's
     * `resetRecovery()` (see its doc comment in `client/src/recovery.js`/the addon's
     * `CLAUDE.md`): this is "let me set a new key", not a guarantee that a device which already
     * has the old keys locally loses access to old history. `resetRecoveryKey()`/
     * `recoverAndReset()` exist on this SDK but don't accept a passphrase -- a custom-passphrase
     * reset goes through disable-then-enable instead, which reaches the same end state (a fresh
     * secret-storage key/backup version) via the same path [setUpRecovery] already uses.
     */
    suspend fun resetRecovery(passphrase: String?): String {
        val activeClient = ensureClient()
        val encryption = activeClient.encryption()
        encryption.disableRecovery()
        return encryption.enableRecovery(true, passphrase, NoOpRecoveryProgressListener)
    }

    /**
     * [Encryption.recoveryState] starts at `UNKNOWN` right after login until the SDK's
     * background crypto tasks resolve it -- waits for that via [RecoveryStateListener] rather
     * than polling.
     */
    private suspend fun waitForRecoveryState(): RecoveryState {
        val activeClient = ensureClient()
        val encryption = activeClient.encryption()
        return suspendCancellableCoroutine { continuation ->
            // Registers the listener *before* checking the current value (rather than the
            // other way around) so a transition happening in between the two can't be missed.
            val fired = AtomicBoolean(false)
            var handle: TaskHandle? = null
            val listener = object : RecoveryStateListener {
                override fun onUpdate(status: RecoveryState) {
                    if (status == RecoveryState.UNKNOWN) return
                    if (fired.compareAndSet(false, true)) {
                        handle?.cancel()
                        continuation.resume(status)
                    }
                }
            }
            handle = encryption.recoveryStateListener(listener)
            val current = encryption.recoveryState()
            if (current != RecoveryState.UNKNOWN && fired.compareAndSet(false, true)) {
                handle.cancel()
                continuation.resume(current)
            }
        }
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
            return resolvedName(heroes[0].userId, heroes[0].displayName)
        }
        room.displayName()?.takeIf { it.isNotBlank() }?.let { return it }
        return "Rozmowa"
    }

    /** Shared by the room-list hero name above and [roomInfo]'s member list: prefer the
     * Friendica name we already know (covers a member who's never opened chat themselves, so
     * has no Matrix displayname yet) over the SDK-reported displayname, then fall back to the
     * bare localpart. */
    private fun resolvedName(userId: String, fallbackDisplayName: String?): String {
        val localpart = localpartOf(userId)
        contactsByLocalpart[localpart]?.let { return it }
        fallbackDisplayName?.takeIf { it.isNotBlank() }?.let { return it }
        return localpart ?: userId
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

/** [MatrixRepository.setUpRecovery]/[MatrixRepository.resetRecovery] don't need progress
 * updates -- the caller already shows its own busy state while awaiting the suspend call. */
private object NoOpRecoveryProgressListener : EnableRecoveryProgressListener {
    override fun onUpdate(status: EnableRecoveryProgress) {}
}
