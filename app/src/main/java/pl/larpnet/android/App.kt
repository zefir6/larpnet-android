package pl.larpnet.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import pl.larpnet.android.di.AppContainer

class App : Application(), SingletonImageLoader.Factory {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }

    /** Routes Coil's image loads through [AppContainer.imageOkHttpClient] -- see its doc comment. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { appContainer.imageOkHttpClient }))
            }
            .build()
}
