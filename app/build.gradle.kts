plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// Release signing comes from the environment (CI secrets), never from committed files -- see
// .github/workflows/release.yml. Absent locally, so a plain `./gradlew assembleGithubRelease` /
// `bundlePlaystoreRelease` on a dev machine still builds (just unsigned); CI is the only place
// these are ever set.
val releaseKeystorePath: String? = System.getenv("RELEASE_KEYSTORE_PATH")
val releaseKeystorePassword: String? = System.getenv("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("RELEASE_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = !releaseKeystorePath.isNullOrBlank() &&
    !releaseKeystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "pl.larpnet.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "pl.larpnet.android"
        minSdk = 26
        targetSdk = 36
        // GITHUB_RUN_NUMBER is unique and strictly increasing across every CI build, which is
        // exactly what versionCode needs to be for Android to treat a new APK as an update
        // rather than a same-or-older version it refuses to install over. Local (non-CI)
        // builds fall back to 1 -- they're never what gets distributed.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        // Semantic-ish version, bumped by hand: patch for fixes, minor for any functionality
        // change, per user preference (2026-08-23) -- not tied to versionCode/run number.
        versionName = "0.8.0"

        // Default Larpnet instance and OAuth redirect scheme. See ui/login/OAuthRedirectActivity.kt
        // and AndroidManifest.xml for the matching intent-filter -- the scheme/host here must stay
        // byte-identical to what's registered with the server (app registration, authorize, token
        // exchange all match on this string).
        buildConfigField("String", "DEFAULT_INSTANCE", "\"larpnet.pl\"")
        buildConfigField("String", "OAUTH_REDIRECT_URI", "\"pl.larpnet.android://oauth\"")
    }

    // Two distribution channels built from the same main branch/tree (see .github/workflows/release.yml,
    // which builds and publishes both from every push): "github" self-updates via UpdateRepository (GitHub
    // Releases) and listens for pushes via NtfyListenerService (no Google Play Services dependency);
    // "playstore" checks for updates via the Play Core In-App Update API (PlayUpdateRepository) and uses
    // Firebase Cloud Messaging instead (PushControl.kt), which is also why it drops the specialUse
    // foreground-service permission Play's review flags -- see app/src/github/AndroidManifest.xml for the
    // manifest pieces unique to the github flavor. UPDATE_CHECK_ENABLED just means "some update-check
    // mechanism is active for this flavor" -- which one is selected per-flavor in di/UpdateCheckerFactory.kt.
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "UPDATE_CHECK_ENABLED", "true")
            buildConfigField("boolean", "FCM_PUSH_ENABLED", "false")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("boolean", "UPDATE_CHECK_ENABLED", "true")
            buildConfigField("boolean", "FCM_PUSH_ENABLED", "true")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            // We ship no native code ourselves, but bundled deps (e.g. Firebase Messaging) do.
            // Without this, Play Console warns that the App Bundle has native libs but no debug
            // symbols were uploaded, which would otherwise leave native crashes/ANRs unsymbolicated.
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.jsoup)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    "playstoreImplementation"(libs.play.app.update.ktx)
}
