package pl.larpnet.android.data.repository

import pl.larpnet.android.domain.links.LinkRouter
import pl.larpnet.android.network.FriendicaApi
import pl.larpnet.android.network.safeApiCall

/** What a fediverse link turned out to be, with the ids the app's screens take. */
sealed interface ResolvedLink {
    data class Post(val statusId: String) : ResolvedLink
    data class Profile(val accountId: String) : ResolvedLink
    data object NotFound : ResolvedLink
}

class LinkRepository(private val apiProvider: () -> FriendicaApi) {
    /** [query] is a post/profile URL or a `user@host` handle. The server fetches it from its
     * home instance if it hasn't seen it yet, so this can take a few seconds. */
    suspend fun resolve(query: String): Result<ResolvedLink> = safeApiCall {
        val api = apiProvider()
        if (LinkRouter.isHandle(query)) {
            val account = api.searchAccounts(query.removePrefix("@"), limit = 1, resolve = true).firstOrNull()
            return@safeApiCall account?.let { ResolvedLink.Profile(it.id) } ?: ResolvedLink.NotFound
        }
        val results = api.search(query)
        results.statuses.firstOrNull()?.let { return@safeApiCall ResolvedLink.Post(it.id) }
        results.accounts.firstOrNull()?.let { return@safeApiCall ResolvedLink.Profile(it.id) }
        ResolvedLink.NotFound
    }
}
