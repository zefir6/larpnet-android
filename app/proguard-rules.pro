# Add project specific ProGuard rules here.
# Minify is disabled for v1 (see app/build.gradle.kts); these rules matter once it's enabled.

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
