package pl.larpnet.android.network

import kotlinx.serialization.json.Json

/**
 * Shared Json config for all API calls. The three flags below exist specifically to absorb
 * Friendica's Mastodon-API-compatible-but-not-identical JSON shape: extra "friendica"
 * extension blocks (ignoreUnknownKeys), occasional server-sent null on fields we declare
 * non-null-with-default (coerceInputValues), and no need to round-trip explicit nulls back
 * out (explicitNulls = false).
 */
val friendicaJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}
