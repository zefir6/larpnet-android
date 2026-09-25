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

# JNA (a transitive dependency of matrix-rust-sdk's UniFFI-generated Kotlin bindings) binds Java
# fields to native memory layouts via JNI code that looks up field IDs by name AND relies on a
# Structure subclass's field declaration order (via its overridden getFieldOrder()) -- both
# invisible to R8's normal reachability analysis, since nothing in Kotlin code ever reads those
# fields directly. A minified build can rename/reorder/strip them with no compile-time warning.
#
# Confirmed live, in two stages, against the actual published release build (v0.15.0-29) -- every
# single launch crashed before any UI ever showed, since initMatrixPlatform() runs unconditionally
# in App.onCreate(). Never caught by local testing because debug builds don't minify
# (isMinifyEnabled = false), so this was never exercised outside a real release build.
#   1. "java.lang.UnsatisfiedLinkError: Can't obtain peer field ID for class com.sun.jna.Pointer"
#      -- fixed by keeping JNA's own classes below.
#   2. Still crashed after (1) alone, with a *different* failure: "Structure.getFieldOrder() on
#      class uniffi.matrix_sdk.RustBuffer$ByValue does not provide enough names [0] ([]) to match
#      declared fields [3] ([capacity, data, len])" -- R8 had stripped the fields of a UniFFI-
#      generated Structure subclass living in the `uniffi.*` package, a *different* package than
#      org.matrix.rustcomponents.sdk (the public Kotlin API surface) that a package-scoped keep
#      rule wouldn't reach. Fixed by keeping every Structure subclass / Callback implementor by
#      *type*, not by enumerating packages -- robust against this SDK generating more such classes,
#      in whatever package, in a future version.
-keep class com.sun.jna.** { *; }
-keepclassmembers class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**
-keep class * extends com.sun.jna.Structure { *; }
-keepclassmembers class * extends com.sun.jna.Structure { *; }
-keep class * implements com.sun.jna.Callback { *; }
-keepclassmembers class * implements com.sun.jna.Callback { *; }

# Also keep the SDK's own public Kotlin API surface, org.matrix.rustcomponents.sdk.** -- not a
# Structure/Callback concern like the rule above, but its JNA `Native.register()`-based interfaces
# (e.g. UniffiLib) and enums are likewise reflected into by native code, not referenced from
# anywhere R8 can trace in this app's own Kotlin code beyond a handful of call sites.
-keep class org.matrix.rustcomponents.sdk.** { *; }
-keepclassmembers class org.matrix.rustcomponents.sdk.** { *; }

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
