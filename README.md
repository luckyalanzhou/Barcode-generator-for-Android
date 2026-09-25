# 条码生成器 Android

一款使用 Kotlin 和 Jetpack Compose 开发的 Android 条码工具，支持批量生成、识别、历史与收藏管理，以及手机和浏览器之间的局域网文件传输。

主界面包含生成、历史记录、收藏和设置四个主要页面。

## Beta 版本

- Beta 分支：`beta`
- `versionName`：`9.9.9`；Beta 工作流每次发布递增 `versionCode`
- 应用 ID：`com.luckyalanzhou.barcodegenerator.test`（与正式版分开安装）
- 最低系统版本：Android 8.0（API 26）
- 最新 Beta Release：[下载页面](https://github.com/luckyalanzhou/Barcode-generator-for-Android/releases/tag/android-test-v9.9.9)
- APK 文件名：`BarcodeGeneratorBeta9.9.9.apk`

Beta 是测试渠道版本，不建议作为唯一的生产环境应用使用。每次 Beta 工作流成功发布时，会更新上述 Release 的 APK，并使用递增的 `versionCode`。

## 功能

- **生成条码**：支持 Code 128-B、QR Code、Code 39、EAN-13、EAN-8、UPC-A、ITF-14 和 Codabar；可按行批量输入，并校验格式及数字条码校验位。
- **调整外观**：设置条码宽度、高度、间距、文字大小、文字位置、是否显示内容与格式，以及配色方案。
- **相机与图片识别**：识别条码内容；使用中文 OCR 提取图片文字，可配置常见字符混淆纠正。
- **历史记录**：查看、重新生成、复制、分享和删除历史条码。
- **收藏管理**：将条码保存到收藏，按文件夹整理、搜索、移动、重命名或删除；支持收藏数据导入和导出。
- **局域网传输**：在同一局域网内通过浏览器与手机互传文字、图片及文件，并可使用二维码或手动访问码加入会话。
- **应用设置**：支持浅色/深色外观、恢复条码默认设置、应用内更新检查；Beta 版本另提供诊断日志导出。

## 架构

项目由三个 Gradle 模块组成，依赖方向如下：

```text
:app ───────────────> :core:domain
  └────────────────> :core:data ─────────> :core:domain
```

| 模块 | 职责 |
| --- | --- |
| `:app` | Compose 界面、ViewModel、页面状态和流程协调、Hilt 依赖装配，以及相机、文件选择器等 Android 系统桥接 |
| `:core:domain` | 领域模型、条码校验、用例、Repository/平台接口；不依赖 Android、Compose 或 Room |
| `:core:data` | Repository 实现、Room、DataStore、文件与网络适配，以及 ZXing、ML Kit 和 APK 更新相关的平台实现 |

主要调用链为 `Compose UI → ViewModel/Coordinator → Domain 用例与接口 → Data 实现`。Composable 不直接访问 DAO、文件系统或网络。

页面切换由 `AppNavigationViewModel` 持有的路由状态驱动，Activity 负责保存和恢复必要的页面状态；当前没有使用 `NavController` 或 Navigation Compose 导航栈。结果页通过稳定 ID 和 `SavedStateHandle` 恢复数据，而不是把整份数据或位图放入状态保存对象。

### 数据与持久化

- Room 保存历史条码、收藏分组及条目关联，并包含数据库版本迁移。
- DataStore 保存条码样式和应用偏好；旧版 `SharedPreferences` 数据通过迁移器兼容导入。
- 收藏备份由用户主动导入或导出。Android 系统备份规则会排除局域网临时附件和调试日志。
- 条码生成和收藏数据默认由应用本地数据层管理；局域网传输服务只在用户启动分享会话时运行。

### 局域网分享安全提示

每次分享会话都会生成随机访问令牌和四位手动访问码，停止并重新启动服务后旧凭据失效；访问码也有错误尝试限制。**局域网传输使用 HTTP，访问令牌和访问码不是加密机制。**请仅在可信网络中分享，并只把二维码或链接提供给可信设备；传输结束后关闭分享服务。

## 开发与构建

### 环境要求

- JDK 17
- Android SDK 37（`compileSdk`）；最低支持 API 26
- 使用仓库提供的 Gradle Wrapper，无需单独安装 Gradle

### 本地构建 Beta

在 Windows PowerShell 中从仓库根目录执行：

```powershell
Set-Location .\android
.\gradlew.bat assembleBetaRelease
```

APK 输出目录：`android/app/build/outputs/apk/beta/release/`。本地版本号来自 `android/beta-version.properties`；CI 会按 Beta Release 元数据分配递增的 `versionCode` 并签名发布。正式构建由 `main` 分支上的手动工作流执行，需提供正式版 `versionName` 和 `versionCode`。

### 单元测试与 lint

以下命令与 Beta 发布工作流执行的验证任务一致：

```powershell
Set-Location .\android
.\gradlew.bat :core:domain:test :core:data:testDebugUnitTest :app:testBetaDebugUnitTest :app:lintBetaRelease -PenableAppUnitTests=true --no-configuration-cache --max-workers=2
```

数据库迁移、收藏关联和局域网服务的仪器化测试位于 `android/core/data/src/androidTest/`，需要 Android 模拟器或连接的测试设备；Beta 发布工作流当前不运行这组仪器化测试。

## 仓库目录

```text
.
├─ .github/workflows/       # Beta 与正式版的手动构建、测试和发布工作流
└─ android/
   ├─ app/                  # Android 应用、Compose UI、ViewModel、协调器和 Hilt 装配
   ├─ core/
   │  ├─ domain/            # 纯 Kotlin 领域模型、规则、用例和接口
   │  └─ data/              # Room、DataStore、网络、文件与 Android 平台适配
   ├─ ARCHITECTURE.md       # 架构边界、状态、导航、持久化及安全约束
   └─ beta-version.properties
```

架构细则见 [`android/ARCHITECTURE.md`](android/ARCHITECTURE.md)。
