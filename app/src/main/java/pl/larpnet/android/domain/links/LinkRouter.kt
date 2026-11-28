package pl.larpnet.android.domain.links

import java.net.URI
import java.net.URLDecoder

/** Where a tapped or incoming link should go. See [LinkRouter.classify]. */
sealed interface LinkTarget {
    /** A hashtag timeline. */
    data class Hashtag(val tag: String) : LinkTarget

    /** A local account by nickname (`larpnet.pl/profile/<nick>`). */
    data class LocalProfile(val nickname: String) : LinkTarget

    /** A post or account somewhere in the fediverse (an http(s) URL or a `user@host` handle),
     * resolved through the server's `api/v2/search?resolve=true`. */
    data class Resolve(val query: String) : LinkTarget

    /** `web+mastodon://share?text=...`: a new post prefilled with [text]. */
    data class Share(val text: String) : LinkTarget

    /** A page of our own instance the app has no screen for (settings, events...). */
    data class InstancePage(val url: String) : LinkTarget

    /** Anything else: the browser. */
    data class External(val url: String) : LinkTarget
}

/**
 * Decides what a link means for the app -- pure, so it's unit tested (LinkRouterTest).
 *
 * Handles links tapped inside posts and links the app is opened with: `https://larpnet.pl/...`
 * App Links, and the `web+mastodon://` (Mastodon's own protocol handler) and `web+ap://`
 * (FEP-07d7, ActivityPub objects) schemes.
 */
object LinkRouter {
    // Path shapes of posts and profiles on the common fediverse servers: Mastodon (/@user,
    // /@user/123, /users/user, /users/user/statuses/123), Friendica (/profile/x, /display/guid),
    // Pleroma/Akkoma (/notice/id, /objects/uuid), Misskey (/notes/id), Pixelfed (/p/user/id),
    // Lemmy (/post/id, /u/user, /c/community), GoToSocial (/@user/statuses/id).
    private val fediversePath = listOf(
        Regex("^/@[^/]+/?$"),
        Regex("^/@[^/]+/(statuses/)?[A-Za-z0-9_-]+/?$"),
        Regex("^/users/[^/]+/?$"),
        Regex("^/users/[^/]+/statuses/[A-Za-z0-9_-]+/?$"),
        Regex("^/profile/[^/]+/?$"),
        Regex("^/display/[A-Za-z0-9_-]+/?$"),
        Regex("^/notice/[A-Za-z0-9_-]+/?$"),
        Regex("^/objects/[A-Za-z0-9_-]+/?$"),
        Regex("^/notes/[A-Za-z0-9_-]+/?$"),
        Regex("^/p/[^/]+/[0-9]+/?$"),
        Regex("^/post/[0-9]+/?$"),
        Regex("^/(u|c)/[^/]+/?$"),
    )

    private val handle = Regex("^@?[A-Za-z0-9_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    /** [instanceHost] is the host of the instance the user is logged in to (`larpnet.pl`). */
    fun classify(raw: String, instanceHost: String?): LinkTarget {
        val link = raw.trim()
        val uri = runCatching { URI(link) }.getOrNull() ?: return LinkTarget.External(link)
        return when (uri.scheme?.lowercase()) {
            "web+mastodon" -> classifyMastodonIntent(uri, link)
            "web+ap" -> classifyWebAp(link, instanceHost)
            "http", "https" -> classifyHttp(uri, link, instanceHost)
            else -> LinkTarget.External(link)
        }
    }

    /** Mastodon's handler only defines `follow?uri=<handle or URL>` and `share?text=...`. */
    private fun classifyMastodonIntent(uri: URI, link: String): LinkTarget {
        // web+mastodon://follow?uri=... parses as an authority-only URI; URI.host is null when
        // the "host" has no dot on some JDKs, so read the authority instead.
        val action = (uri.host ?: uri.rawAuthority ?: "").lowercase()
        val params = queryParams(uri.rawQuery)
        return when (action) {
            "follow" -> params["uri"]?.removePrefix("acct:")?.takeIf { it.isNotBlank() }
                ?.let { LinkTarget.Resolve(it.removePrefix("@")) }
                ?: LinkTarget.External(link)
            "share" -> LinkTarget.Share(
                listOfNotNull(params["text"], params["url"]).joinToString(" ").trim(),
            )
            else -> LinkTarget.External(link)
        }
    }

    /** FEP-07d7: `web+ap://host/path` is `https://host/path`. */
    private fun classifyWebAp(link: String, instanceHost: String?): LinkTarget {
        val https = "https://" + link.substringAfter("://")
        val uri = runCatching { URI(https) }.getOrNull() ?: return LinkTarget.External(https)
        return when (val target = classifyHttp(uri, https, instanceHost)) {
            // It names an ActivityPub object even when the path isn't a shape we know.
            is LinkTarget.External, is LinkTarget.InstancePage -> LinkTarget.Resolve(https)
            else -> target
        }
    }

    private fun classifyHttp(uri: URI, link: String, instanceHost: String?): LinkTarget {
        val host = uri.host?.lowercase() ?: return LinkTarget.External(link)
        val path = uri.rawPath.orEmpty().let { decode(it) }
        val own = instanceHost != null && host == instanceHost.lowercase()

        // Hashtags: Mastodon-style /tags/x anywhere; Friendica's /search?tag=x on our instance.
        Regex("^/tags/([^/]+)/?$").find(path)?.let { return LinkTarget.Hashtag(it.groupValues[1]) }
        if (own && path.trimEnd('/') == "/search") {
            queryParams(uri.rawQuery)["tag"]?.takeIf { it.isNotBlank() }
                ?.let { return LinkTarget.Hashtag(it.removePrefix("#")) }
        }

        if (own) {
            Regex("^/(?:profile/|@)([^/]+)/?$").find(path)?.let {
                return LinkTarget.LocalProfile(it.groupValues[1])
            }
            if (Regex("^/(display|item|objects)/[^/]+/?$").containsMatchIn(path)) {
                return LinkTarget.Resolve(link)
            }
            return LinkTarget.InstancePage(link)
        }

        if (fediversePath.any { it.containsMatchIn(path) }) return LinkTarget.Resolve(link)
        return LinkTarget.External(link)
    }

    /** True when [query] is a `user@host` handle rather than a URL. */
    fun isHandle(query: String): Boolean = handle.matches(query)

    private fun queryParams(raw: String?): Map<String, String> =
        raw.orEmpty().split('&').mapNotNull { part ->
            if (part.isEmpty()) return@mapNotNull null
            val key = decode(part.substringBefore('='))
            val value = decode(part.substringAfter('=', ""))
            key to value
        }.toMap()

    private fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}
