# 项目结构与主要组成

[项目介绍](README.md) · 当前：项目结构 · [架构原则](android/ARCHITECTURE.md) · [贡献指南](CONTRIBUTING.md)

本文按仓库中的实际目录说明项目由哪些部分组成，以及修改不同功能时通常从哪里查起。模块边界和依赖规则见[架构原则](android/ARCHITECTURE.md)。

## 仓库顶层

```text
.
├── README.md                  项目介绍、安装和基本使用
├── LICENSE                    MIT 许可证
├── PROJECT_STRUCTURE.md       本目录导览
├── CONTRIBUTING.md            查找代码和修改步骤
├── .github/workflows/         自动检查、测试版和正式版工作流
└── android/                   Android 应用和 Gradle 工程
    ├── app/                   应用界面、页面状态和应用装配
    ├── core/
    │   ├── domain/            业务模型、规则和接口
    │   ├── data/              本地数据和平台能力的实现
    │   └── lan-share/         局域网传输及浏览器页面
    ├── architecture-tests/   架构边界和代码约定测试
    ├── tools/                 正式版版本分配工具及测试
    ├── gradle/                Gradle Wrapper 和依赖版本目录
    ├── ARCHITECTURE.md        架构原则与模块边界
    ├── PERFORMANCE_BASELINE.md 本地渲染与内存基线记录
    ├── settings.gradle.kts    注册 Gradle 模块
    └── build.gradle.kts       工程级 Gradle 配置与架构验证
```

## Android 工程的模块

`android/settings.gradle.kts` 当前包含五个 Gradle 模块。目录中的子文件夹不一定是独立模块；例如生成页和收藏页都属于 `:app`。

| Gradle 模块 | 主要组成和职责 |
| --- | --- |
| `:app` | Android 应用入口、依赖装配、Compose 界面、页面状态和系统交互；包含 Beta 与正式版配置。 |
| `:core:domain` | 条码及收藏等业务模型、规则、用例和接口；不负责界面、数据库或 Android 系统操作。 |
| `:core:data` | 业务接口的数据实现，包括 Room 数据库、设置、迁移、本地文件、收藏备份，以及 OCR、扫码和更新等平台适配。 |
| `:core:lan-share` | 局域网 HTTP/WebSocket 服务与客户端、文件传输、协议、浏览器页面和预览处理。 |
| `:architecture-tests` | 检查模块依赖方向和重要代码约定；只用于验证，不打进应用。 |

## `:app` 内部怎么分

应用源码位于 `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/`：

- `MainActivity.kt`、`BarcodeGeneratorApplication.kt`：Android 应用与 Activity 入口。
- `di/`：把界面、业务接口和数据实现连接起来。
- `presentation/`：ViewModel、页面状态和操作协调，例如生成、历史、收藏、设置、导航和局域网页面的状态流程。
- `ui/app/`：应用外壳、页面切换、主 Tab 和应用级菜单。
- `ui/feature/`：各功能页面的 Compose 界面，包括生成、结果、历史、收藏、设置、编辑和局域网传输。
- `ui/component/`、`ui/dialogs/`、`ui/theme/`、`ui/animation/`：可复用界面组件、弹窗、颜色尺寸主题和动画。
- `infrastructure/`、`icons/`：应用侧的技术适配和图标资源；例如条码图片渲染适配。

`src/beta/` 和 `src/official/` 存放两个发布渠道各自的配置或实现；共享功能主要在 `src/main/`。两种渠道使用不同应用 ID，可分别安装。`src/test/`、`src/androidTest/` 放置应用测试。

## 其他重要目录

- `android/core/domain/src/`：领域规则及模型；对应单元测试在 `android/core/domain/src/test/`。
- `android/core/data/src/`：本地存储和 Android 平台适配；相关测试在模块自己的 `src/test/`。
- `android/core/lan-share/src/`：传输协议、服务端、客户端、网页资源及预览处理；通信细节见 [`PROTOCOL.md`](android/core/lan-share/PROTOCOL.md)。
- `android/gradle/libs.versions.toml`：集中管理 Gradle 插件和第三方库版本。
- `android/PERFORMANCE_BASELINE.md`：记录可复跑的条码渲染时间与结果位图内存基线；真机滚动帧率仍需连接设备测量。
- `android/tools/`：正式版版本分配器及对应测试。
- [`.github/workflows/`](.github/workflows/)：PR 架构与单测检查，以及手动启动的 Beta 构建、正式版发布。

构建缓存、`build/` 输出和本机工具目录不应作为项目文件提交。

## 修改时从哪里开始

| 要调整的内容 | 通常先看 |
| --- | --- |
| 页面布局、颜色、按钮或动画 | `android/app/src/main/java/.../ui/` |
| 页面状态、用户操作或导航 | `android/app/src/main/java/.../presentation/` |
| 条码、收藏等业务规则 | `android/core/domain/` |
| 数据库、设置或收藏文件 | `android/core/data/` |
| 浏览器与手机之间的传输 | `android/core/lan-share/` |
| 模块依赖和代码约束测试 | `android/architecture-tests/` |

一般从界面操作追到 `presentation`，再根据操作选择 `domain` 规则及 `data` 或 `lan-share` 实现。修改前后的查找和验证流程见[贡献指南](CONTRIBUTING.md)，完整依赖方向见[架构原则](android/ARCHITECTURE.md)。
