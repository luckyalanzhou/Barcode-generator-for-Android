# 条码生成器

一款面向 Android 的本地条码生成工具。`main` 是经过 Beta 验证后发布的正式版分支，适合日常使用；新功能和架构实验先在 `beta` 分支验证，不会直接进入正式版。

> 当前正式 Release：`v1.0.0`<br>
> 当前分支：`main`<br>
> 正式包名：`com.luckyalanzhou.barcodegenerator`<br>
> 最低 Android 版本：API 26<br>
> 编译/目标 SDK：35

## 正式版状态

- 正式版以稳定性和兼容性为优先，只有通过 Beta 测试的变更才会合并到本分支。
- 正式版使用独立的正式包名和正式更新通道，不会接收 `android-test-v*` Beta Release。
- 当前正式版不包含 Beta 分支中尚未验证的 Compose 主界面、液态玻璃 Tab 动画和 Beta 测试中心。
- 正式版没有 Beta 调试日志导出入口，也不会显示 Beta 专用测试页面。

## 功能

- 生成 Code 128-B、QR Code、Code 39、EAN-13、EAN-8、UPC-A、ITF-14 和 Codabar。
- 支持多行内容批量生成条码。
- 支持相机/图片识别文字和条码内容。
- 查看历史记录，重新生成、复制、分享和删除条码。
- 收藏条码并使用文件夹整理、重命名、移动和删除收藏内容。
- 支持收藏数据备份与恢复。
- 支持通过局域网在手机和浏览器之间分享条码和文件。
- 支持浅色/深色主题、结果页亮屏和应用内更新检查。

## 下载正式版

最新正式 Release：

<https://github.com/luckyalanzhou/Barcode-generator-for-Android/releases/tag/android-v1.0.0>

APK 文件名：`BarcodeGenerator1.0.0.apk`

正式版安装包的 applicationId 为：

```text
com.luckyalanzhou.barcodegenerator
```

Beta 测试包使用独立的：

```text
com.luckyalanzhou.barcodegenerator.test
```

因此正式版和 Beta 可以并行安装；日常使用请优先选择正式版，测试新功能请安装 Beta 包。

## 本地构建

项目要求 JDK 17、Android SDK 35 和 Gradle Wrapper。Windows PowerShell 示例：

```powershell
cd android
$env:JAVA_HOME = "D:\Java17"
$env:GRADLE_USER_HOME = "D:\Barcode_build\gradle-home"
$env:ANDROID_USER_HOME = "D:\Barcode_build\android-home"
$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
.\gradlew.bat :app:assembleOfficialDebug :app:testOfficialDebugUnitTest --no-daemon --no-configuration-cache
```

正式版 Release 构建需要 Release 签名环境变量：

```text
KEYSTORE_FILE
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

## 正式版构建流程

正式版由 `.github/workflows/build-android-official.yml` 在 `main` 分支手动触发。触发时必须填写：

- `version_name`：三段式版本号，例如 `1.0.1`。
- `version_code`：递增的正整数，例如 `12`。

工作流会校验版本参数，使用 Release 签名构建 `assembleOfficialRelease`，上传 APK Artifact，并发布 `android-vx.y.z` GitHub Release。正式版构建不会自动修改版本文件。

## 项目结构

```text
android/
└─ app/             # 正式版 Android 应用、界面和业务逻辑
```

正式版的核心约束是：

1. 先在 `beta` 分支完成开发和验证。
2. Beta 构建、安装和功能测试通过后，再合并到 `main`。
3. 只从 `main` 触发正式版 Release 构建。

## 分支说明

- `main`：稳定正式版，仅接收已验证的功能。
- `beta`：测试版，用于新功能、架构迁移和交互动画验证。

问题反馈请附上应用版本、Android 版本、设备型号和复现步骤；Beta 问题请同时注明使用的是 Beta 包。
