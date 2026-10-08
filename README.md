<div align="center">

# 条码生成器 · Android

<p>在 Android 上生成、识别和管理条码，也可与同一局域网内的浏览器互传文件。</p>

<p>
  <a href="https://github.com/luckyalanzhou/Barcode-generator-for-Android/releases/tag/android-test-v9.9.9"><strong>下载最新 Beta APK</strong></a>
  &nbsp;·&nbsp;
  <a href="android/ARCHITECTURE.md">架构：为什么这样设计</a>
  &nbsp;·&nbsp;
  <a href="android/DEVELOPMENT.md">开发：如何运行和验证</a>
  &nbsp;·&nbsp;
  <a href="android/core/lan-share/PROTOCOL.md">协议：设备如何通信</a>
  &nbsp;·&nbsp;
  <a href="CONTRIBUTING.md">贡献：如何修改项目</a>
</p>

<p>
  <a href="https://github.com/luckyalanzhou/Barcode-generator-for-Android/actions/workflows/build-android-testing.yml?query=branch%3Abeta"><img alt="Beta APK workflow" src="https://img.shields.io/github/actions/workflow/status/luckyalanzhou/Barcode-generator-for-Android/build-android-testing.yml?branch=beta&label=Beta%20build&logo=github"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white">
</p>

</div>

## 项目简介

项目使用 Kotlin、Jetpack Compose 和多模块结构。主要功能包括条码生成与识别、历史与收藏管理，以及手机和浏览器间的局域网文件传输。Beta 版与正式版可并行安装。

## 从这里开始

| 想了解或完成什么 | 阅读 |
| --- | --- |
| 项目有哪些模块，以及各模块为什么这样划分 | [架构原则](android/ARCHITECTURE.md) |
| 如何搜索代码、规划修改并检查改动 | [贡献与修改指南](CONTRIBUTING.md) |
| 如何设置本机环境、运行验证和启动构建 | [开发与验证](android/DEVELOPMENT.md) |
| 浏览器与 App 如何连接、交换消息和传输文件 | [LAN Share 协议](android/core/lan-share/PROTOCOL.md) |

## 项目模块

```text
:app                  页面、应用流程与 Android 系统接入
:core:domain          业务规则、数据定义和接口
:core:data            本机数据保存与平台能力实现
:core:lan-share       局域网服务、浏览器页面与文件传输
:architecture-tests  检查模块依赖约束
```

源码位置和关键文件见 [贡献指南中的代码地图](CONTRIBUTING.md#代码地图)。

## 发布入口

- **Beta**：`beta` 分支，手动运行 [Beta APK 工作流](.github/workflows/build-android-testing.yml)，从[最新 Beta Release](https://github.com/luckyalanzhou/Barcode-generator-for-Android/releases/tag/android-test-v9.9.9)下载。
- **正式版**：用户确认发布后，从 `main` 分支手动运行[正式版工作流](.github/workflows/build-android-official.yml)。

本地验证要求和工作流步骤见[开发与验证](android/DEVELOPMENT.md)。
