package pl.larpnet.android.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Keystore-backed credential storage -- the Android analogue of the sibling macOS app's
 * src-tauri/src/keychain.rs. Holds the instance URL, the cached OAuth client registration
 * (so we don't re-register with the server on every login), and the current access token.
 *
 * There is no refresh token to store: confirmed server-side (src/Module/OAuth/Token.php)
 * that tokens never expire and this server never issues one. The only way a stored token
 * stops working is server-side revocation, surfaced to us as a 401/403 (see AuthInterceptor).
 *
 * If androidx.security:security-crypto is ever unavailable/broken at your Gradle sync
 * (it has shipped from alpha for a long time), swap the SharedPreferences instance below
 * for a plain `context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)` -- still
 * app-private storage, just not Keystore-wrapped.
 */
class TokenStore(context: Context) {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var instanceBaseUrl: String?
        get() = prefs.getString(KEY_INSTANCE_URL, null)
        set(value) = prefs.edit().putString(KEY_INSTANCE_URL, value).apply()

    var clientId: String?
        get() = prefs.getString(KEY_CLIENT_ID, null)
        set(value) = prefs.edit().putString(KEY_CLIENT_ID, value).apply()

    var clientSecret: String?
        get() = prefs.getString(KEY_CLIENT_SECRET, null)
        set(value) = prefs.edit().putString(KEY_CLIENT_SECRET, value).apply()

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrBlank() && !instanceBaseUrl.isNullOrBlank()

    /** User-facing push toggle (Settings). Defaults on: "Add notifications" is an explicit ask, and the
     * runtime POST_NOTIFICATIONS prompt (Android 13+) already gives the user a natural off-ramp. */
    var pushEnabled: Boolean
        get() = prefs.getBoolean(KEY_PUSH_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PUSH_ENABLED, value).apply()

    /** Epoch millis of the last in-app update check (any account) -- see UpdateRepository.checkIfDue. */
    var lastUpdateCheckAt: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_AT, value).apply()

    /** versionCode of the update the user last dismissed the banner for -- see UpdateRepository.dismiss. */
    var dismissedUpdateVersionCode: Int
        get() = prefs.getInt(KEY_DISMISSED_UPDATE_VERSION_CODE, 0)
        set(value) = prefs.edit().putInt(KEY_DISMISSED_UPDATE_VERSION_CODE, value).apply()

    /** Comma-separated last-3 hashtags (no leading '#') the user has published with, most-recent-first
     * -- see [pl.larpnet.android.ui.compose.RecentTagsStore]. Null until first use. */
    var recentTags: String?
        get() = prefs.getString(KEY_RECENT_TAGS, null)
        set(value) = prefs.edit().putString(KEY_RECENT_TAGS, value).apply()

    /** Clears the access token (and app registration, since it's keyed to one instance) on logout / forced re-login. */
    fun clear() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_CLIENT_ID)
            .remove(KEY_CLIENT_SECRET)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "larpnet_secure_prefs"
        private const val KEY_INSTANCE_URL = "instance_base_url"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_CLIENT_SECRET = "client_secret"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_PUSH_ENABLED = "push_enabled"
        private const val KEY_LAST_UPDATE_CHECK_AT = "last_update_check_at"
        private const val KEY_DISMISSED_UPDATE_VERSION_CODE = "dismissed_update_version_code"
        private const val KEY_RECENT_TAGS = "recent_tags"
    }
}
