<div align="center">

# 条码生成器 · Android

在 Android 上批量生成、识别和管理条码，并与同一 Wi-Fi 下的浏览器互传文件。

[下载最新 Beta APK](https://github.com/luckyalanzhou/Barcode-generator-for-Android/releases/tag/android-test-v9.9.9) · [使用说明](#使用) · [开发文档](android/DEVELOPMENT.md)

[![Beta build](https://img.shields.io/github/actions/workflow/status/luckyalanzhou/Barcode-generator-for-Android/build-android-testing.yml?branch=beta&label=Beta%20build&logo=github)](https://github.com/luckyalanzhou/Barcode-generator-for-Android/actions/workflows/build-android-testing.yml?query=branch%3Abeta)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin Compose](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)

</div>

## 项目简介

条码生成器是一款面向日常批量制码和本地管理的 Android 应用。它使用 Kotlin 与 Jetpack Compose 开发，数据保存在设备本地；Beta 版与正式版使用不同应用 ID，可同时安装。

## 核心功能

- 支持 Code 128-B、QR Code、Code 39、EAN-13、EAN-8、UPC-A、ITF-14 和 Codabar。
- 多行输入并行生成，可编辑、排序、收藏、保存或分享结果图片。
- 通过相机 OCR 识别文字并回填生成页。
- 按历史记录、一级/二级文件夹和收藏文件管理条码。
- 导入和导出收藏备份，便于迁移本地数据。
- 在同一 Wi-Fi 下启动临时 HTTP/WebSocket 服务，用浏览器收发文字和原始文件。

## 安装

1. 打开 [Beta Release](https://github.com/luckyalanzhou/Barcode-generator-for-Android/releases/tag/android-test-v9.9.9)。
2. 下载 `BarcodeGeneratorBeta9.9.9.apk`。
3. 在 Android 8.0 或更高版本上允许当前文件来源安装 APK，然后完成安装。

Beta Release 说明包含版本号、提交 SHA 和 APK SHA-256；正式版提供 APK 与 `.sha256` 校验文件，发布流程会验证文件，但 Release 说明不保证列出提交 SHA 和摘要。正式版发布后可从同一仓库的 [Releases](https://github.com/luckyalanzhou/Barcode-generator-for-Android/releases) 获取。

## 使用

### 生成和保存条码

1. 在“生成”页输入一行或多行内容，也可点击“拍照填充”。
2. 选择条码格式并点击生成。
3. 在结果页编辑、收藏、分享或保存图片；记录会进入“历史”页。

### 管理收藏

1. 在结果页点击收藏，选择一级或二级文件夹并输入文件名。
2. 在“收藏”页搜索、展开文件夹或长按文件与文件夹进行管理。
3. 长按“收藏”Tab 可导入或导出收藏备份。

### 局域网文件传输

1. 进入“设置”，启动“局域网文件分享”。
2. 让另一台设备连接同一 Wi-Fi，并扫描二维码或在浏览器打开 App 显示的 `http://IP:端口`。
3. 在网页与 App 之间发送文字或上传、下载文件；离开文件传输页会关闭服务并清理本次会话文件。

局域网分享不提供登录、身份认证或端到端加密；同网内能打开地址的设备均可访问。完整边界见 [LAN Share 协议](android/core/lan-share/PROTOCOL.md#安全模型)。

## 从源码验证

准备 JDK 17 和 Android SDK Platform 37，然后在仓库的 `android` 目录运行：

```bash
./gradlew verifyLocal -PenableAppUnitTests=true --no-configuration-cache
```

Windows PowerShell 使用 `./gradlew.bat`。签名 APK、本机固定目录和 GitHub Actions 的操作方式见[开发与验证](android/DEVELOPMENT.md)。

## 版本与维护

| 项目 | 状态 |
| --- | --- |
| 维护状态 | 持续维护；构建状态见页面顶部徽章 |
| Android 兼容性 | Android 8.0+（minSdk 26），targetSdk 37 |
| 发布渠道 | Beta 与正式版独立安装，均由 GitHub Actions 手动发布 |
| 更新方式 | App 内检查更新或查看 GitHub Releases |

普通代码推送不会自动发布 APK。Beta 和正式版的构建、签名与版本规则以[开发与验证](android/DEVELOPMENT.md#github-actions)为准。

## 文档

- [ARCHITECTURE.md](android/ARCHITECTURE.md)：架构原则、模块边界和依赖方向。
- [DEVELOPMENT.md](android/DEVELOPMENT.md)：环境、验证、构建和发布方式。
- [PROTOCOL.md](android/core/lan-share/PROTOCOL.md)：局域网通信协议与安全模型。
- [CONTRIBUTING.md](CONTRIBUTING.md)：查找代码、修改和提交要求。

## 许可证

本仓库未提供开源许可证；如需复用或分发代码，请先联系仓库所有者确认授权。
