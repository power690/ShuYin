
-dontwarn com.mpatric.mp3agic.**
-keep class com.mpatric.mp3agic.** { *; }

-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class com.google.android.exoplayer2.** { *; }

-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
-keep @androidx.compose.runtime.Composable class *
-keep class androidx.compose.runtime.** { *; }

-dontwarn coil.**
-keep class coil.** { *; }

-keep class android.support.v4.media.** { *; }
-keep class android.support.v4.app.** { *; }
-dontwarn android.support.v4.**

-keepclassmembernames class kotlinx.** { volatile <fields>; }
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }

-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

-keep class com.xiaowei.player.ShuYinApp { *; }
-keep class com.xiaowei.player.MainActivity { *; }
-keep class com.xiaowei.player.player.PlaybackService { *; }
-keep class com.xiaowei.player.player.DesktopLyricService { *; }
-keep class com.xiaowei.player.player.DesktopLyricService$* { *; }
-keep class com.xiaowei.player.player.DesktopLyricSettings { *; }
-keep class com.xiaowei.player.player.MusicPlayerManager { *; }
-keep class com.xiaowei.player.player.MusicPlayerManager$PlayerState { *; }
-keep class com.xiaowei.player.player.MusicPlayerManager$PlayMode { *; }
-keep class com.xiaowei.player.player.MusicNotificationManager { *; }

-keep class com.xiaowei.player.i18n.Strings { *; }
-keep class com.xiaowei.player.i18n.Strings$* { *; }

-keep class com.xiaowei.player.data.Song { *; }
-keep class com.xiaowei.player.data.Song$* { *; }
-keep class com.xiaowei.player.data.Album { *; }
-keep class com.xiaowei.player.data.Artist { *; }
-keep class com.xiaowei.player.data.RecommendCard { *; }
-keep class com.xiaowei.player.data.LyricLine { *; }
-keep class com.xiaowei.player.data.PlaybackPrefs$SavedState { *; }

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

-keepclasseswithmembernames class * {
    native <methods>;
}
-dontobfuscate
-dontshrink
-keepattributes SourceFile,LineNumberTable
