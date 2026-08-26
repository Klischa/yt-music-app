# ProGuard rules for YT Music App
-keep class com.klischa.ytmusic.** { *; }
-keep class androidx.media3.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
