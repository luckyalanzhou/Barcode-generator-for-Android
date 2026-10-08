<div align="center">

# 条码生成器 · Android

<p>在 Android 上生成、识别和管理条码，也可与同一局域网内的浏览器互传文件。</p>

<p>
  <a href="https://github.com/luckyalanzhou/Barcode-generator-for-Android/releases/tag/android-v1.0.0"><strong>下载正式版 APK</strong></a>
  &nbsp;·&nbsp;
  <a href="android/ARCHITECTURE.md">架构</a>
  &nbsp;·&nbsp;
  <a href="android/DEVELOPMENT.md">开发与验证</a>
  &nbsp;·&nbsp;
  <a href="android/core/lan-share/PROTOCOL.md">通信协议</a>
  &nbsp;·&nbsp;
  <a href="CONTRIBUTING.md">贡献指南</a>
</p>

<p>
  <a href="https://github.com/luckyalanzhou/Barcode-generator-for-Android/actions/workflows/build-android-official.yml?query=branch%3Amain"><img alt="正式版构建工作流" src="https://img.shields.io/github/actions/workflow/status/luckyalanzhou/Barcode-generator-for-Android/build-android-official.yml?branch=main&label=Official%20build&logo=github"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white">
</p>

</div>

## 项目简介

项目使用 Kotlin、Jetpack Compose 和多模块结构，支持条码生成与识别、历史和收藏管理，以及手机与浏览器间的局域网文件传输。

## 功能

- 生成 Code 128-B、QR Code、Code 39、EAN-13、EAN-8、UPC-A、ITF-14 和 Codabar。
- 支持多行内容批量生成，以及通过相机或图片识别文字和条码。
- 管理历史记录和收藏文件夹，支持收藏备份与恢复。
- 通过局域网在手机和浏览器之间传输文字与文件。
- 支持浅色/深色主题、结果页亮屏和应用内更新检查。

## 文档入口

- **README**：项目入口和模块概览。
- **[ARCHITECTURE](android/ARCHITECTURE.md)**：说明模块边界、依赖方向和架构约束。
- **[DEVELOPMENT](android/DEVELOPMENT.md)**：说明本地环境、验证命令和构建流程。
- **[PROTOCOL](android/core/lan-share/PROTOCOL.md)**：说明 App 与浏览器的局域网通信。
- **[CONTRIBUTING](CONTRIBUTING.md)**：说明如何安全地修改和提交代码。

## 模块概览

- `:app`：界面、应用流程和 Android 系统接入。
- `:core:domain`：业务规则、数据定义和接口。
- `:core:data`：本机数据保存与平台能力实现。
- `:core:lan-share`：局域网服务、浏览器页面和文件传输。
- `:architecture-tests`：检查模块依赖约束。

## 正式版

- 当前正式 Release：[`v1.0.0`](https://github.com/luckyalanzhou/Barcode-generator-for-Android/releases/tag/android-v1.0.0)
- APK：`BarcodeGenerator1.0.0.apk`
- 包名：`com.luckyalanzhou.barcodegenerator`
- 最低系统：Android 8.0（API 26）

正式版从 `main` 分支手动运行[正式版工作流](.github/workflows/build-android-official.yml)，并填写版本号和 `versionCode`。本地验证要求与工作流步骤见[开发与验证](android/DEVELOPMENT.md)。
