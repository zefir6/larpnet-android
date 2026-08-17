package pl.larpnet.android.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import pl.larpnet.android.data.model.Identifiable
import retrofit2.HttpException
import retrofit2.Response

/** A page of results plus cursors for the next/previous page, if any. */
data class Page<T>(
    val items: List<T>,
    val nextMaxId: String?,
    val prevMinId: String?,
)

/**
 * Parses cursors from the Mastodon-API-style `Link` response header (rel="next"/"prev",
 * carrying max_id/min_id or since_id query params) rather than from the JSON body -- this
 * mirrors src-tauri/src/friendica/pagination.rs in the sibling macOS app. Confirmed via
 * BaseApi::setLinkHeader that every paginated endpoint this app calls sets this header.
 *
 * Falls back to the last item's id as `nextMaxId` if the header is ever missing, as defense
 * in depth (the header not being decodable shouldn't silently break "load more").
 *
 * Retrofit does NOT auto-throw on non-2xx for `Response<T>`-returning suspend functions the
 * way it does for direct-return ones (that's the whole reason these endpoints are declared to
 * return `Response<List<T>>` -- to reach the Link header at all). Without this check a failed
 * request would silently look like an empty page instead of surfacing through safeApiCall.
 */
fun <T> parseLinkHeader(response: Response<List<T>>): Page<T> {
    if (!response.isSuccessful) {
        throw HttpException(response)
    }
    val body = response.body().orEmpty()
    var next: String? = null
    var prev: String? = null

    response.headers()["Link"]?.split(",")?.forEach { part ->
        val segments = part.split(";").map { it.trim() }
        val rawUrl = segments.firstOrNull()?.trim('<', '>', ' ') ?: return@forEach
        val rel = segments.drop(1)
            .firstOrNull { it.startsWith("rel=") }
            ?.substringAfter("rel=")
            ?.trim('"')
        val httpUrl = rawUrl.toHttpUrlOrNull() ?: return@forEach
        when (rel) {
            "next" -> next = httpUrl.queryParameter("max_id")
            "prev" -> prev = httpUrl.queryParameter("min_id") ?: httpUrl.queryParameter("since_id")
        }
    }

    if (next == null) {
        val last = body.lastOrNull()
        if (last is Identifiable) next = last.id
    }

    return Page(items = body, nextMaxId = next, prevMinId = prev)
}
