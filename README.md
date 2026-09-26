<div align="center">

# 条码生成器 · Android

<p>在 Android 设备上生成、识别和管理条码，并可与同一局域网中的浏览器互传文件。</p>

<p>
  <a href="https://github.com/luckyalanzhou/Barcode-generator-for-Android/releases/tag/android-test-v9.9.9"><strong>下载最新 Beta APK</strong></a>
  &nbsp;·&nbsp;
  <a href="android/ARCHITECTURE.md">查看架构说明</a>
</p>

<p>
  <a href="https://github.com/luckyalanzhou/Barcode-generator-for-Android/actions/workflows/build-android-testing.yml?query=branch%3Abeta"><img alt="Beta APK workflow" src="https://img.shields.io/github/actions/workflow/status/luckyalanzhou/Barcode-generator-for-Android/build-android-testing.yml?branch=beta&label=Beta%20build&logo=github"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white">
</p>

</div>

## 项目简介

一款以 Kotlin 和 Jetpack Compose 构建的 Android 条码工具。条码生成、历史与收藏管理均在应用内完成；历史、收藏和样式设置保存在本机。应用还内置局域网文件传输：手机作为创建端后，同一局域网内的设备可通过浏览器扫描二维码或打开连接地址加入，无需安装本应用。

| Beta 信息 | 详情 |
| --- | --- |
| 发布渠道 | `beta` 分支；与正式版分开安装 |
| 应用 ID | `com.luckyalanzhou.barcodegenerator.test` |
| Android 版本 | Android 8.0（API 26）及以上 |
| 版本号 | `versionName` 固定为 `9.9.9`，Beta Release 持续递增 `versionCode` |
| 下载 | [最新 Beta Release](https://github.com/luckyalanzhou/Barcode-generator-for-Android/releases/tag/android-test-v9.9.9) |

## 功能亮点

| 功能 | 说明 |
| --- | --- |
| **生成条码** | QR Code、Code 128-B、Code 39、EAN-13、EAN-8、UPC-A、ITF-14、Codabar；支持逐行批量输入和格式校验。 |
| **识别内容** | 使用相机或图片识别条码；图片文字可通过中文 OCR 提取，并可配置常见字符混淆纠正。 |
| **历史记录** | 浏览历史条码，重新生成、复制、分享或删除。 |
| **收藏整理** | 将条码保存到收藏夹，按文件夹整理和搜索；支持移动、重命名、导入与导出。 |
| **局域网传输** | Android 应用与浏览器互传文字、原始图片和文件；浏览器加入端无需安装应用。图片可预览，支持全屏查看与缩放。 |
| **多端消息区分** | 多个浏览器加入端同时传输时，按发送端区分气泡颜色；本机发送的气泡样式保持独立。 |
| **样式与设置** | 调整条码尺寸、间距、文字与颜色；支持浅色/深色外观、识别纠错选项和应用更新检查。Beta 版提供诊断日志导出。 |

## 主要页面

- **生成**：选择格式、输入内容并生成条码，可一次处理多行内容。
- **历史**：集中查看并复用最近生成的条码。
- **收藏**：按文件夹整理、搜索和移动常用条码，并可导入或导出收藏备份。
- **局域网分享**：在手机与浏览器间发送文字和文件；图片按原比例显示，支持预览。
- **设置**：调整生成样式、识别选项、外观和应用选项。

## 项目架构

```text
:app ───────────────> :core:domain
  ├────────────────> :core:data ─────────> :core:domain
  └────────────────> :core:lan-share ────> :core:domain
```

| 模块 | 职责 |
| --- | --- |
| `:app` | Compose 页面、ViewModel、页面状态与流程协调、Hilt 装配及 Android 系统交互 |
| `:core:domain` | 领域模型、验证规则、用例，以及数据和平台能力的接口 |
| `:core:data` | Room、DataStore、Repository 实现、收藏备份，以及条码识别、OCR 和更新的平台适配 |
| `:core:lan-share` | 独立 Android Library；提供局域网服务与客户端、文件传输、图片预览处理及内嵌网页界面 |

核心数据流：`Compose UI → ViewModel/Coordinator → Domain 接口 → Data 实现`。局域网传输通过 `:core:domain` 定义的 `LanShareGateway` 与界面解耦，并作为 Android Library 随应用打包。模块边界、状态管理和持久化说明见[架构文档](android/ARCHITECTURE.md)。

## 开发与验证

### 环境要求

- JDK 17
- Android SDK Platform 37
- 仓库自带 Gradle Wrapper

### 本地运行测试与 lint

在仓库根目录使用 Windows PowerShell：

```powershell
Set-Location .\android
.\gradlew.bat :core:domain:test :core:data:testDebugUnitTest :core:lan-share:testDebugUnitTest :app:testBetaDebugUnitTest :app:lintBetaRelease -PenableAppUnitTests=true --no-configuration-cache --max-workers=2
```

### Beta APK

Beta APK 由 `beta` 分支上的 **Build Android Beta APK** 手动工作流签名打包，并发布到 [Beta Release](https://github.com/luckyalanzhou/Barcode-generator-for-Android/releases/tag/android-test-v9.9.9)。远程工作流专注于 APK 打包与发布；单元测试和 lint 在本地验证。APK 名称为 `BarcodeGeneratorBeta9.9.9.apk`，Beta 安装包与正式版使用不同应用 ID，可并行安装。

## 仓库结构

```text
.
├─ .github/workflows/       # Beta 与正式版构建发布工作流
├─ README.md
└─ android/
   ├─ app/                  # Android 应用与 Compose 界面
   ├─ core/
   │  ├─ domain/            # 领域模型、规则和接口
   │  ├─ data/              # 数据持久化与平台实现
   │  └─ lan-share/         # 局域网传输模块
   ├─ ARCHITECTURE.md
   └─ beta-version.properties
```
