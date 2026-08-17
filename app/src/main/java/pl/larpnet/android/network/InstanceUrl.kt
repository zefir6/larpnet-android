package pl.larpnet.android.network

/**
 * Normalizes a user-entered instance address the same way the sibling macOS app's
 * `FriendicaClient::new` does: a bare domain (e.g. "larpnet.pl") gets `https://` prepended;
 * an explicit http(s):// is left alone. Retrofit additionally requires base URLs to end
 * with a trailing slash.
 */
object InstanceUrl {
    fun normalize(input: String): String {
        val trimmed = input.trim()
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }
}
