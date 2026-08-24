package pl.larpnet.android.network

import pl.larpnet.android.data.model.GitHubRelease
import retrofit2.http.GET

/** Public, unauthenticated GitHub API -- only used for in-app update checks. See AppContainer for the dedicated client/base URL. */
interface GitHubApi {
    @GET("repos/zefir6/larpnet-android/releases/latest")
    suspend fun latestRelease(): GitHubRelease
}
