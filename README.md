# 鼠音 ShuYin

一款开源的 Android 本地音乐播放器，基于 Jetpack Compose 构建，支持 FLAC 无损解码、桌面歌词、悬浮歌词、40 种语言界面，采用 iOS 风格的页面转场动画。

## ✨ 功能特性

### 🎵 音乐播放
- **FLAC 无损解码**：使用 HeHang0 预编译的 ExoPlayer FLAC 扩展（基于 libFLAC native 解码），不依赖系统 MediaCodec，魔改 ROM 也能完美播放 FLAC
- **全格式支持**：MP3 / FLAC / OGG / M4A / AAC / WAV / OPUS
- **播放队列管理**：支持播放列表、循环模式（顺序/单曲/随机）、上一首/下一首
- **播放状态恢复**：杀后台后重启自动恢复上次播放位置和队列
- **前台服务**：通知栏控制播放，不被系统杀死

### 🎨 界面设计
- **Jetpack Compose 全量构建**：纯 Compose UI，Material 3 设计
- **iOS 风格转场动画**：二级页面 push 时主页压缩 + 高斯模糊（Android 12+），弹簧动画
- **动态取色**：Android 12+ 跟随系统壁纸动态取色
- **主题色自定义**：10 种预设主题色，Android 11 及以下跟随主题色
- **深色模式**：跟随系统或强制深色/浅色

### 📝 歌词与封面
- **桌面悬浮歌词**：独立悬浮窗显示歌词，支持拖动、自定义样式
- **内嵌歌词解析**：自动读取 MP3 USLT 字段 / FLAC LYRICS 块 / 外部 .lrc 文件
- **内嵌封面提取**：直接从文件二进制读取封面（绕过 MediaStore 兼容性问题）
- **三层封面缓存**：内存映射 + 字节缓存 + 磁盘缓存，消除滑动闪烁

### 🌍 多语言支持
- **40 种语言界面**：中文（简/繁）/ 英语 / 日语 / 韩语 / 法语 / 德语 / 西班牙语 / 俄语 / 阿拉伯语 / 印地语 / 泰语 / 越南语 等
- **跟随系统语言**：默认跟随系统，可手动切换
- **RTL 布局支持**：阿拉伯语等 RTL 语言自动镜像

### 📂 自定义路径扫描
- **指定目录扫描**：在设置中输入或选择要扫描的音乐目录
- **原生文件选择器**：集成系统文件选择器一键选目录
- **路径持久化**：杀后台重开仍保留自定义路径
- **中文路径支持**：完整支持中文路径的扫描和播放

### ⭐ 收藏与个人
- **歌曲收藏**：一键收藏喜欢的歌曲，独立收藏列表
- **个人资料**：自定义昵称和头像（原生 PhotoPicker）
- **播放历史**：搜索历史记录

### 🔍 搜索
- **全文搜索**：按歌名 / 歌手 / 专辑搜索
- **热门关键词**：按系统语言推荐热门歌手
- **搜索历史**：Room 数据库持久化搜索记录

## 📱 截图

（待补充）

## 🛠️ 技术栈

| 类别 | 技术 |
|------|------|
| **UI** | Jetpack Compose + Material 3 |
| **播放器** | AndroidX Media3 (ExoPlayer) |
| **FLAC 解码** | HeHang0 预编译的 ExoPlayer FLAC 扩展（libFLAC native）|
| **数据库** | Room（收藏 / 搜索历史 / 播放状态 / 桌面歌词设置 / 用户资料）|
| **图片加载** | Coil（内存缓存 25% + 磁盘缓存 100MB）|
| **架构** | MVVM + ViewModel + StateFlow |
| **导航** | HorizontalPager（官方位移动画）+ 自定义二级页面转场 |
| **最低支持** | Android 6.0 (API 23) |
| **目标版本** | Android 17 (API 37) |

## 📦 下载安装

### 方式一：直接下载 APK
前往 [Releases](../../releases) 页面下载最新的 `鼠音-debug-v1.2.0.apk`，直接安装即可。

