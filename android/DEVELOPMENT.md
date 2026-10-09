# 开发与验证

[项目入口](../README.md) · [贡献指南](../CONTRIBUTING.md) · [架构原则](ARCHITECTURE.md) · 当前：开发与验证 · [通信协议](core/lan-share/PROTOCOL.md)

> 本文说明如何准备环境、运行本地检查、验证设备功能和启动 GitHub Actions。

## 环境

项目使用 JDK 17、Android SDK Platform 37 和仓库自带的 Gradle Wrapper。版本以仓库配置为准；以下路径仅适用于当前 Windows 电脑。

- **仓库**：`D:\GitHub\Barcode-generator-for-Android`；Android 项目在其 `android` 子目录。
- **JDK 17**：`D:\Java17`。
- **Gradle 9.5.0、Wrapper 和依赖缓存**：`D:\Barcode_build\gradle-home`。
- **Android SDK Platform 37**：`D:\Barcode_build\android-sdk`；ADB 位于 `platform-tools\adb.exe`。
- **Android 用户目录和本机调试签名**：`D:\Barcode_build\android-user-home`。
- **临时文件 / Kotlin daemon 标记**：`D:\Barcode_build\android-temp` / `D:\Barcode_build\kotlin-daemon`。
- **固定启动器**：`D:\Barcode_build\run-barcode-android-gradle.ps1`。
- **模块输出**：`android\<module>\build`。

本机 Gradle 并行度在 `D:\Barcode_build\gradle-home\gradle.properties` 设置，当前为 `org.gradle.workers.max=4`。不要把本机路径或机器专属参数写进仓库。

所有本机验证都通过固定启动器运行。它会检查工具目录是否存在、可写，并把 Gradle、SDK、JDK、临时文件和 Kotlin daemon 指向上述目录；缺少工具时会停止，不改用 `C:\.gradle`、`C:\.android`、AppData 中的其他配置，也不下载缺失的 Wrapper。

本机和 GitHub Actions 使用独立的 Gradle 缓存、SDK、临时目录和构建输出。本机启动器不参与远程构建；不要把 `D:\Barcode_build` 或 `D:\Java17` 写入 `gradle.properties` 或 workflow。

## 本地验证

在 PowerShell 从仓库根目录运行完整 Beta 检查：

```powershell
$buildDir = 'D:\Barcode_build'
$gradleLauncher = Join-Path $buildDir 'run-barcode-android-gradle.ps1'
$gradleTasks = @(
    ':architecture-tests:test'
    'verifyArchitectureModuleDependencies'
    ':core:domain:test'
    ':core:data:testDebugUnitTest'
    ':core:lan-share:testDebugUnitTest'
    ':app:testBetaDebugUnitTest'
    ':app:lintBetaRelease'
    '-PenableAppUnitTests=true'
    '--no-configuration-cache'
)
& $gradleLauncher @gradleTasks
```

它会运行架构、各模块和 Beta App 测试，并检查 Beta Release 的 lint；不会生成或发布 APK。只检查某项改动时，可以将对应的 Gradle task 交给同一个启动器，例如：

```powershell
$buildDir = 'D:\Barcode_build'
$gradleLauncher = Join-Path $buildDir 'run-barcode-android-gradle.ps1'
$gradleTasks = @(
    ':app:compileBetaDebugKotlin'
    '-PenableAppUnitTests=true'
    '--no-configuration-cache'
)
& $gradleLauncher @gradleTasks
```

`BUILD SUCCESSFUL` 表示命令中的任务通过；编译通过不代表 UI、设备兼容性或真机传输已经验收。

### 真机检查

涉及系统 UI、GPU、触感、Wi-Fi 或生命周期时，应在手机上确认。建议至少检查：

- 浅色/深色、大字体、TalkBack、系统减少动态效果和高对比度。
- Tab 点击、滑动、长按菜单、选项命中和关闭；页面滚动不能被 Tab 栏遮住。
- 生成、编辑、收藏、历史、分享和保存流程；取消或失败后可以重试。
- 局域网连接、文件上传/下载字节完整性、取消传输、图片预览和离开页面后的服务关闭。
- Android 13+ GPU 效果及不支持 GPU 时的兼容显示。

