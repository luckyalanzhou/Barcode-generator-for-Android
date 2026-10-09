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
    'verifyLocal'
    '-PenableAppUnitTests=true'
    '--no-configuration-cache'
)
& $gradleLauncher @gradleTasks
```

它会运行完整架构、各模块和 Beta App 测试，以及两个 Release 渠道的编译和 lint；不会生成或发布 APK。Compose/Robolectric 测试验证真实组件的点击和状态，不等于真机 GPU 或帧耗时验收。只检查某项改动时，可以将对应的 Gradle task 交给同一个启动器，例如：

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

排序和单条删除使用 `UnframedActionPressFeedback.kt`：45ms 内轻压至 84%，短点按的视觉反馈至少 80ms（控件仍存在时），取消立即结束目标状态；点击回调不等待动画。减少动态效果时仅改变透明度，不缩放，不增加底色或描边。

普通按钮统一使用 `ui/component/ButtonOutlineChrome.kt`：保留原尺寸和圆角，浅色微暗、深色微亮的完整描边绘制在边界内，不加高光、阴影、内部填充或 GPU 图层。黑底图片预览按实际背景使用亮边。生成输入行的排序/删除和历史单条删除不描边，只提供按压反馈。普通按钮的点击组件与 `iosPressFeedback` 共用手势源，按下轻压、松开或取消恢复，不重复拦截点击。卡片、菜单选项和 Tab 不增加按钮框；结果页圆按钮保持独立。按钮文字不得继承背景。收藏行的按压只做单次绘制，展开只改变行高，不重复缓存整行。

本地 `:architecture-tests:test` 包含 `ButtonRenderingContractTest`，阻止已知危险组合重新引入；它不是截图测试。修改颜色、透明度、阴影或图层后，还需检查真机：

- 浅色和深色：生成页、设置页按钮在正常、禁用、按下、松开状态均无内部灰块或文字矩形。
- 收藏页：展开、收起、快速滚动、长按和取消后，图标、标题、数量、箭头不重影。
- 按钮保持原点击行为；长按预览轻微聚焦且不裁边，菜单从实际来源位置展开。

没有真机截图/录屏证据时，只报告本地检查通过，不报告视觉验收通过。临时素材检查后删除；不增加远程测试或启用 PR 检查。

菜单分两套动效：长按菜单同步目标聚焦、背景模糊与面板展开；左侧两个 Tab 对齐整个槽位左边缘，右侧两个对齐右边缘，空间不足时避让屏幕。外观、字符纠错及生成页/编辑弹窗的条码格式菜单从按钮锚点整体展开，外框、文字、分割线共享变换，不模糊页面。新增同类入口须启用 `cornerReveal` 并传入按钮实际高度；格式选项使用紧凑勾号行，不填充已选项。

二者共用 `ui/component/menu/MenuSurface.kt` 和 `MenuMaterialSpec.kt`。Android 13 及以上在可用时用 GPU 采样真实背景，浅深色材质保持文字对比度；文字在采样层之后绘制，菜单不做动态折射。高对比度、GPU 不可用或旧系统使用实色回退，减少动态效果时简化过渡。Popup 与页面使用屏幕坐标对齐，不读取 CPU 位图。普通内容卡片不使用菜单材质。

本地 `MenuRenderingContractTest` 检查材质边界与整体动画结构；`MenuBackdropCoordinatesTest` 检查采样坐标；`MenuMaterialSpecTest` 检查主题和高对比度参数。`tools/validate_glass_backdrop.py` 用固定本地 Python/Skia 验证实际 shader，属于合成像素测试，不能证明 Compose 跨窗口重放在目标手机上的表现。苹果未公开统一的缩放比例和时长，最终观感、首帧与收起过程须以真机录屏验收。

Tab 长按拖动与弹出使用独立缩放中心：弹出沿来源锚点展开，拖动时左侧两个菜单向自身左下角、右侧两个向自身右下角收缩。左右或向下拖动有阻尼反馈，向上进入菜单仍用于选项选择；松手未选中操作时回弹并保持菜单打开。减少动态效果时禁用拖动缩放。

外观和字符纠错的选项行使用紧凑布局：正常字号每项 40dp，文字 14sp、行高 20sp；只在大字体或换行确实需要空间时增高。不要换回 Material 默认行高，避免统一组件时把菜单整体撑高。

## 圆形玻璃工具按钮验收基准

四个结果页工具按钮只保留固定外观：主题底色、细边缘、倒角高光和柔和阴影。不录制结果页背景，不创建圆按钮 RuntimeShader，不做实时扩散或折射。常规模式使用 88% 不透明度的主题底色，仅有直接透明合成，不模拟背景模糊；增强对比度使用实色。

按住时仅图标轻微变淡，松手或取消后恢复；禁用/忙碌时不叠加反馈，高对比度下减弱淡化。尺寸、位置和点击功能不变，Tab 与菜单仍使用各自的玻璃路径。

`ResultActionRim.kt` 单独绘制完整细轮廓和内侧方向高光：浅色微暗轮廓、深色微亮轮廓，高光两侧和底部不降为零，描边向内收进防裁边。不使用外围光晕，也不影响 Tab 的边缘样式。

本地契约测试防止重新引入圆按钮背景采样和光学动画；真机验收检查浅深色边缘、图标反馈与四个操作功能。Skia 脚本只验证仍在使用的 Tab 与菜单 shader，不作为按钮外观的验收依据。

## 导出调试日志

Beta 的设置页可导出日志，正式版不启用。复现问题后尽快导出，并说明操作步骤和发生时间。

- 记录关键操作、异常堆栈、设置读写、收藏备份、OCR、更新、文件传输及图片保存/分享结果；导出附带版本、设备与显示环境。
- `glass_render` 记录材质路径、回退原因和采样尺寸；`gpu_selected` 仅说明路径条件满足，不能证明真机像素正确，仍需截图或录屏。
- 按自然日保留最近 7 天（含边界日），每日上限 2MiB，单次导出上限 8MiB；优先保留最新的完整记录，不切断中文或堆栈。`omittedRecords` 明示轮转或导出裁减，兜底文件也纳入导出。
- 打包在后台进行，不逐帧记录；新增诊断不记录条码内容、消息正文或文件 URI。日志不是系统全量 logcat，无法保证捕获系统杀进程、原生崩溃或所有未埋点故障。
- 普通记录通过有界单线程队列落盘；崩溃同步写入，导出先等待队列。过载使用 `queue_overflow omittedRecords`，等待超时使用 `export_flush_timeout` 明示缺口，不能当作完整记录。结果图片准备和编码回退须记录原因。

## 缓存与构建时间

GitHub Actions 用 `gradle/actions/setup-gradle@v6` 缓存 Gradle 依赖和可复用任务输出。缓存不代表所有任务都会跳过：输入变化或不可缓存的任务仍需重新运行。本机缓存和 `build` 输出不会传到远程 runner。

Beta 工作流每次发布都会传入新的 `versionCode`，构建脚本在配置阶段读取它，因此远程 APK 构建使用 `--no-configuration-cache`。这不会关闭依赖或构建缓存。构建耗时取决于任务、缓存命中、runner 启动和发布步骤。
