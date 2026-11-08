# 条码生成器（Android）

一个以本地使用为主的 Android 条码工具：录入文本并生成条码图片，管理历史和收藏，也可以通过局域网与浏览器交换文本和文件。

## 产品目标

- 快速把一条或多条文本转换为可预览、保存和分享的条码图片。
- 让常用条码内容可检索、可整理、可备份，减少重复录入。
- 提供相机文字识别和局域网传输等辅助能力，同时让主要数据保存在设备本地。

产品范围和验收条件见 [PRD.md](PRD.md)。

## 核心功能

- **生成条码**：支持 Code 128-B、QR Code、Code 39、EAN-13、EAN-8、UPC-A、ITF-14 和 Codabar；可维护多行输入、调整顺序、批量生成并编辑结果。
- **结果导出**：预览生成结果，保存 PNG 图片或通过 Android 分享面板分享；可从结果创建收藏。
- **历史记录**：按生成批次查看历史条码，并支持清空历史。
- **收藏管理**：使用两级文件夹整理收藏，搜索名称、路径和条码内容；支持编辑、移动、导入和导出收藏备份。
- **辅助录入**：通过相机拍摄图片进行文字识别，也可从图片来源识别；可设置 OCR 字符纠错选项。
- **局域网分享**：手机作为局域网服务端，通过二维码让浏览器接入；支持文字消息和文件/图片传输、预览、下载及取消传输。
- **个性化与更新**：支持跟随系统、浅色和深色外观，调整条码高度、宽度、间距和文字大小，并检查应用更新。

## 使用方式

1. 在“条码生成器”页面选择条码类型并输入内容。批量输入可按行填写。
2. 点击生成后，在结果页预览条码；需要修改时返回编辑，需要长期保存时添加到收藏。
3. 在“历史记录”查看以往生成内容，在“收藏”按文件夹整理并搜索。
4. 在设置中启动“局域网文件分享”，让同一局域网中的浏览器扫描页面二维码连接。

OCR 结果可能需要核对；EAN/UPC 等格式只接受符合对应格式规则的内容。局域网分享需要设备处于可互通的网络环境。

## 技术架构

项目位于 `android/`，采用 Kotlin、Android Gradle Plugin 和 Jetpack Compose，最低支持 Android 8.0（API 26）。生产代码按以下模块组织：

| 模块 | 职责 |
| --- | --- |
| `:app` | Android 应用入口、Compose 界面、Presentation 协调、平台适配和依赖注入 |
| `:core:domain` | 条码生成/校验、业务模型、用例及仓储和平台接口；不依赖 Android UI |
| `:core:data` | Room 本地数据、DataStore 设置、文件与 Android 平台接口实现 |
| `:core:lan-share` | 局域网 HTTP/WebSocket 服务、浏览器端页面和传输协议 |
| `:architecture-tests` | 模块边界、架构约定和工作流契约检查 |

主要依赖包括 ZXing（条码编解码）、ML Kit Text Recognition（文字识别）、Room、DataStore、Hilt、Kotlin Coroutines 和 NanoHTTPD。UI 负责呈现，Presentation 编排状态与操作，Domain 定义业务，Data 实现存储和平台接口。

## 开发与构建

需要 JDK 17 和 Android SDK。Windows PowerShell 下：

```powershell
cd android
./gradlew.bat tasks
```

本地完整验证门禁（包含架构检查、核心与应用单测、官方版/测试版编译及 lint）：

```powershell
./gradlew.bat verifyLocal --no-configuration-cache -PenableAppUnitTests=true
```

发布变体为 `official` 和 `beta`。日常开发及默认测试发布使用 `beta`；正式版从 `main` 发布。签名发布构建需要通过环境变量提供签名配置；密钥不应写入仓库。推送前必须通过上述完整门禁。

## 项目文档

- [应用设计语言](docs/DESIGN_LANGUAGE.md)：组件命名、菜单分类、视觉层级、主题颜色、玻璃材质、动效与交互约定。
- [产品需求文档（PRD）](PRD.md)：目标、用户场景、范围、架构约束和验收标准。
