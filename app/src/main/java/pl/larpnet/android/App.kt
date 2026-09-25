package pl.larpnet.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import org.matrix.rustcomponents.sdk.LogLevel
import org.matrix.rustcomponents.sdk.TracingConfiguration
import org.matrix.rustcomponents.sdk.initPlatform
import pl.larpnet.android.di.AppContainer

class App : Application(), SingletonImageLoader.Factory {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        initMatrixPlatform()
        appContainer = AppContainer(this)
    }

    /**
     * MatrixRustSDK requires this exactly once, before any `Client` is built --
     * on Android specifically it wires up rustls-platform-verifier's JNI bridge to
     * the already-running JVM (via `JNI_GetCreatedJavaVMs` + Android's own
     * `ActivityThread.currentActivityThread()`, both handled entirely on the Rust
     * side -- no Context needs to be passed in from here). Without this,
     * `ClientBuilder().build()` panics with "Expect rustls-platform-verifier to be
     * initialized" on every real device/emulator -- see `MatrixRepository`'s doc
     * comment for the full root-cause writeup this fixes (confirmed against
     * matrix-rust-sdk's own source, `bindings/matrix-sdk-ffi/src/platform/mod.rs`'s
     * `init_platform()` -- it's a real exported FFI function, just never called).
     */
    private fun initMatrixPlatform() {
        initPlatform(
            TracingConfiguration(
                logLevel = LogLevel.WARN,
                traceLogPacks = emptyList(),
                extraTargets = emptyList(),
                writeToStdoutOrSystem = true,
                writeToFiles = null,
                sentryConfig = null,
            ),
            false,
        )
    }

    /** Routes Coil's image loads through [AppContainer.imageOkHttpClient] -- see its doc comment. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { appContainer.imageOkHttpClient }))
            }
            .build()
}
