package pl.larpnet.android.data.matrix

import android.content.Context
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientBuilder
import org.matrix.rustcomponents.sdk.CreateRoomParameters
import org.matrix.rustcomponents.sdk.EnableRecoveryProgress
import org.matrix.rustcomponents.sdk.EnableRecoveryProgressListener
import org.matrix.rustcomponents.sdk.HttpPusherData
import org.matrix.rustcomponents.sdk.LatestEventValue
import org.matrix.rustcomponents.sdk.Membership
import org.matrix.rustcomponents.sdk.MediaSource
import org.matrix.rustcomponents.sdk.MembershipState
import org.matrix.rustcomponents.sdk.MessageLikeEventContent
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.NotificationEvent
import org.matrix.rustcomponents.sdk.NotificationProcessSetup
import org.matrix.rustcomponents.sdk.NotificationStatus
import org.matrix.rustcomponents.sdk.PushFormat
import org.matrix.rustcomponents.sdk.PusherIdentifiers
import org.matrix.rustcomponents.sdk.PusherKind
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
import org.matrix.rustcomponents.sdk.TimelineEventContent
import org.matrix.rustcomponents.sdk.TimelineItemContent
import pl.larpnet.android.BuildConfig
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
private const val MAX_SERVER_BACKUP_DELETE_ATTEMPTS = 20

