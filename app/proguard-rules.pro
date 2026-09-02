# Default ProGuard rules.

# --- 第三方库 ---
-dontwarn com.mpatric.mp3agic.**
-keep class com.mpatric.mp3agic.** { *; }

# --- Media3 / ExoPlayer ---
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class com.google.android.exoplayer2.** { *; }

# --- Compose 运行时反射 ---
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
# Compose Metadata 注解处理
-keep @androidx.compose.runtime.Composable class *
-keep class androidx.compose.runtime.** { *; }

# --- Coil 图片加载 ---
-dontwarn coil.**
-keep class coil.** { *; }

# --- AndroidX Media（旧 MediaSession）---
-keep class android.support.v4.media.** { *; }
-keep class android.support.v4.app.** { *; }
-dontwarn android.support.v4.**

# --- Kotlin Coroutines ---
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }

# --- Kotlin Metadata（反射需要）---
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# --- 项目自身：保留反射使用的入口 ---
# Application、Activity、Service 入口由 AndroidManifest 引用，需保留
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

# i18n 字符串表（保留 key 和翻译，避免被优化）
-keep class com.xiaowei.player.i18n.Strings { *; }
-keep class com.xiaowei.player.i18n.Strings$* { *; }

# Song 数据类（被 MediaSession / Notification 序列化）
-keep class com.xiaowei.player.data.Song { *; }
-keep class com.xiaowei.player.data.Song$* { *; }
-keep class com.xiaowei.player.data.Album { *; }
-keep class com.xiaowei.player.data.Artist { *; }
-keep class com.xiaowei.player.data.RecommendCard { *; }
-keep class com.xiaowei.player.data.LyricLine { *; }
-keep class com.xiaowei.player.data.PlaybackPrefs$SavedState { *; }

# 枚举（避免被混淆后 name() 失效）
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Native 方法
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontobfuscate
-dontshrink
-keepattributes SourceFile,LineNumberTable
