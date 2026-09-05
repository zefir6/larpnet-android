package pl.larpnet.android.data.repository

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.larpnet.android.BuildConfig
import pl.larpnet.android.data.auth.TokenStore

/**
 * In-app update checking against the Play Core In-App Update API, for the "playstore" distribution
 * flavor. Only queries availability -- never drives Play Core's own update-flow UI -- so the banner
 * just opens the Play Store listing, same as the GitHub flow opens a browser download.
 */
class PlayUpdateRepository(
    context: Context,
    private val tokenStore: TokenStore,
) : UpdateChecker {
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)
    private val packageName = context.packageName

    private val _updateAvailable = MutableStateFlow<AppUpdate?>(null)
    override val updateAvailable: StateFlow<AppUpdate?> = _updateAvailable.asStateFlow()

    override suspend fun checkIfDue() {
        if (System.currentTimeMillis() - tokenStore.lastUpdateCheckAt < UPDATE_CHECK_INTERVAL_MS) return
        checkNow(respectDismissal = true)
    }

    override suspend fun checkNow(respectDismissal: Boolean) {
        tokenStore.lastUpdateCheckAt = System.currentTimeMillis()
        val update = fetchLatest() ?: return
        if (respectDismissal && update.versionCode == tokenStore.dismissedUpdateVersionCode) return
        _updateAvailable.value = update
    }

    override fun dismiss() {
        _updateAvailable.value?.let { tokenStore.dismissedUpdateVersionCode = it.versionCode }
        _updateAvailable.value = null
    }

    private suspend fun fetchLatest(): AppUpdate? {
        val info = try {
            appUpdateManager.requestAppUpdateInfo()
        } catch (e: Exception) {
            // Sideloaded install / no Play association / API genuinely unavailable -- no-op
            // silently, never crash, never show a banner.
            return null
        }
        if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return null
        val remoteVersionCode = info.availableVersionCode()
        if (remoteVersionCode <= BuildConfig.VERSION_CODE) return null
        return AppUpdate(
            versionName = null,
            versionCode = remoteVersionCode,
            downloadUrl = "market://details?id=$packageName",
            source = UpdateSource.PLAY_STORE,
        )
    }
}
