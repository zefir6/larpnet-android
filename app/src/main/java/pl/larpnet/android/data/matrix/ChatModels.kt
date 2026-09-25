package pl.larpnet.android.data.matrix

/**
 * One row in the chat room list -- [MatrixRepository.rooms]'s output shape, already resolved
 * to display-ready fields so `ChatViewModel`/`ChatScreen` never touch MatrixRustSDK types
 * directly (mirrors how [pl.larpnet.android.data.model.Conversation]/`Account` keep Messages'
 * UI layer decoupled from the raw Mastodon API JSON shape).
 */
data class ChatRoom(
    val id: String,
    val name: String,
    val preview: String?,
    val timestampMillis: Long?,
)

/** One message in a room's timeline -- [ChatTimelineHandle]'s output shape. */
data class ChatMessage(
    val id: String,
    val isOwn: Boolean,
    val body: String,
    val timestampMillis: Long,
)

/** One other member of a room, display-ready -- [MatrixRepository.roomInfo]'s member list. */
data class ChatRoomMember(
    val userId: String,
    val displayName: String,
)

/** [MatrixRepository.roomInfo]'s output shape -- mirrors the web client's `RoomInfoModal.jsx`
 * (`others`/`isGroup`/`room.name`). */
data class ChatRoomInfo(
    val roomId: String,
    val rawName: String,
    val isGroup: Boolean,
    val members: List<ChatRoomMember>,
)
