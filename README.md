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
| `:app` | 应用页面、页面状态、导航和 Android 功能接入 |
| `:core:domain` | 条码业务规则、数据定义和功能接口 |
| `:core:data` | 保存条码和设置，处理备份、扫码和文字识别 |
| `:core:lan-share` | 手机与浏览器之间的局域网传输功能 |
| `:architecture-tests` | 自动检查模块依赖是否符合约定，不打入 APK |

主要调用关系：`界面 → 页面状态 → 业务规则 → 数据实现`。局域网传输通过接口与页面连接，具体边界见[架构原则](android/ARCHITECTURE.md)；传输规则见[LAN Share 协议说明](android/core/lan-share/PROTOCOL.md)。

### 展开查看关键文件

下面列出常用文件的位置和用途。点击路径可查看源码；完整架构规则见[架构原则](android/ARCHITECTURE.md)。

<details>
<summary><strong>:app</strong> — 应用页面、操作和导航</summary>

**应用入口**

- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/BarcodeUi.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/BarcodeUi.kt) — 汇总页面，是应用界面的入口。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/ComposeAppShell.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/ComposeAppShell.kt) — 管理主界面、Tab 切换和弹窗。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/navigation/AppNavigationViewModel.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/navigation/AppNavigationViewModel.kt) — 记录当前页面并处理页面跳转。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/di/AppModule.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/di/AppModule.kt) — 把各模块的功能接入应用。

**功能页面和操作**

- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/generate/GenerateContent.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/generate/GenerateContent.kt) — 显示输入、格式选择和生成按钮。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/generate/GenerateViewModel.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/generate/GenerateViewModel.kt) — 处理输入、校验、生成和页面状态。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/history/ComposeHistoryScreen.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/history/ComposeHistoryScreen.kt) — 显示历史条码和操作入口。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/history/HistoryViewModel.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/history/HistoryViewModel.kt) — 加载历史，处理重新使用、编辑和删除。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/favorites/content/FavoritesContent.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/favorites/content/FavoritesContent.kt) — 显示收藏文件夹、条码和搜索结果。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/favorites/FavoritesViewModel.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/favorites/FavoritesViewModel.kt) — 管理收藏、文件夹和备份操作。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/results/ResultsContent.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/results/ResultsContent.kt) — 显示生成结果及编辑、收藏、分享、保存按钮。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/results/ResultsViewModel.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/results/ResultsViewModel.kt) — 管理结果，并处理编辑、收藏、分享和保存。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/settings/SettingsContent.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/settings/SettingsContent.kt) — 显示设置项和开关。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/settings/SettingsViewModel.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/settings/SettingsViewModel.kt) — 读取和保存用户设置。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/lanshare/LanShareContent.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/lanshare/LanShareContent.kt) — 显示连接信息、消息和文件发送入口。
- [`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/lanshare/LanShareViewModel.kt`](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/lanshare/LanShareViewModel.kt) — 管理连接、消息和传输进度。

</details>

<details>
<summary><strong>:core:domain</strong> — 条码业务规则和数据定义</summary>

- [`android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/BarcodeModels.kt`](android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/BarcodeModels.kt) — 定义条码、历史和收藏的数据。
- [`android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/BarcodeRepository.kt`](android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/BarcodeRepository.kt) — 说明业务层怎样读写条码和收藏。
- [`android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/GenerateBarcodesUseCase.kt`](android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/GenerateBarcodesUseCase.kt) — 按输入生成一批条码。
- [`android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/BarcodeValidator.kt`](android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/BarcodeValidator.kt) — 检查内容是否符合所选格式。
- [`android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/FavoritesImportPlanner.kt`](android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/FavoritesImportPlanner.kt) — 整理收藏备份导入后的条目和顺序。
- [`android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/LanShareModels.kt`](android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/LanShareModels.kt) — 定义应用和局域网传输模块交换的数据。

</details>

<details>
<summary><strong>:core:data</strong> — 本地数据保存和系统能力接入</summary>

- [`android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/BarcodeDatabase.kt`](android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/BarcodeDatabase.kt) — 本地条码数据库。
- [`android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/RoomBarcodeRepository.kt`](android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/RoomBarcodeRepository.kt) — 用数据库读取和保存条码、历史与收藏。
- [`android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/SettingsStore.kt`](android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/SettingsStore.kt) — 保存应用设置。
- [`android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/FavoritesBackupUseCase.kt`](android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/FavoritesBackupUseCase.kt) — 导入和导出收藏备份。
- [`android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/platform/ZxingBarcodeDecodeGateway.kt`](android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/platform/ZxingBarcodeDecodeGateway.kt) — 识别图片里的条码。
- [`android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/platform/MlKitOcrTextGateway.kt`](android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/platform/MlKitOcrTextGateway.kt) — 识别图片里的文字。

</details>

<details>
<summary><strong>:core:lan-share</strong> — 手机与浏览器互传文件</summary>

- [`android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/server/LanShareManager.kt`](android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/server/LanShareManager.kt) — 启动和关闭局域网服务。
- [`android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/server/LanShareServer.kt`](android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/server/LanShareServer.kt) — 接收浏览器消息和文件，并提供文件下载。
- [`android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/client/LanShareClient.kt`](android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/client/LanShareClient.kt) — 连接局域网服务，发送消息和文件。
- [`android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/protocol/LanShareModels.kt`](android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/protocol/LanShareModels.kt) — 定义传输消息和文件信息。
- [`android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/protocol/LanShareStreamBuffer.kt`](android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/protocol/LanShareStreamBuffer.kt) — 接收文件数据并写入文件。
- [`android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/web/LanShareWebScript.kt`](android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/web/LanShareWebScript.kt) — 实现浏览器中的发送和预览操作。
- [`android/core/lan-share/PROTOCOL.md`](android/core/lan-share/PROTOCOL.md) — 说明传输规则和安全边界。

</details>

<details>
<summary><strong>:architecture-tests</strong> — 检查模块依赖规则</summary>

- [`android/architecture-tests/src/test/kotlin/com/luckyalanzhou/barcodegenerator/architecture/ArchitectureBoundaryTest.kt`](android/architecture-tests/src/test/kotlin/com/luckyalanzhou/barcodegenerator/architecture/ArchitectureBoundaryTest.kt) — 检查代码是否按约定依赖各模块。

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