### 方式二：自行编译
```bash
git clone https://github.com/yourname/ShuYin.git
cd ShuYin
./gradlew :app:assembleDebug
```
编译产物：`app/build/outputs/apk/debug/app-debug.apk`

**环境要求**：
- JDK 21+
- Android SDK 37+（compileSdk）
- Gradle 9.6.1（项目自带 gradlew 会自动下载）

## 🎬 转场动画说明

本项目的二级页面转场动画（右侧滑入 + 缩放 + 圆角 + 高斯模糊背景）参考并使用了 [SPICaMusic_Android](https://github.com/yangSpica27/SPICaMusic_Android.git) 项目的动画方案。

### 动画效果
- **二级页面进场**：从右侧滑入 + 0.5→1.0 缩放 + 28dp→0 圆角 + 阴影淡出（弹簧动画）
- **背景层压缩**：主页缩放到 0.74 + 左移 6% + 24dp 高斯模糊（仅 Android 12+）
- **快速点击防卡死**：串行化动画状态机，避免快速 push/pop 导致 UI 卡住

### 致谢
感谢 [yangSpica27](https://github.com/yangSpica27) 的 [SPICaMusic_Android](https://github.com/yangSpica27/SPICaMusic_Android.git) 项目提供的优秀动画方案。

## 🌐 多语言贡献

本项目支持 40 种语言，所有字符串集中在 `app/src/main/kotlin/com/xiaowei/player/i18n/Strings.kt`，可用项目自带的 Python 脚本批量管理：

```bash
cd scripts
python3 i18n_helper.py add new_strings.json    # 批量添加字符串
python3 i18n_helper.py validate                 # 验证完整性
python3 i18n_helper.py list                     # 列出所有 key
```

详见 `scripts/I18N_HELPER_GUIDE.md`。

如果发现翻译有误或想新增语言，欢迎提交 PR。

## 📋 权限说明

| 权限 | 用途 |
|------|------|
| `READ_MEDIA_AUDIO` (Android 13+) | 读取音乐文件 |
| `READ_EXTERNAL_STORAGE` (Android 12-) | 读取音乐文件 |
| `MANAGE_EXTERNAL_STORAGE` (Android 11+) | 自定义路径扫描（可选）|
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | 播放前台服务 |
| `POST_NOTIFICATIONS` (Android 13+) | 通知栏控制 |
| `SYSTEM_ALERT_WINDOW` | 悬浮歌词 |

## 📄 开源协议

本项目基于 **GNU General Public License v3.0 (GPL-3.0)** 协议开源。

### GPL-3.0 协议要点
- ✅ **自由使用**：可免费用于个人学习和商业用途
- ✅ **自由修改**：可任意修改源码
- ✅ **自由分发**：可分发原版或修改版
- ⚠️ **开源要求**：任何基于本项目的衍生项目必须同样以 GPL-3.0 协议开源
- ⚠️ **源码提供**：分发二进制时必须提供完整源码
- ⚠️ **协议保留**：必须保留原作者版权声明和协议文本

完整协议文本见 [LICENSE](LICENSE) 文件或 [GNU GPL-3.0 官网](https://www.gnu.org/licenses/gpl-3.0.html)。

### 第三方依赖协议
本项目使用的第三方库协议如下：
- Jetpack Compose / Media3 / Room / Coil：Apache License 2.0
- HeHang0 ExoPlayer FLAC 扩展：Apache License 2.0
- mp3agic：MIT License

## 💬 项目交流与反馈

### QQ 群
**767301251**

欢迎加入 QQ 群交流使用心得、反馈 Bug、提出功能建议、参与开发讨论。

### Issue 反馈
遇到 Bug 或有功能建议，请前往 [Issues](../../issues) 页面提交，请包含：
- 设备型号和系统版本
- App 版本
- 复现步骤
- 预期行为和实际行为

## 🙏 致谢

- [SPICaMusic_Android](https://github.com/yangSpica27/SPICaMusic_Android.git) - 二级页面转场动画方案
- [jianyin](https://github.com/qianqianhhh2/jianyin.git) - 部分代码参考
- [HeHang0](https://github.com/HeHang0) - ExoPlayer FLAC 扩展预编译版
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - 声明式 UI 框架
- [AndroidX Media3](https://developer.android.com/media/media3) - 媒体播放库
- [Coil](https://coil-kt.github.io/coil/) - 图片加载库
- 所有翻译贡献者和测试用户

## 📊 项目状态

- **当前版本**：v1.2.0
- **最低支持**：Android 6.0 (API 23)
- **目标版本**：Android 17 (API 37)

---

# ShuYin (English)

An open-source Android local music player built with Jetpack Compose, featuring FLAC lossless decoding, desktop floating lyrics, 40-language UI, and iOS-style page transition animations.

## ✨ Features

### 🎵 Music Playback
- **FLAC Lossless Decoding**: Uses HeHang0's precompiled ExoPlayer FLAC extension (based on libFLAC native decoding), independent of system MediaCodec - works perfectly even on modified ROMs
- **Full Format Support**: MP3 / FLAC / OGG / M4A / AAC / WAV / OPUS
- **Queue Management**: Playlist, loop modes (sequence / single / shuffle), previous / next
- **State Restoration**: Auto-restore last playback position and queue after restart
- **Foreground Service**: Notification bar playback control, immune to system kill

### 🎨 UI Design
- **Fully Jetpack Compose**: Pure Compose UI with Material 3 design
- **iOS-style Transitions**: Secondary pages push with background compression + Gaussian blur (Android 12+), spring animations
- **Dynamic Color**: Android 12+ follows system wallpaper for dynamic theming
- **Theme Customization**: 10 preset theme colors; Android 11 and below follows theme color
- **Dark Mode**: Follow system or force dark/light

### 📝 Lyrics & Cover Art
- **Floating Desktop Lyrics**: Independent floating window with draggable, customizable styling
- **Embedded Lyrics Parsing**: Auto-reads MP3 USLT tags / FLAC LYRICS blocks / external .lrc files
- **Embedded Cover Extraction**: Reads cover art directly from file binary (bypasses MediaStore compatibility issues)
- **Three-layer Cover Cache**: Memory mapping + byte cache + disk cache, eliminates scroll flicker

### 🌍 Multilingual Support
- **40 Language UI**: Chinese (Simplified/Traditional) / English / Japanese / Korean / French / German / Spanish / Russian / Arabic / Hindi / Thai / Vietnamese and more
- **Follow System Language**: Defaults to system language, manually switchable
- **RTL Layout Support**: Auto-mirror for RTL languages like Arabic

### 📂 Custom Path Scanning
- **Directory Scanning**: Input or pick a music directory to scan in settings
- **Native File Picker**: Integrated system file picker for one-tap directory selection
- **Path Persistence**: Custom path retained after app restart
- **Chinese Path Support**: Full support for Chinese path scanning and playback

### ⭐ Favorites & Profile
- **Song Favorites**: One-tap favorite songs with independent favorites list
- **User Profile**: Custom nickname and avatar (native PhotoPicker)
- **Playback History**: Search history records

### 🔍 Search
- **Full-text Search**: Search by song name / artist / album
- **Hot Keywords**: Recommended popular artists by system language
- **Search History**: Room database persistent search records

## 📱 Screenshots

(To be added)

## 🛠️ Tech Stack

| Category | Technology |
|----------|-----------|
| **UI** | Jetpack Compose + Material 3 |
| **Player** | AndroidX Media3 (ExoPlayer) |
| **FLAC Decoding** | HeHang0's precompiled ExoPlayer FLAC extension (libFLAC native) |
| **Database** | Room (favorites / search history / playback state / desktop lyrics settings / user profile) |
| **Image Loading** | Coil (25% memory cache + 100MB disk cache) |
| **Architecture** | MVVM + ViewModel + StateFlow |
| **Navigation** | HorizontalPager (official slide animation) + custom secondary page transitions |
| **Min SDK** | Android 6.0 (API 23) |
| **Target SDK** | Android 17 (API 37) |

## 📦 Download & Install

### Option 1: Download APK
Go to the [Releases](../../releases) page to download the latest `ShuYin-debug-v1.2.0.apk` and install directly.

### Option 2: Build from Source
```bash
git clone https://github.com/yourname/ShuYin.git
cd ShuYin
./gradlew :app:assembleDebug
```
Build output: `app/build/outputs/apk/debug/app-debug.apk`

**Requirements**:
- JDK 21+
- Android SDK 37+ (compileSdk)
- Gradle 9.6.1 (auto-downloaded via gradlew)

## 🎬 Transition Animation Notice

The secondary page transition animations in this project (right slide-in + scale + rounded corners + Gaussian blur background) reference and use the animation solution from the [SPICaMusic_Android](https://github.com/yangSpica27/SPICaMusic_Android.git) project.

### Animation Effects
- **Secondary Page Entry**: Slide-in from right + 0.5→1.0 scale + 28dp→0 rounded corners + shadow fade-out (spring animation)
- **Background Compression**: Home page scales to 0.74 + shifts left 6% + 24dp Gaussian blur (Android 12+ only)
- **Fast Click Protection**: Serialized animation state machine prevents UI freeze on rapid push/pop

### Acknowledgment
Thanks to [yangSpica27](https://github.com/yangSpica27) for the excellent animation solution from [SPICaMusic_Android](https://github.com/yangSpica27/SPICaMusic_Android.git).

## 🌐 Multilingual Contribution

This project supports 40 languages. All strings are centralized in `app/src/main/kotlin/com/xiaowei/player/i18n/Strings.kt` and can be batch-managed using the built-in Python script:

```bash
cd scripts
python3 i18n_helper.py add new_strings.json    # Batch add strings
python3 i18n_helper.py validate                 # Validate completeness
python3 i18n_helper.py list                     # List all keys
```

See `scripts/I18N_HELPER_GUIDE.md` for details.

If you find translation errors or want to add a new language, PRs are welcome.

## 📋 Permissions

| Permission | Purpose |
|-----------|---------|
| `READ_MEDIA_AUDIO` (Android 13+) | Read music files |
| `READ_EXTERNAL_STORAGE` (Android 12-) | Read music files |
| `MANAGE_EXTERNAL_STORAGE` (Android 11+) | Custom path scanning (optional) |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Playback foreground service |
| `POST_NOTIFICATIONS` (Android 13+) | Notification bar control |
| `SYSTEM_ALERT_WINDOW` | Floating lyrics |

## 📄 License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

### GPL-3.0 Key Points
- ✅ **Free Use**: Free for personal and commercial use
- ✅ **Free Modification**: Modify source code freely
- ✅ **Free Distribution**: Distribute original or modified versions
- ⚠️ **Open Source Requirement**: Any derivative project must also be open-sourced under GPL-3.0
- ⚠️ **Source Code Provision**: Must provide complete source code when distributing binaries
- ⚠️ **License Preservation**: Must retain original copyright notices and license text

See the [LICENSE](LICENSE) file or [GNU GPL-3.0 official site](https://www.gnu.org/licenses/gpl-3.0.html) for the full license text.

### Third-party License
- Jetpack Compose / Media3 / Room / Coil: Apache License 2.0
- HeHang0 ExoPlayer FLAC extension: Apache License 2.0
- mp3agic: MIT License

## 💬 Communication & Feedback

### QQ Group
**767301251**

Feel free to join the QQ group to share experiences, report bugs, suggest features, or participate in development discussions.

### Issue Reports
For bugs or feature suggestions, please go to the [Issues](../../issues) page and include:
- Device model and OS version
- App version
- Reproduction steps
- Expected and actual behavior

## 🙏 Acknowledgments

- [SPICaMusic_Android](https://github.com/yangSpica27/SPICaMusic_Android.git) - Secondary page transition animation solution
- [jianyin](https://github.com/qianqianhhh2/jianyin.git) - Partial code reference
- [HeHang0](https://github.com/HeHang0) - Precompiled ExoPlayer FLAC extension
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Declarative UI framework
- [AndroidX Media3](https://developer.android.com/media/media3) - Media playback library
- [Coil](https://coil-kt.github.io/coil/) - Image loading library
- All translation contributors and testers

## 📊 Project Status

- **Current Version**: v1.2.0
- **Min Support**: Android 6.0 (API 23)
- **Target Version**: Android 17 (API 37)
