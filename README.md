<div align="center">

# 条码生成器 · Android

<p>快速生成、识别和整理条码，也可在手机与浏览器之间通过局域网传输文件。</p>

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

一款以 Jetpack Compose 构建的 Android 条码工具。生成与识别在设备上完成；历史、收藏和样式保存在本机。需要跨设备传文件时，可开启局域网分享，让手机和普通浏览器互相发送文字、图片及附件。

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
| **局域网传输** | 手机与浏览器互传文字、图片和文件；使用二维码链接或手动访问码加入会话。 |
| **样式与设置** | 调整条码尺寸、间距、文字和配色；支持浅色/深色外观、应用更新检查。Beta 版可导出诊断日志。 |

## 主要页面

- **生成**：选择格式、输入内容并生成条码，可一次处理多行内容。
- **历史**：集中查看并复用最近生成的条码。
- **收藏**：管理常用条码与文件夹，也可备份和恢复收藏数据。
- **设置**：调整生成样式、外观和应用选项。

## 项目架构

```text
:app ───────────────> :core:domain
  ├────────────────> :core:data ─────────> :core:domain
  └────────────────> :core:lan-share ────> :core:domain
```

| 模块 | 职责 |
| --- | --- |
| `:app` | Compose 页面、ViewModel、页面状态与流程协调、Hilt 装配及 Android 系统交互 |
| `:core:domain` | 领域模型、业务规则、用例和 Repository/平台接口 |
| `:core:data` | Room、DataStore、Repository 实现、文件与平台适配、条码识别和更新能力 |
| `:core:lan-share` | 局域网 HTTP 服务与客户端、传输协议及内嵌浏览器页面 |

核心数据流：`Compose UI → ViewModel → Domain 接口/用例 → Data 实现`。局域网分享作为独立 Android Library 随应用一起打包。架构边界、状态管理和持久化说明见[架构文档](android/ARCHITECTURE.md)。

## 数据与连接安全

- 历史、收藏和应用偏好保存在设备本地；局域网分享仅在用户启动会话时开放。
- 分享会话使用随机令牌；也可以通过四位数字与大写英文字母组成的访问码加入。
- 局域网传输采用 HTTP，不提供传输加密。请仅在可信网络中使用，并在传输结束后关闭分享会话。

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

Beta APK 由 `beta` 分支上的 **Build Android Beta APK** 手动工作流签名打包并发布到 [Beta Release](https://github.com/luckyalanzhou/Barcode-generator-for-Android/releases/tag/android-test-v9.9.9)。APK 名称为 `BarcodeGeneratorBeta9.9.9.apk`；Beta 安装包与正式版分开，可并行安装。

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
