package pl.larpnet.android.data.model

/** Anything with a Mastodon-API-style string id -- lets pagers fall back to "last item's id" as a next cursor. */
interface Identifiable {
    val id: String
}
