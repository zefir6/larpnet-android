package pl.larpnet.android.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.larpnet.android.BuildConfig
import pl.larpnet.android.data.auth.TokenStore
import pl.larpnet.android.network.GitHubApi
import pl.larpnet.android.network.safeApiCall

data class AppUpdate(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
)

/** The run-number suffix GitHub Actions puts on every release tag ("v0.2.0-3") -- see .github/workflows/release.yml. */
private val VERSION_CODE_FROM_TAG = Regex("-(\\d+)$")

private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

/**
 * In-app update checking against GitHub Releases (there's no Play Store distribution for this
 * app). Owns both the fetch and the "have we already told the user about this one" state, so
 * every caller (cold-start auto-check, Settings' manual check, the global banner) shares one
 * source of truth.
 */
class UpdateRepository(
    private val gitHubApi: GitHubApi,
    private val tokenStore: TokenStore,
) {
    private val _updateAvailable = MutableStateFlow<AppUpdate?>(null)
    val updateAvailable: StateFlow<AppUpdate?> = _updateAvailable.asStateFlow()

    /** Cold-start path: no-ops if we already checked within [CHECK_INTERVAL_MS], and never re-surfaces a dismissed version. */
    suspend fun checkIfDue() {
        if (System.currentTimeMillis() - tokenStore.lastUpdateCheckAt < CHECK_INTERVAL_MS) return
        checkNow(respectDismissal = true)
    }

    /** Settings' explicit "Check for updates": always hits the network, always reports the truth even if this version was dismissed before. */
    suspend fun checkNow(respectDismissal: Boolean = false) {
        tokenStore.lastUpdateCheckAt = System.currentTimeMillis()
        val update = fetchLatest() ?: return
        if (respectDismissal && update.versionCode == tokenStore.dismissedUpdateVersionCode) return
        _updateAvailable.value = update
    }

    fun dismiss() {
        _updateAvailable.value?.let { tokenStore.dismissedUpdateVersionCode = it.versionCode }
        _updateAvailable.value = null
    }

    private suspend fun fetchLatest(): AppUpdate? {
        val release = safeApiCall { gitHubApi.latestRelease() }.getOrNull() ?: return null
        val remoteVersionCode = VERSION_CODE_FROM_TAG.find(release.tagName)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: return null
        if (remoteVersionCode <= BuildConfig.VERSION_CODE) return null
        val downloadUrl = release.assets.firstOrNull { it.name.endsWith(".apk") }?.browserDownloadUrl ?: return null
        val versionName = release.tagName.removePrefix("v").substringBeforeLast('-')
        return AppUpdate(versionName = versionName, versionCode = remoteVersionCode, downloadUrl = downloadUrl)
    }
}
