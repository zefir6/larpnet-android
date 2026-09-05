package pl.larpnet.android.di

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import pl.larpnet.android.BuildConfig
import pl.larpnet.android.data.auth.OAuthFlow
import pl.larpnet.android.data.auth.TokenStore
import pl.larpnet.android.data.repository.AuthRepository
import pl.larpnet.android.data.repository.ConversationRepository
import pl.larpnet.android.data.repository.MediaRepository
import pl.larpnet.android.data.repository.NotificationRepository
import pl.larpnet.android.data.repository.ProfileRepository
import pl.larpnet.android.data.repository.PushRepository
import pl.larpnet.android.data.repository.StatusRepository
import pl.larpnet.android.data.repository.TimelineRepository
import pl.larpnet.android.data.repository.UpdateChecker
import pl.larpnet.android.network.AuthApi
import pl.larpnet.android.network.AuthInterceptor
import pl.larpnet.android.network.FriendicaApi
import pl.larpnet.android.network.GitHubApi
import pl.larpnet.android.network.InstanceUrl
import pl.larpnet.android.network.friendicaJson
import pl.larpnet.android.ui.compose.RecentTagsStore
import pl.larpnet.android.ui.nav.BottomNavOrderStore
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

/**
 * Hand-rolled dependency graph (no Hilt): this app is small enough that a top-level object
 * graph built once in App.onCreate() and handed to ViewModel factories covers it, without
 * pulling in a KSP/annotation-processor plugin whose AGP/Kotlin version alignment can't be
 * verified without a working build environment (see plan doc "Tech stack").
 */
class AppContainer(context: Context) {

    val tokenStore = TokenStore(context)

    val bottomNavOrderStore = BottomNavOrderStore(tokenStore)

    val recentTagsStore = RecentTagsStore(tokenStore)

    val authInterceptor = AuthInterceptor(tokenStore)

    /** Forwarded from OAuthRedirectActivity when the OAuth browser redirect lands; collected by LoginViewModel. */
    private val _oauthCallbackEvents = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val oauthCallbackEvents: SharedFlow<Uri> = _oauthCallbackEvents.asSharedFlow()
    fun emitOAuthCallback(uri: Uri) {
        _oauthCallbackEvents.tryEmit(uri)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
    }

    /**
     * larpnet.pl sits behind Cloudflare, whose WAF/bot-management blocks requests carrying a
     * generic HTTP-library User-Agent (OkHttp's default, curl's default) with a 403 -- verified
     * directly against the production server (`okhttp/4.12.0` -> 403, `larpnet-android/x.y.z` ->
     * 200 on the same POST /api/v1/apps call). The sibling macOS app works around the same
     * restriction with its own `larpnet-desktop/<version>` User-Agent; this mirrors that.
     */
    private val userAgentInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", "larpnet-android/${BuildConfig.VERSION_NAME}")
            .build()
        chain.proceed(request)
    }

    private val unauthenticatedClient = OkHttpClient.Builder()
        .addInterceptor(userAgentInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val authenticatedClient = OkHttpClient.Builder()
        .addInterceptor(userAgentInterceptor)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    /**
     * Coil (image loading) builds its own default OkHttpClient unless explicitly told
     * otherwise -- confirmed live that this bites avatar/media loading exactly like the API
     * calls did: larpnet.pl proxies avatars through its own `/photo/contact/...` URLs, which
     * sit behind the same Cloudflare UA block (curl with a generic UA -> 403, this app's UA ->
     * 200). App.kt wires this into Coil's SingletonImageLoader. Deliberately not sharing
     * [authenticatedClient] here: images don't need the Bearer token, and skipping
     * [authInterceptor] means a broken image load can never trigger a spurious forced logout.
     */
    val imageOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(userAgentInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    /**
     * Dedicated client for [pl.larpnet.android.push.NtfyListenerService]'s long-lived streaming
     * connection to push.larpnet.pl (a different host than the Friendica instance, so this is
     * intentionally separate from [authenticatedClient]/its Bearer token). Read timeout is
     * disabled -- the connection is meant to stay open indefinitely, not time out between pushes.
     */
    val pushOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(userAgentInterceptor)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * Plain client for GitHub's public REST API (in-app update checks -- there's no Play Store
     * distribution for this app). GitHub 403s requests with no User-Agent at all, so
     * [userAgentInterceptor] is load-bearing here, not just consistency; deliberately not
     * [authenticatedClient] -- a Friendica Bearer token has no business on a request to GitHub.
     */
    private val gitHubOkHttpClient = OkHttpClient.Builder()
        .addInterceptor(userAgentInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val jsonConverterFactory = friendicaJson.asConverterFactory("application/json".toMediaType())

    private fun retrofitFor(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(InstanceUrl.normalize(baseUrl))
            .client(client)
            .addConverterFactory(jsonConverterFactory)
            .build()

    fun authApi(baseUrl: String): AuthApi =
        retrofitFor(baseUrl, unauthenticatedClient).create(AuthApi::class.java)

    // Memoized by base URL so repeated access doesn't rebuild Retrofit on every call, but
    // stays correct if the user logs into a different instance later (cache key changes).
    private var cachedApi: Pair<String, FriendicaApi>? = null

    fun friendicaApi(): FriendicaApi {
        val baseUrl = tokenStore.instanceBaseUrl
            ?: error("friendicaApi() accessed before an instance URL was stored (i.e. before login)")
        val cached = cachedApi
        if (cached != null && cached.first == baseUrl) return cached.second
        val api = retrofitFor(baseUrl, authenticatedClient).create(FriendicaApi::class.java)
        cachedApi = baseUrl to api
        return api
    }

    val oAuthFlow = OAuthFlow(tokenStore, ::authApi)

    val authRepository = AuthRepository(tokenStore, oAuthFlow, ::friendicaApi)
    val timelineRepository = TimelineRepository(::friendicaApi)
    val statusRepository = StatusRepository(::friendicaApi)
    val notificationRepository = NotificationRepository(::friendicaApi)
    val profileRepository = ProfileRepository(::friendicaApi)
    val mediaRepository = MediaRepository(::friendicaApi)
    val conversationRepository = ConversationRepository(::friendicaApi)
    val pushRepository = PushRepository(::friendicaApi)

    private val gitHubApi: GitHubApi = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(gitHubOkHttpClient)
        .addConverterFactory(jsonConverterFactory)
        .build()
        .create(GitHubApi::class.java)

    val updateChecker: UpdateChecker = createUpdateChecker(context, gitHubApi, tokenStore)
}