class MatrixRepository(
    private val context: Context,
    private val tokenStore: TokenStore,
    private val apiProvider: () -> FriendicaApi,
) {
    private var client: Client? = null
    private var syncHandle: TaskHandle? = null

    /** Guards [ensureClient]'s build-and-login section -- see that function's doc comment for
     * why this is required, not just a defensive nicety. */
    private val clientMutex = Mutex()

    /** Used only by [deleteAllServerSideBackups] -- a handful of plain authenticated REST calls
     * for a rare, one-shot user action, not worth threading a shared `OkHttpClient` through this
     * class's constructor for. */
    private val backupCleanupHttpClient = OkHttpClient()

    /** nickname (lowercased) -> Friendica display name, from the same `contacts` list the web
     * client's `resolveDisplayName()` uses -- covers anyone who's never opened chat themselves
     * and so has no Matrix displayname yet. */
    private var contactsByLocalpart: Map<String, String> = emptyMap()

    var serverName: String? = null
        private set

    /** This deployment's Matrix push gateway URL (already includes the shared secret as a
     * query param -- see `larpnet_matrix_push_gateway_url()` server-side), or null if the
     * server hasn't got push configured yet. Set once per [ensureClient] login, same lifetime
     * as [serverName]. */
    private var pushGatewayUrl: String? = null

    private val _roomListUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fires (with no payload -- just a "something changed, go re-fetch" signal) after every
     * sync response lands, so `ChatViewModel` can refresh the room list live. */
    val roomListUpdates: SharedFlow<Unit> = _roomListUpdates.asSharedFlow()

    private val _unreadCount = MutableStateFlow(0)

    /** Sum of [ChatRoom.unreadCount] across every room, updated on every [rooms] call -- drives
     * the chat entry point's badge total (the Android counterpart has no dedicated bottom tab
     * for Chat, unlike iOS, so this badges the icon button that opens it instead). */
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    /**
     * Logs in if needed (idempotent -- returns the existing client on every call after the
     * first this launch) and makes sure the background sync loop is running.
     *
     * Guarded by [clientMutex]: [ChatViewModel]'s `init` fires `refresh()` (-> [rooms]) and
     * `checkRecovery()` (-> [recoveryPromptKind]) as two separate, un-awaited
     * `viewModelScope.launch { }` coroutines, both of which call this function. Without the
     * lock, a genuinely first-ever login (client still null in both) let both coroutines race
     * past the null-check and each call `ClientBuilder().build()` against the *same*
     * [sessionDirectory] concurrently -- confirmed live: the loser's SQLite migration failed
     * outright with "Failed to run migrations: table \"kv\" already exists" (the winner had
     * just created it moments earlier), leaving chat completely unusable on a fresh
     * account+device. Never surfaced in any earlier testing this session because every prior
     * check happened to run against an *already*-initialized client from an earlier login in
     * the same process, where the `client?.let { return it }` fast path always won before the
     * race window mattered.
     */
    suspend fun ensureClient(): Client = clientMutex.withLock {
        client?.let { return@withLock it }

        val identity = apiProvider().matrixLogin()
        contactsByLocalpart = identity.contacts.associate { it.nickname.lowercase() to it.name }
        val resolvedServerName = identity.userId.substringAfter(':', missingDelimiterValue = "")
            .ifEmpty { error("Malformed Matrix identity: ${identity.userId}") }
        serverName = resolvedServerName
        pushGatewayUrl = identity.pushGatewayUrl

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
        newClient
    }

    suspend fun rooms(): List<ChatRoom> {
        val activeClient = ensureClient()
        val result = mutableListOf<ChatRoom>()
        for (room in activeClient.rooms()) {
            room.use {
                if (it.membership() == Membership.JOINED) {
                    val name = displayNameFor(it)
                    val (previewText, timestampMillis) = previewFor(it)
                    val unread = runCatching { it.roomInfo().numUnreadMessages.toInt() }.getOrDefault(0)
                    val avatarUrl = avatarUrlFor(it)
                    result.add(
                        ChatRoom(
                            id = it.id(), name = name, preview = previewText,
                            timestampMillis = timestampMillis, unreadCount = unread, avatarUrl = avatarUrl,
                        ),
                    )
                }
            }
        }
        _unreadCount.value = result.sumOf { it.unreadCount }
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
     * has the old keys locally loses access to old history.
     *
     * Two distinct problems had to be fixed here, both confirmed by reading the actual Rust
     * source (`Backups`/`Recovery` in `matrix-sdk`), not guessed:
     *
     * 1. **Server-side backup deletion must not trust the local crypto store.**
     *    `Backups::disable()` only deletes the *one* backup version this device's local crypto
     *    store currently happens to know about; it never asks the server what actually exists.
     *    A stale local record (plausible on any account that's been through several earlier
     *    reset attempts) makes disabling "succeed" while an orphaned version remains on the
     *    server, and the next `enableRecovery()` correctly refuses to overwrite it
     *    (`BackupExistsOnServer`). Fixed the same way `matrix-js-sdk` already does it
     *    (`deleteAllKeyBackupVersions()` in `rust-crypto/backup.js`): ask the *server* directly,
     *    in a loop -- "what's the current version? delete it. ask again. repeat until there
     *    isn't one." The Rust SDK's own equivalent (`Backups::disable_and_delete()`) exists but
     *    was never exposed through this FFI, so [deleteAllServerSideBackups] replicates it with
     *    plain authenticated HTTP calls using the session's own access token.
     *
     * 2. **`enableRecovery()` must be told the old backup is gone, not just have it deleted out
     *    from under it.** `Enable`'s Rust source only touches the backup at all when the
     *    *local* `backups().are_enabled()` flag is false -- if a previous session already
     *    activated a backup, that flag stays true regardless of what [deleteAllServerSideBackups]
     *    just did to the server, and `enableRecovery()` silently skips recreating a backup
     *    entirely, rotating only the secret-storage key. Confirmed live: two resets in a row
     *    each returned a distinct-looking "new" recovery key while zero calls touched
     *    `room_keys/version` and the account was left with no working backup at all -- worse
     *    than the original bug, since it also discards whatever backup existed. Fixed by calling
     *    `disableRecovery()` first purely to flip that local flag to false; its own server-side
     *    deletion is the same unreliable one-version attempt as above, which is why
     *    [deleteAllServerSideBackups] still runs unconditionally afterward. `disableRecovery()`
     *    is expected to throw here (e.g. `BackupNotEnabled` when local state was already stale,
     *    exactly the account shape problem 1 fixes) -- read from source that the local flag flips
     *    to `Unknown` before that error ever propagates, so the throw is safe to ignore.
     */
    suspend fun resetRecovery(passphrase: String?): String {
        val activeClient = ensureClient()
        try {
            activeClient.encryption().disableRecovery()
        } catch (e: Exception) {
            // Expected -- see doc comment point 2. The local "backup enabled" flag is already
            // flipped to false by this point regardless of why this threw.
        }
        deleteAllServerSideBackups()
        return activeClient.encryption().enableRecovery(true, passphrase, NoOpRecoveryProgressListener)
    }

    @Serializable
    private data class BackupVersionResponse(val version: String)

    /**
     * Deletes every key-backup version this account has on the server, asking the server fresh
     * each time rather than trusting the SDK's local cache -- see [resetRecovery]'s doc comment
     * for the full "why". Bypasses `Encryption`/`Backups` entirely via plain authenticated
     * Matrix Client-Server API calls, using the already-logged-in session's own access token
     * ([Client.session]) against its own homeserver ([Client.homeserver]) -- no new auth, just
     * the same credentials the SDK is already using internally.
     *
     * Capped at [MAX_SERVER_BACKUP_DELETE_ATTEMPTS] rounds as a sanity backstop against a
     * genuinely pathological server response -- never expected to matter in practice.
     */
    private suspend fun deleteAllServerSideBackups() {
        val activeClient = ensureClient()
        val accessToken = activeClient.session().accessToken
        val homeserver = activeClient.homeserver().trimEnd('/')
        val json = Json { ignoreUnknownKeys = true }
        val versionUrl = "$homeserver/_matrix/client/v3/room_keys/version"

        fun authedRequest(url: String) = Request.Builder().url(url).header("Authorization", "Bearer $accessToken")

        suspend fun currentVersion(): String? = withContext(Dispatchers.IO) {
            backupCleanupHttpClient.newCall(authedRequest(versionUrl).build()).execute().use { response ->
                when {
                    response.code == 404 -> null
                    response.isSuccessful -> json.decodeFromString<BackupVersionResponse>(response.body!!.string()).version
                    else -> null // Unexpected shape -- don't loop on something we can't interpret.
                }
            }
        }

        for (unused in 0 until MAX_SERVER_BACKUP_DELETE_ATTEMPTS) {
            val version = currentVersion() ?: return
            val deleteUrl = "$homeserver/_matrix/client/v3/room_keys/version/$version"
            val deleted = withContext(Dispatchers.IO) {
                backupCleanupHttpClient.newCall(authedRequest(deleteUrl).delete().build()).execute().use { it.isSuccessful }
            }
            if (!deleted) break // A delete that didn't actually succeed isn't safe to loop past silently.
        }

        // Re-check once more: either we exhausted the attempt cap, or a delete failed above --
        // confirm the end state before deciding whether this is actually a problem.
        if (currentVersion() != null) {
            error("Couldn't clear all server-side key backups before resetting recovery")
        }
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
        // Same Friendica-name-first resolution the room list uses (resolvedName, via
        // displayNameFor) -- without this, a per-message sender falls back straight to their
        // bare mxid localpart whenever they haven't set a Matrix displayname yet (the common
        // case for anyone who's never opened chat themselves), instead of the full name the
        // room list already knows how to show.
        val handle = ChatTimelineHandle(timeline, resolveDisplayName = ::resolvedName)
        handle.start()
        return handle
    }

    /**
     * Registers this device's FCM token as a Matrix pusher, so Synapse starts calling
     * larpnet_matrix's push gateway for new messages in any room this account is in. A no-op
     * if the server hasn't got the gateway configured yet ([pushGatewayUrl] null) -- same
     * "safe until configured" convention the gateway itself follows.
     *
     * `format = EVENT_ID_ONLY` tells Synapse to never include full event content in the
     * gateway call, matching the gateway's own guarantee independently -- both sides agree
     * message content never reaches Apple/Google, not just this one.
     *
     * `append = false`: replaces any existing pusher for this (app_id, pushkey) pair rather
     * than accumulating duplicates across re-logins/token refreshes -- the pushkey (FCM token)
     * is the actual identity here, not the device id, so there's nothing worth keeping from a
     * previous registration.
     */
    suspend fun registerPusher(pushToken: String) {
        val activeClient = ensureClient()
        val gatewayUrl = pushGatewayUrl ?: return
        activeClient.setPusher(
            identifiers = PusherIdentifiers(pushkey = pushToken, appId = BuildConfig.APPLICATION_ID),
            kind = PusherKind.Http(HttpPusherData(url = gatewayUrl, format = PushFormat.EVENT_ID_ONLY, defaultPayload = "{}")),
            appDisplayName = "Larpnet Android",
            deviceDisplayName = "Larpnet Android",
            profileTag = null,
            lang = "pl",
            append = false,
        )
    }

    /** Called when the user turns off push notifications (Settings) without logging out
     * entirely -- [clearSession]'s own `logout()` call already removes every pusher for that
     * device server-side, so this is only needed for the "still logged in, just disabled push"
     * case. */
    suspend fun unregisterPusher(pushToken: String) {
        val activeClient = ensureClient()
        activeClient.deletePusher(PusherIdentifiers(pushkey = pushToken, appId = BuildConfig.APPLICATION_ID))
    }

    /**
     * Decrypts and resolves a single event referenced by a data-only Matrix push (see
     * `LarpnetFirebaseMessagingService`) into a title/body pair ready to show in a local
     * notification -- never the push payload itself, which only ever carries `event_id`/
     * `room_id` (see `larpnet_matrix_push_notify()`'s own doc comment for why). Returns null
     * for anything not worth surfacing (the room/event no longer exists, was redacted, or the
     * push arrived for a room this device has since left/filtered).
     */
    suspend fun notificationPreview(roomId: String, eventId: String): Pair<String, String>? {
        val activeClient = ensureClient()
        val item = activeClient.notificationClient(NotificationProcessSetup.MultipleProcesses).use { notificationClient ->
            (notificationClient.getNotification(roomId, eventId) as? NotificationStatus.Event)?.item
        } ?: return null

        // No raw mxid available here to run through resolvedName()'s Friendica-name lookup
        // the way room-list rows do (NotificationSenderInfo only carries a display name, not
        // a user id) -- relying instead on larpnet_matrix_sync_profile()'s own server-side
        // sync (throttled hourly, or via its cron hook for everyone) already having given
        // this sender a real Matrix displayname by the time push notifications matter.
        val senderName = item.senderInfo.displayName?.takeIf { it.isNotBlank() } ?: return null
        val roomName = item.roomInfo.displayName?.takeIf { it.isNotBlank() }
        val title = if (item.roomInfo.isDm || roomName == null) senderName else "$senderName ($roomName)"

        val body = when (val event = item.event) {
            is NotificationEvent.Invite -> "Zaproszenie do rozmowy"
            is NotificationEvent.Timeline -> bodyFor(event.event.content())
        } ?: return null

        return title to body
    }

    private fun bodyFor(content: TimelineEventContent): String? {
        val messageLike = (content as? TimelineEventContent.MessageLike)?.content ?: return null
        return when (messageLike) {
            is MessageLikeEventContent.RoomMessage -> when (val messageType = messageLike.messageType) {
                is MessageType.Text -> messageType.content.body
                is MessageType.Image -> "📷 Zdjęcie"
                is MessageType.File -> "📎 Plik"
                is MessageType.Audio -> "🎤 Nagranie"
                is MessageType.Video -> "🎬 Wideo"
                else -> "Nowa wiadomość"
            }
            is MessageLikeEventContent.RoomEncrypted -> "🔒"
            else -> null
        }
    }

    /**
     * Called alongside [TokenStore.clear] at logout (see `SettingsScreen`) -- deletes the
     * on-disk crypto store and the persisted device id, so a different account logging into
     * this device next doesn't inherit either. Best-effort server-side `logout()` first (so
     * the device is also cleanly removed from the account -- Synapse deletes that device's
     * pushers as part of this too, so [unregisterPusher] is never needed here), but the local
     * cleanup below runs regardless of whether that network call succeeds.
     */
    fun clearSession() {
        syncHandle?.cancel()
        syncHandle?.close()
        syncHandle = null
        contactsByLocalpart = emptyMap()
        serverName = null
        pushGatewayUrl = null

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

    /** Real room avatar, same hero-fallback shape as [displayNameFor]: for a 1:1 DM, the room
     * itself rarely has its own avatar set, so fall back to the other person's. */
    private suspend fun avatarUrlFor(room: Room): String? {
        room.avatarUrl()?.let { return it }
        val heroes = room.heroes()
        return if (heroes.size == 1) heroes[0].avatarUrl else null
    }

    /** Fetches a real avatar image's bytes for a `mxc://` URL (a sender's or room's) -- callers
     * decode this into a `Bitmap` and cache it themselves (see `MatrixAvatarImage`); this layer
     * only knows how to talk to the SDK's media loader, not about Compose/caching. */
    suspend fun avatarThumbnail(mxcUrl: String, size: Int = 96): ByteArray {
        val activeClient = ensureClient()
        val source = MediaSource.fromUrl(mxcUrl)
        return activeClient.getMediaThumbnail(source, size.toULong(), size.toULong())
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
        return "Chat"
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