若验证玻璃 shader，可选运行原生 Skia 检查脚本。它检查像素算法，不代替 Android 真机验收；缺少固定环境时不要换用其他 Python 或安装位置：

```powershell
$runtimeRoot = 'C:\Users\zhimi\.cache\codex-runtimes'
$pythonDir = Join-Path $runtimeRoot 'codex-primary-runtime\dependencies\python'
$pythonExe = Join-Path $pythonDir 'python.exe'
$validationArgs = @(
    'android/tools/validate_glass_backdrop.py'
    '--runtime-package-dir'
    'D:\Barcode_build\glass-validation\python-packages'
)
& $pythonExe @validationArgs
```

该环境使用 `skia-python==138.0`，安装在 `D:\Barcode_build\glass-validation\python-packages`。

### 构建文件被占用时

先查明哪个进程占用了文件。只有确认占用来自无关且空闲的进程后，才结束它并重跑失败的任务。若是本项目空闲的 Gradle daemon，可运行：

```powershell
& 'D:\Barcode_build\run-barcode-android-gradle.ps1' --stop
```

不要手动删除锁文件、构建目录或用途不明的进程。

## GitHub Actions

先完成本地验证，再按需要手动启动远程工作流。Beta 与正式版使用不同应用 ID。

- **Beta APK**：[工作流文件](../.github/workflows/build-android-testing.yml)。从 `beta` 分支手动运行，签名、打包并发布 APK；不运行测试或 lint。
- **正式版 APK**：[工作流文件](../.github/workflows/build-android-official.yml)。用户确认发布后，从 `main` 手动运行并填写版本信息；不运行测试或 lint。
- **PR 架构检查**：[工作流文件](../.github/workflows/architecture-checks.yml)。面向 `beta` 或 `main` 的 PR，默认跳过；仓库变量 `ENABLE_ARCHITECTURE_PR_CHECKS=true` 后才运行，也不会自动成为合并必需项。

本地测试通过、远程构建成功和真机验收是三件不同的事。Actions 的具体权限、签名和版本来源以 workflow 文件为准；凭据不要写入文档或提交到仓库。

Actions 运行产物会按工作流设置到期清理；Release 附件是单独的下载入口。同一渠道的发布任务按顺序执行，避免同时更新一个 Release。

## 按钮与列表渲染验收

普通按钮使用 `ui/component/ButtonOutlineChrome.kt`：只描边，不添加原生阴影、内部填充或 GPU 图层。卡片、实色按钮和液态玻璃材质分别管理；按钮文字不得继承背景。收藏行的按压只做单次绘制，展开只改变行高，不重复缓存整行。

本地 `:architecture-tests:test` 包含 `ButtonRenderingContractTest`，阻止已知危险组合重新引入；它不是截图测试。修改颜色、透明度、阴影或图层后，还需检查真机：

- 浅色和深色：生成页、设置页按钮在正常、禁用、按下、松开状态均无内部灰块或文字矩形。
- 收藏页：展开、收起、快速滚动、长按和取消后，图标、标题、数量、箭头不重影。
- 按钮保持原点击行为；长按目标保留 1.15 倍聚焦，菜单仍按既定锚点展开。

没有真机截图/录屏证据时，只报告本地检查通过，不报告视觉验收通过。临时素材检查后删除；不增加远程测试或启用 PR 检查。

菜单分两套动效：长按菜单同步目标聚焦、背景模糊与面板展开，1.15 倍为项目参数；外观和字符纠错从按钮锚点连续扩展到菜单边界，文字不缩放，不模糊页面。二者都使用静态菜单材质，减少动态效果时简化过渡。苹果未公开统一的缩放比例和时长；相似度需以真机录屏确认，不能只靠参数断言。

## 缓存与构建时间

GitHub Actions 用 `gradle/actions/setup-gradle@v6` 缓存 Gradle 依赖和可复用任务输出。缓存不代表所有任务都会跳过：输入变化或不可缓存的任务仍需重新运行。本机缓存和 `build` 输出不会传到远程 runner。

Beta 工作流每次发布都会传入新的 `versionCode`，构建脚本在配置阶段读取它，因此远程 APK 构建使用 `--no-configuration-cache`。这不会关闭依赖或构建缓存。构建耗时取决于任务、缓存命中、runner 启动和发布步骤。
