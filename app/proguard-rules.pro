# VD Player ProGuard / R8 Rules

# Strip Android Log.v, Log.d, Log.i calls in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep Kotlin Serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.tkofficial.videodownloder.**$$serializer { *; }
-keepclassmembers class com.tkofficial.videodownloder.** {
    *** Companion;
}
-keepclasseswithmembers class com.tkofficial.videodownloder.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room Entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Keep Moshi models, generated adapters & CustomApiConfig
-keep class com.tkofficial.videodownloder.network.CustomApiConfig { *; }
-keepclassmembers class com.tkofficial.videodownloder.network.** { *; }
-keepclasseswithmembers class * extends com.squareup.moshi.JsonAdapter {
    <init>(com.squareup.moshi.Moshi);
}
-keepattributes Signature, RuntimeVisibleAnnotations, AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface com.tkofficial.videodownloder.network.CustomExtractorService {
    <methods>;
}

# Keep Media3
-keep class androidx.media3.** { *; }

# Keep Biometric
-keep class androidx.biometric.** { *; }

# Keep App Models and Navigation Routes (kotlinx.serialization + Moshi)
-keep class com.tkofficial.videodownloder.models.** { *; }
-keep class com.tkofficial.videodownloder.navigation.** { *; }
-keep class com.tkofficial.videodownloder.data.AppSettings { *; }

# Keep Coil & OkHttp runtime classes cleanly
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class coil.** { *; }

