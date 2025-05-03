# ProGuard rules for KorrentAndroid

# Keep Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }
-keep class kotlin.** { *; }
-keepattributes Signature,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,InnerClasses,EnclosingMethod

# Keep Coroutines internals
-keepnames class kotlinx.coroutines.internal.** { *; }
-keepclassmembers class kotlinx.coroutines.flow.** { *; }

# Keep Ktor classes (adjust based on specific features used)
-keep class io.ktor.** { *; }
-keepnames class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep Kotlin Serialization classes used for data models
# Replace com.example.korrent.data.model.** with your actual model package(s) if different
-keep class com.example.korrent.data.model.** { *; }
-keepclassmembers class com.example.korrent.data.model.** { *; }
-keepattributes *Annotation*

# Keep Compose runtime and UI elements
-keep class androidx.compose.** { *; }
-keep classmembernames class androidx.compose.runtime.reflect.** { *; }
-keepclassmembers class androidx.compose.ui.** { *; }
-keepclassmembers class androidx.compose.material.** { *; }
-keepclassmembers class androidx.compose.material3.** { *; }
-keepclassmembers class androidx.compose.foundation.** { *; }
-keepclassmembers class com.google.accompanist.webview.** { *; } # For Accompanist WebView

# Keep default constructors for Activities, Services, etc.
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Add any library-specific rules here if needed