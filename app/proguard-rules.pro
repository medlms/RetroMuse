# ProGuard & R8 rules for RetroMuse Music Player

# Gson rules
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }
-keep class com.retro.grooveplayer.data.** { *; }

# Media3 & ExoPlayer rules
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil Image Loader rules
-keep class coil.** { *; }
-dontwarn coil.**

# AndroidX Compose & Lifecycle rules
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Google Mobile Ads (AdMob) rules
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**
-keep class com.google.ads.** { *; }
-dontwarn com.google.ads.**
