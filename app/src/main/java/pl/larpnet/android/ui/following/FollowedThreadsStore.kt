package pl.larpnet.android.ui.following

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import pl.larpnet.android.data.auth.TokenStore
import pl.larpnet.android.network.friendicaJson

/**
 * Entirely client-side -- the server has no concept of "following a thread". [lastSeenReplyCount]
 * must be compared against a live [pl.larpnet.android.data.model.StatusContext.descendants].size,
 * never [pl.larpnet.android.data.model.Status.repliesCount] (that field reflects direct replies
 * only, not the full descendant count ThreadBuilder flattens).
 */
@Serializable
data class FollowedThread(
    val rootStatusId: String,
    val lastSeenReplyCount: Int = 0,
    val followedAt: Long = System.currentTimeMillis(),
)

/** Persisted as a JSON array in [TokenStore.followedThreadsJson]. */
class FollowedThreadsStore(private val tokenStore: TokenStore) {
    private val _threads = MutableStateFlow(readPersisted())
    val threads: StateFlow<List<FollowedThread>> = _threads.asStateFlow()

    fun isFollowing(rootStatusId: String): Boolean = _threads.value.any { it.rootStatusId == rootStatusId }

    fun follow(rootStatusId: String, currentReplyCount: Int) {
        if (isFollowing(rootStatusId)) return
        persist(_threads.value + FollowedThread(rootStatusId, currentReplyCount))
    }

    fun unfollow(rootStatusId: String) {
        persist(_threads.value.filterNot { it.rootStatusId == rootStatusId })
    }

    fun updateLastSeen(rootStatusId: String, replyCount: Int) {
        persist(_threads.value.map { if (it.rootStatusId == rootStatusId) it.copy(lastSeenReplyCount = replyCount) else it })
    }

    private fun persist(updated: List<FollowedThread>) {
        tokenStore.followedThreadsJson = if (updated.isEmpty()) null else friendicaJson.encodeToString(updated)
    }

    private fun readPersisted(): List<FollowedThread> {
        val json = tokenStore.followedThreadsJson ?: return emptyList()
        return runCatching { friendicaJson.decodeFromString<List<FollowedThread>>(json) }.getOrDefault(emptyList())
    }
}
