# 条码生成器 Beta

Android 条码生成器的 Beta 测试分支。此分支用于验证新功能、现代化 Android 架构迁移和交互动画；测试确认稳定后，才会合并到 `main` 正式版。

> 当前 Beta 版本：`1.0.57`  
> 当前分支：`beta`  
> 测试包名：`com.luckyalanzhou.barcodegenerator.test`  
> 稳定性说明：测试版，不建议作为唯一的生产环境应用使用。

## 当前 Beta 状态

- 已完成 Compose 主界面迁移，使用四个底部 Tab：生成、历史、收藏、设置。
- 底部 Tab 使用液态玻璃选中框，支持跟随手指滑动、触碰放大和弹簧回弹动画。
- 选中框当前高度为 `56dp`；图标和文字在动画结束后恢复原始尺寸。
- 增加 Beta 测试中心、调试日志导出和 Beta 专用诊断能力。
- Beta 使用独立 applicationId，可与正式版并行安装，不覆盖正式版数据和应用入口。
- Beta 版本号由 GitHub Actions 构建前自动递增，并由构建流程提交回 `beta` 分支。

## 功能

- 生成 Code 128-B、QR Code、Code 39、EAN-13、EAN-8、UPC-A、ITF-14 和 Codabar。
- 支持一次输入多行内容并批量生成条码。
- 支持相机/图片识别文字和条码内容。
- 查看生成历史，重新生成、复制、分享和删除条码。
- 收藏条码，使用文件夹整理、重命名、移动和删除收藏内容。
- 收藏数据备份与恢复。
- 通过局域网在手机和浏览器之间分享条码和文件。
- 支持浅色/深色主题、条码结果页亮屏和应用内更新检查。

## 下载 Beta

最新 Beta Release：

<https://github.com/luckyalanzhou/Barcode-generator-for-Android/releases/tag/android-test-v1.0.57>

APK 文件名：`BarcodeGeneratorTest1.0.57.apk`

Beta Release 与正式版使用不同的包名，可以同时安装：

```text
正式版：com.luckyalanzhou.barcodegenerator
Beta：  com.luckyalanzhou.barcodegenerator.test
```

## 本地构建

项目要求 JDK 17、Android SDK 35 和 Gradle Wrapper。Windows PowerShell 示例：

```powershell
cd android
$env:JAVA_HOME = "D:\Java17"
$env:GRADLE_USER_HOME = "D:\Barcode_build\gradle-home"
$env:ANDROID_USER_HOME = "D:\Barcode_build\android-home"
$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
.\gradlew.bat :app:assembleBetaDebug :app:testBetaDebugUnitTest --no-daemon --no-configuration-cache
```

本地调试安装如需与其他本地包完全隔离，可增加 `-PlocalDebugPackageSuffix=true`，此时包名为：

```text
com.luckyalanzhou.barcodegenerator.test.debug
```

## Beta 构建流程

Beta 构建由 `.github/workflows/build-android-testing.yml` 手动触发，流程会：

1. 自动递增 `android/beta-version.properties` 中的版本号和 versionCode。
2. 使用 GitHub Actions 作者信息提交版本递增变更。
3. 使用 Release 签名构建 Beta APK。
4. 上传构建 Artifact，并发布 `android-test-vx.y.z` Release。

提交 Beta 功能后，只触发 Beta 工作流；未经测试确认，不推送或构建 `main` 正式版。

## 项目结构

```text
android/
├─ app/             # Android 应用、Compose 页面和应用层业务
├─ core/domain/     # 与 Android 无关的领域模型和用例
├─ core/data/       # Room、文件和数据仓储
└─ core/ui/         # Compose 动画和共享 UI 配置
```

Beta 通过 `official` 和 `beta` product flavor 区分正式包与测试包；正式版只从 `main` 分支构建。

## 分支约定

- `beta`：新功能开发、架构迁移、动画调整和测试构建。
- `main`：经过 Beta 验证后发布的正式版本。
- Beta 验证失败时，只修复 `beta`，不直接修改正式版发布流程。
