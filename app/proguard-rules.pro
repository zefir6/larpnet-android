# Add project specific ProGuard rules here.

# kotlinx.serialization: keep serializer() for @Serializable classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class pl.larpnet.android.**$$serializer { *; }
-keepclassmembers class pl.larpnet.android.** {
    *** Companion;
}
-keepclasseswithmembers class pl.larpnet.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# rustls-platform-verifier (vendored in org/rustls/platformverifier/, see CertificateVerifier.kt's
# own header comment) is called by name via JNI from the matrix-rust-sdk native library, not from
# any Kotlin call site R8 can trace -- without this it gets stripped/renamed in a minified build,
# and native chat's first HTTPS call fails with "Expect rustls-platform-verifier to be initialized".
-keep,includedescriptorclasses class org.rustls.platformverifier.** { *; }

# Retrofit service interfaces are only ever implemented by a java.lang.reflect.Proxy created at
# runtime (Retrofit.create()) -- R8 full mode has no visibility into that and otherwise leaves
# these interfaces in a state where the Proxy's checkcast back to the interface type fails with
# a ClassCastException at construction time (verified against a real minified build: AppContainer
# crashes in App.onCreate() constructing GitHubApi). The default retrofit2.pro consumer rule only
# keeps annotated methods; keep the whole interface to be safe for all three service interfaces.
-keep interface pl.larpnet.android.network.AuthApi { *; }
-keep interface pl.larpnet.android.network.FriendicaApi { *; }
-keep interface pl.larpnet.android.network.GitHubApi { *; }
