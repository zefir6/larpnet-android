package pl.larpnet.android.data.repository

import kotlinx.coroutines.flow.StateFlow

enum class UpdateSource { GITHUB, PLAY_STORE }

data class AppUpdate(
    val versionName: String?,
    val versionCode: Int,
    val downloadUrl: String,
    val source: UpdateSource,
)

internal const val UPDATE_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

/**
 * Backs the global "new version available" banner and Settings' manual check. One implementation
 * per distribution flavor (see di/UpdateCheckerFactory.kt) -- GitHub Releases polling or the Play
 * Core In-App Update API -- but callers never need to know which.
 */
interface UpdateChecker {
    val updateAvailable: StateFlow<AppUpdate?>

    /** Cold-start path: no-ops if we already checked within [UPDATE_CHECK_INTERVAL_MS], and never re-surfaces a dismissed version. */
    suspend fun checkIfDue()

    /** Settings' explicit "Check for updates": always checks, always reports the truth even if this version was dismissed before. */
    suspend fun checkNow(respectDismissal: Boolean = false)

    fun dismiss()
}
