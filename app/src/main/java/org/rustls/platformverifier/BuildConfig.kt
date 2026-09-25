package org.rustls.platformverifier

/**
 * Element X Android generates this via a dedicated Gradle module's `buildConfigField` (see
 * their `libraries/rustls-tls/build.gradle.kts`) since `CertificateVerifier.kt` (vendored
 * unmodified alongside this file -- see its own header comment) references `BuildConfig.TEST`.
 * This app has no such module, so this plain object stands in for it: always `false`, since we
 * never run the mock-root-CA test path this flag guards, only the real system-trust-store path.
 */
internal object BuildConfig {
    const val TEST = false
}
