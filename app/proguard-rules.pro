# ProGuard / R8 Rules for NOCTURNE

# Strip debugging and test logging calls in release builds to prevent telemetry leaks
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# Preserve Room Database Entities and DAOs
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Preserve Moshi JSON serialization classes
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json(name=*) <fields>;
}

# Preserve Coroutines and Flow infrastructure
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Line numbers and source files for release diagnostics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
