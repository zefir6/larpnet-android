package pl.larpnet.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET https://api.github.com/repos/zefir6/larpnet-android/releases/latest` -- used only for
 * in-app update checks (see [pl.larpnet.android.data.repository.UpdateRepository]). [tagName]
 * is `v{versionName}-{run_number}` (see `.github/workflows/release.yml`), and that trailing
 * run number *is* the release's `versionCode` -- no separate manifest needed.
 */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val assets: List<GitHubReleaseAsset> = emptyList(),
)

@Serializable
data class GitHubReleaseAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)
