<div align="center">

# 条码生成器 · Android

<p>在 Android 设备上生成、识别和管理条码，并可与同一局域网中的浏览器互传文件。</p>

<p>
  <a href="https://github.com/luckyalanzhou/Barcode-generator-for-Android/releases/tag/android-test-v9.9.9"><strong>下载最新 Beta APK</strong></a>
  &nbsp;·&nbsp;
  <a href="android/ARCHITECTURE.md">架构原则</a>
  &nbsp;·&nbsp;
  <a href="android/core/lan-share/PROTOCOL.md">LAN Share 协议</a>
  &nbsp;·&nbsp;
  <a href="android/DEVELOPMENT.md">开发验证</a>
  &nbsp;·&nbsp;
  <a href="项目操作与变更追踪指南.md">新手操作指南：找代码、看改动</a>
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
| **收藏整理** | 将条码保存到收藏夹，按文件夹整理和搜索；支持移动、重命名、导入与导出，并保留备份中的条目顺序和逐条格式。 |
| **局域网传输** | Android 应用与浏览器互传文字、原始图片和文件；文字经 WebSocket 传递，文件经 HTTP 传输。浏览器加入端无需安装应用；图片可预览、全屏查看与缩放。 |
| **多端消息区分** | 多个浏览器加入端同时传输时，按发送端区分气泡颜色；本机发送的气泡样式保持独立。 |
| **样式与设置** | 调整条码尺寸、间距与文字显示；支持浅色/深色外观。“显示与动效”集中管理减少动态效果与提高对比度。另有识别纠错选项和应用更新检查，Beta 版提供诊断日志导出。 |

## 主要页面

- **生成**：选择格式、输入内容并生成条码，可一次处理多行内容。
- **历史**：集中查看并复用最近生成的条码。
- **收藏**：按文件夹整理、搜索和移动常用条码，并可导入或导出收藏备份。
- **文件传输**：手机作为创建端，与同一局域网的浏览器互传文字和文件；图片按比例预览，支持全屏查看和缩放。
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
| `:architecture-tests` | 仅用于开发验证的架构约束测试；不作为应用运行依赖，也不打入 APK |

核心数据流：`Compose UI → ViewModel/Coordinator → Domain 接口 → Data 实现`。局域网传输通过 `:core:domain` 定义的 `LanShareGateway` 与界面解耦；`:core:lan-share` 作为 Android Library 随应用打包。稳定的模块边界与状态原则见[架构原则](android/ARCHITECTURE.md)；传输行为和安全边界见[LAN Share 协议说明](android/core/lan-share/PROTOCOL.md)。

### 展开查看关键文件

以下路径均相对于 GitHub 仓库根目录；点击文件链接可查看源码。页面 UI、页面状态与底层实现分开列出，完整模块边界仍以[架构原则](android/ARCHITECTURE.md)为准。

<details>
<summary><strong>:app</strong> — Android 应用、Compose 页面、交互状态与平台装配</summary>

**应用入口与导航**

- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/BarcodeUi.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/BarcodeUi.kt) — 组合应用界面与主要页面入口。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/ComposeAppShell.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/ComposeAppShell.kt) — 组织主界面、页面切换、弹窗和底部 Tab。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/navigation/AppNavigationViewModel.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/navigation/AppNavigationViewModel.kt) — 持有并协调应用级导航状态。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/di/AppModule.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/di/AppModule.kt) — 在应用组合层绑定各模块实现和平台适配器。

**主要功能页面**

- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/generate/GenerateContent.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/generate/GenerateContent.kt) — 生成页界面；接收显示状态并把输入、生成等操作通过回调交给上层。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/generate/GenerateViewModel.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/generate/GenerateViewModel.kt) — 协调生成输入、校验、生成请求和页面状态。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/history/ComposeHistoryScreen.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/history/ComposeHistoryScreen.kt) — 绘制历史记录列表及其页面交互。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/history/HistoryViewModel.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/history/HistoryViewModel.kt) — 提供历史记录状态并协调复用、编辑和删除操作。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/favorites/content/FavoritesContent.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/favorites/content/FavoritesContent.kt) — 展示收藏文件夹、条码列表和搜索结果。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/favorites/FavoritesViewModel.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/favorites/FavoritesViewModel.kt) — 协调收藏查询、文件夹与条码操作、备份导入导出。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/results/ResultsContent.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/results/ResultsContent.kt) — 展示生成结果、条码列表及结果页操作按钮。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/results/ResultsViewModel.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/results/ResultsViewModel.kt) — 管理结果页状态及保存、分享、编辑等操作协调。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/settings/SettingsContent.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/settings/SettingsContent.kt) — 绘制设置页及设置项交互。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/settings/SettingsViewModel.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/settings/SettingsViewModel.kt) — 读取和更新应用设置状态。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/lanshare/LanShareContent.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/lanshare/LanShareContent.kt) — 绘制文件传输页、连接信息、消息气泡和发送入口。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/lanshare/LanShareViewModel.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/lanshare/LanShareViewModel.kt) — 协调局域网会话、连接状态、消息和文件传输状态。

</details>

<details>
<summary><strong>:core:domain</strong> — 与 Android UI 和存储实现隔离的业务规则、模型与接口</summary>

