package pl.larpnet.android.data.repository

import pl.larpnet.android.data.model.Conversation
import pl.larpnet.android.data.model.DirectMessage
import pl.larpnet.android.network.FriendicaApi
import pl.larpnet.android.network.Page
import pl.larpnet.android.network.parseLinkHeader
import pl.larpnet.android.network.safeApiCall

/**
 * Direct messages, backed by Friendica's legacy `mail` table -- the same store the web UI's
 * Messages feature reads, and deliberately *not* `POST /api/v1/statuses?visibility=direct`.
 * That endpoint creates a normal (Item-table) private post whose `visibility` reads back as
 * `"private"`, indistinguishable from a followers-only post, and it's never surfaced by
 * `/api/v1/conversations` or `/api/direct_messages` -- the two systems don't talk to each
 * other server-side. See friendica-larpnet's src/Model/Mail.php / src/Module/Api/Mastodon/
 * Conversations.php for the split. Conversations here are strictly 1:1 (no `mail` schema
 * support for multiple recipients), so [Conversation.accounts] always has exactly one entry.
 */
class ConversationRepository(private val apiProvider: () -> FriendicaApi) {

    suspend fun list(maxId: String? = null): Result<Page<Conversation>> = safeApiCall {
        parseLinkHeader(apiProvider().conversations(maxId))
    }

    suspend fun markRead(id: String): Result<Conversation> = safeApiCall {
        apiProvider().markConversationRead(id)
    }

    suspend fun delete(id: String): Result<Unit> = safeApiCall {
        apiProvider().deleteConversation(id)
    }

    /** Full message history with one contact, newest first. [profileUrl] is the recipient's [Account.url]. */
    suspend fun thread(profileUrl: String, maxId: String? = null): Result<Page<DirectMessage>> = safeApiCall {
        parseLinkHeader(apiProvider().directMessages(profileUrl = profileUrl, maxId = maxId))
    }

    /** [screenName] is the recipient's [Account.acct] (nickname, or `nick@host` for remote accounts). */
    suspend fun send(screenName: String, text: String): Result<DirectMessage> {
        val result = safeApiCall { apiProvider().sendDirectMessage(screenName = screenName, text = text) }
        val message = result.getOrNull() ?: return result
        // The server returns 200 with {"error": <code>} in the body on failure (e.g.
        // recipient not resolvable) rather than a non-2xx status, so safeApiCall's
        // HttpException handling never sees it -- check explicitly instead.
        return if (message.error != null) {
            Result.failure(IllegalStateException("Send failed (server error code ${message.error})"))
        } else {
            result
        }
    }
}