- [`android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/BarcodeModels.kt`](android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/BarcodeModels.kt) — 定义条码、历史和收藏等核心业务模型。
- [`android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/BarcodeRepository.kt`](android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/BarcodeRepository.kt) — 定义业务层访问条码与收藏数据的仓库契约。
- [`android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/GenerateBarcodesUseCase.kt`](android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/GenerateBarcodesUseCase.kt) — 执行批量条码生成的业务流程。
- [`android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/BarcodeValidator.kt`](android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/BarcodeValidator.kt) — 集中处理条码格式和输入内容校验。
- [`android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/FavoritesImportPlanner.kt`](android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/FavoritesImportPlanner.kt) — 规划收藏备份导入时的条目、文件夹和顺序处理。
- [`android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/LanShareModels.kt`](android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/LanShareModels.kt) — 定义应用与局域网传输模块之间共享的领域数据和能力契约。

</details>

<details>
<summary><strong>:core:data</strong> — 本地持久化、数据仓库和 Android 平台能力适配</summary>

- [`android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/BarcodeDatabase.kt`](android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/BarcodeDatabase.kt) — 定义 Room 数据库及本地数据表访问入口。
- [`android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/RoomBarcodeRepository.kt`](android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/RoomBarcodeRepository.kt) — 将领域仓库接口落实为 Room 数据读写。
- [`android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/SettingsStore.kt`](android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/SettingsStore.kt) — 持久化用户偏好和生成样式设置。
- [`android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/FavoritesBackupUseCase.kt`](android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/FavoritesBackupUseCase.kt) — 处理收藏数据备份的导入和导出。
- [`android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/platform/ZxingBarcodeDecodeGateway.kt`](android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/platform/ZxingBarcodeDecodeGateway.kt) — 通过 ZXing 实现条码图像识别能力。
- [`android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/platform/MlKitOcrTextGateway.kt`](android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/platform/MlKitOcrTextGateway.kt) — 通过 ML Kit 实现图片文字识别能力。

</details>

<details>
<summary><strong>:core:lan-share</strong> — 局域网 HTTP/WebSocket 服务、客户端与网页端</summary>

- [`android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/server/LanShareManager.kt`](android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/server/LanShareManager.kt) — 管理创建端局域网服务的启动、停止和会话生命周期。
- [`android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/server/LanShareServer.kt`](android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/server/LanShareServer.kt) — 接收浏览器消息和文件，并提供会话内的 HTTP/WebSocket 服务。
- [`android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/client/LanShareClient.kt`](android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/client/LanShareClient.kt) — 管理客户端连接及消息、文件传输请求。
- [`android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/protocol/LanShareModels.kt`](android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/protocol/LanShareModels.kt) — 定义网络消息、文件信息及传输状态模型。
- [`android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/protocol/LanShareStreamBuffer.kt`](android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/protocol/LanShareStreamBuffer.kt) — 管理文件流接收过程中的缓冲与写入。
- [`android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/web/LanShareWebScript.kt`](android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/web/LanShareWebScript.kt) — 提供浏览器端传输页面的交互脚本。
- [`android/core/lan-share/PROTOCOL.md`](android/core/lan-share/PROTOCOL.md) — 说明网络接口、消息行为和安全边界。

</details>

<details>
<summary><strong>:architecture-tests</strong> — 验证架构边界的开发测试，不参与应用运行</summary>

- [`android/architecture-tests/src/test/kotlin/com/luckyalanzhou/barcodegenerator/architecture/ArchitectureBoundaryTest.kt`](android/architecture-tests/src/test/kotlin/com/luckyalanzhou/barcodegenerator/architecture/ArchitectureBoundaryTest.kt) — 自动检查模块和包之间的依赖约束。

</details>

## 验证与构建

先按[开发验证指南](android/DEVELOPMENT.md)在本地运行相关测试与 lint，再使用适配分支的手动工作流构建 APK。远程 APK 工作流只负责签名、打包和发布，不重复运行测试或 lint。

| 用途 | 分支与触发方式 | 工作流职责 |
| --- | --- | --- |
| Beta 测试版 | `beta` 分支，手动运行 [Build Android Beta APK](.github/workflows/build-android-testing.yml) | 生成并发布 Beta APK。可从 [最新 Beta Release](https://github.com/luckyalanzhou/Barcode-generator-for-Android/releases/tag/android-test-v9.9.9) 获取；Beta 与正式版应用可并行安装。 |
| 正式版 | 仅在用户确认发布后使用 `main` 分支，手动运行 [Build Android Release APK](.github/workflows/build-android-official.yml)，并填写版本信息 | 生成并发布正式版 APK。正式版与 Beta 使用不同应用 ID。 |
| PR 架构检查 | 面向 `beta` 或 `main` 的 PR | [Architecture checks](.github/workflows/architecture-checks.yml) 默认显示为跳过；仅在仓库变量 `ENABLE_ARCHITECTURE_PR_CHECKS=true` 时运行，且不会自动成为合并必需项。 |

本地固定工具目录、验证命令、远程缓存边界与工作流操作步骤见[开发验证指南](android/DEVELOPMENT.md)。第一次参与项目或想学习如何找代码、查看改动和检查构建，可从[新手操作指南](项目操作与变更追踪指南.md)开始。

## 仓库结构

```text
.
├─ .github/workflows/       # Beta/正式版构建发布及 PR 架构检查工作流
├─ .github/pull_request_template.md
├─ README.md
├─ 项目操作与变更追踪指南.md
└─ android/
   ├─ app/                  # Android 应用与 Compose 界面
   ├─ architecture-tests/   # Kotlin 包层架构约束测试
   ├─ core/
   │  ├─ domain/            # 领域模型、规则和接口
   │  ├─ data/              # 数据持久化与平台实现
   │  └─ lan-share/         # 局域网传输模块
   ├─ ARCHITECTURE.md
   ├─ DEVELOPMENT.md
   └─ beta-version.properties
```
