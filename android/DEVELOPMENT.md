# 开发与验证

本文记录本仓库当前可执行的本地验证方式。Android 依赖版本与模块边界见 [架构原则](ARCHITECTURE.md)；LAN Share 接口细节见 [协议说明](core/lan-share/PROTOCOL.md)。

## 环境

- JDK 17、Android SDK Platform 37 和仓库 Gradle Wrapper；具体版本随仓库配置。
- Windows 命令示例使用 PowerShell。通用机器可以配置自己的 SDK 与 Gradle User Home；本机固定环境见下节。

## 本机固定验证环境（Windows）

以下路径固定用于当前本机，不是远程构建要求，也不应复制到 GitHub Actions 环境变量：

| 用途 | 固定位置 |
| --- | --- |
| 仓库检出 | `D:\GitHub\Barcode-generator-for-Android` |
| Android 项目根目录 | `D:\GitHub\Barcode-generator-for-Android\android` |
| JDK 17 | `D:\Java17`（`bin\java.exe`、`bin\javac.exe`） |
| Gradle User Home、依赖缓存及 Wrapper 分发 | `D:\Barcode_build\gradle-home`（使用 Gradle 9.5.0） |
| Android SDK | `D:\Barcode_build\android-sdk`（`platforms\android-37.0`、`platform-tools\adb.exe`） |
| Android 用户偏好与本机调试签名文件 | `D:\Barcode_build\android-user-home` |
| Java/Gradle 临时文件（`TEMP`、`TMP`、`java.io.tmpdir`） | `D:\Barcode_build\android-temp` |
| Kotlin 编译守护进程运行标记 | `D:\Barcode_build\kotlin-daemon` |
| 本地启动器 | `D:\Barcode_build\run-barcode-android-gradle.ps1` |
| 模块构建输出 | 各模块的 `android\<module>\build` 目录 |

本机专用 Gradle worker 并行度保存在 `D:\Barcode_build\gradle-home\gradle.properties`，当前为 `org.gradle.workers.max=4`。不要把本机资源参数写入仓库 `gradle.properties`。

本机验证统一通过启动器运行。启动器为本次 Gradle/JVM 进程固定设置 `GRADLE_USER_HOME`、`ANDROID_HOME`、`ANDROID_SDK_ROOT`、`ANDROID_USER_HOME`、`JAVA_HOME`、`TEMP`、`TMP`、`java.io.tmpdir` 和 Kotlin daemon 的 `runFilesPath`。这些路径全部指向表格列出的 `D:` 目录；启动前会检查所需工具与缓存目录可用、可写，并在缺少本机专用临时目录时创建它们。任一固定工具路径缺失时立即退出，不回退到 `C:\.gradle`、`C:\.android`、用户 `AppData` 或其他 SDK/JDK，也不会尝试下载缺失的 Wrapper。启动器位于仓库外，因此这些本机绝对路径不会进入 Git 或影响远程工作流。

### 本机目录与 GitHub Actions 目录的边界

| 内容 | 本机验证 | 远程 Beta/正式版构建 |
| --- | --- | --- |
| Gradle 缓存、Wrapper、依赖 | `D:\Barcode_build\gradle-home` | GitHub Actions runner 自己的 Gradle Home/缓存 |
| JDK 与 Android SDK | `D:\Java17`、`D:\Barcode_build\android-sdk` | GitHub Actions 工作流在 Ubuntu runner 上配置的 JDK/SDK |
| Android 偏好、调试签名、临时目录、Kotlin daemon 标记 | `D:\Barcode_build\android-user-home`、`android-temp`、`kotlin-daemon` | runner 自己的用户目录和临时目录 |
| 模块编译输出 | 当前检出中的 `android\<module>\build` | runner 检出目录里的对应 `android/<module>/build` |
| 配置入口 | 仓库外的 `D:\Barcode_build\run-barcode-android-gradle.ps1` 及本机 Gradle Home 属性 | `.github/workflows/` 与仓库共享的 Gradle 配置；不读取本机启动器 |

不要把 `D:\Barcode_build`、`D:\Java17` 或任何本机绝对路径写进仓库的 `android/gradle.properties` 或 GitHub Actions 工作流。仓库的 `android/gradle.properties` 是本机和远程共用的构建行为配置；固定机器路径只放在仓库外的本机启动器中。

## 本地 Beta 验证

### Tab 栏与长按菜单视觉回归

Tab 栏不绘制整体外框，也不绘制整条实色背景；下方页面内容可透过 Tab 空白区域显示。页面内容单独记录到 Compose `GraphicsLayer`，Tab 和菜单自身不进入背景源；材质按窗口坐标重放对应区域，不截图、不逐帧转 Bitmap 或读回 GPU。Android 13+ 且硬件加速可用时，选中胶囊通过 `RuntimeShader` / `RenderEffect` 对局部真实像素做透镜折射；点击、按压和滑动时折射及边缘光随接触位置变化，静止后折射归零，图标与文字恢复原始几何形态。Android 8–12、软件渲染或 shader 初始化失败时使用兼容的胶囊光影，不提供像素折射。普通内容卡片保持实色，不接入玻璃材质。主 Tab 列表保留足够的末尾滚动留白，确保最后一项可滚过底部 Tab 栏并完整显示。

背景材质与 Tab 前景使用独立图层，避免对页面和图标重复折射或重复着色。背景使用 Android GPU 高斯模糊与 AGSL 局部透镜组合；shader 内的少量邻域采样按背景亮度与细节增加有限的对比保护，不做全局主题翻转。菜单大小决定基准散射与折射强度；菜单文字、操作图标和浮起 Tab 始终独立清晰绘制，不参与模糊或折射。胶囊接触光仅出现在边缘。普通内容卡片、条码和文件源不受材质处理影响。只有导航控件可见、且未启用不透明降级时才记录背景，不在相机/结果/传输等无主导航页面额外采样。

长按菜单共享打开/关闭进度：背景模糊、Tab 上移、菜单上移与淡入同时进行。菜单在退出结束前保留，操作在关闭后只执行一次；未开始进入就关闭时直接释放模态状态。菜单跟随实际 Tab 锚点，屏幕边缘限制位置，上方空间不足时允许菜单自身滚动，操作文字可换行。窗口大小、页面路由或深浅主题改变时关闭旧菜单。底层页面在菜单存续期间不接收触摸和键盘焦点，也不暴露无障碍节点。返回、外部点击、Escape 与无障碍关闭动作均走同一关闭流程；普通关闭后请求将输入焦点恢复到原 Tab，执行会打开窗口的操作时不抢占新窗口焦点。TalkBack 的实际焦点与播报仍需设备确认。

### 视觉效果与无障碍选项

设置页外观卡片仅显示外观选择与“显示与动效”入口。入口摘要：有应用自定义限制时为“已调整”；没有应用限制、系统限制生效时为“跟随系统”；其余为“默认”。点击打开二级弹窗，标题与“完成”按钮固定，选项区域在内容溢出时滚动；文字可换行，整行提供至少 48dp 的单一开关触摸与无障碍目标。更改即时保存，“完成”、外部点击与返回均只关闭弹窗，不撤销已经保存的设置。

三个独立开关默认关闭，仍沿现有 SettingsViewModel → SettingsRepository → DataStore 路径保存；没有新增存储字段，旧安装保留原先的开关值：

| 选项 | 实际范围 |
| --- | --- |
| 减少动态效果 | 关闭主 Tab 弹性、装饰性形变、动态折射、主页面切换和操作菜单的位移/缩放动画；手指拖动与切页操作仍可用。不是关闭整个应用全部组件的动画。 |
| 使用不透明导航与菜单 | Tab 导航底色与胶囊、操作菜单使用清晰不透明表面，停止背景折射与模糊；不影响普通内容卡片。 |
| 提高导航与菜单对比度 | 使用不透明降级，提高 Tab 文字/图标与选中边界的辨识度，菜单标题改用清晰主文字色；未启用时仍保留原占位文字色。 |

系统关闭动画时，即使应用开关关闭，导航仍减少动态效果；监听系统动画时长变化并在应用恢复前台时刷新状态。Android 16（API 36）及以上使用公开的高对比文字查询/监听接口，系统要求高对比时应用不能用自身开关抵消；更低版本通过应用内“提高导航与菜单对比度”补充，不使用隐藏 API。系统限制对应的开关显示实际生效值、禁用手动切换并说明系统原因；对比度已开启时，不透明开关显示开启并说明效果已包含。上述显示状态不改写用户原值，系统限制或对比度取消后恢复原选择。开启 TalkBack 本身不自动关闭材质或改变导航行为。Tab 提供角色、选中状态和操作菜单的无障碍动作，图标不重复播报；操作行最小 48dp，高字体倍率允许换行并在有限空间内滚动。

兼容策略：Android 13+ 硬件加速与 shader 初始化成功时使用 GPU 背景材质；Android 12 可保留页面模糊并使用高覆盖率菜单表面；更低版本、软件渲染或效果不可用时使用清晰静态表面。无障碍不透明策略始终优先。设备验证应覆盖浅色/深色、字体倍率、系统关闭动画、应用三个开关和 TalkBack，不能仅由编译成功推断无障碍完全可用。

发布后由用户手动更新 Beta。浅色/深色下检查四个 Tab：点击和横向滑动时，选中胶囊应平滑跟随当前项，材质对比度适中、无明显塑料描边或大块高光；Android 13+ 的胶囊经过其他图标/文字时有轻微局部折射，但不能整组放大、重影或妨碍辨认，松手后恢复清晰。光影仅在胶囊边缘，不应有中心亮斑或胶囊外的蓝色光晕。中途抓住正在移动的胶囊、快速反向拖动、连续点击不同 Tab 时，不跳位、不被旧动画拉回。滑动经过其他 Tab 中心时页面立即切换。系统关闭动画时不应保留动态折射。长按历史、收藏、设置 Tab 时，不论该 Tab 当前是否选中，都应显示对应的 Tab 图标和标题；浮起 Tab 无描边或选中胶囊，操作菜单应与其同步上移并保持锚定在上方，背景模糊但不整体压暗。主 Tab 点击与滑动仍使用短距离进入动画，快速连续切换时不应叠影；三个条码结果页仍保持无切页动画。另检查重复点击收藏折叠、系统导航栏、长列表末尾，以及深色模式下生成、历史、收藏、设置和对话框卡片边缘是否柔和且可辨。编译与 lint 不替代真机视觉和帧率验证。

点击历史 Tab 应重新整理最新共享历史快照、刷新日期显示，并将列表滚动位置重置到顶部，无论点击前位于哪个页面。从历史结果页返回时应恢复离开历史页时的滚动位置；滑动经过历史 Tab 不触发点击刷新或滚动重置。

设置页标题固定，卡片列表使用 `LazyListState.canScrollForward || canScrollBackward` 判断是否允许手势滚动；不要仅比较最后一项与整页高度，因为 Tab 栏覆盖在列表底部。列表末尾预留 Tab 栏高度加 24dp 的空间，计入测量后的滚动范围。卡片连同安全留白能完整显示时不可滚动；超出时可滚动，在最底部仍能向上滚回。回归检查应覆盖新增外观开关后的 Beta 页面、大屏内容完全可见、小屏/横屏/大字体内容溢出，以及滚到底部后的“关于 / 检查更新”不被 Tab 栏遮挡。

### 历史与设置的内容卡片

历史批次卡片与设置分组使用独立的 `groupedContentSurface` 实色样式：不绘制阴影；浅色仅保留 0.5dp 的弱描边，深色依靠卡片与页面底色分离，不额外描边。不修改其他页面共用的 `globalCardSurface`，不接入玻璃折射。两页卡片沿页面统一水平边缘对齐，历史圆角 12dp、设置圆角 16dp，保留各自内容密度。

历史预览完整首条内容，由 `Text` 按可用宽度单行省略，不预先截取字符；条数与时间放在下方次级信息行。卡片最小高度 56dp，文字变大时内容撑高，不固定裁切高度。删除图标为 20dp 次级色，按钮区域为 48dp；点击打开、长按编辑、删除确认与返回滚动位置不变。历史列表末尾同样预留 Tab 栏高度加 24dp 的空间。

设置组间距 8dp，内部行仍以 48dp 为最小高度（包括滑块行），分割线左右与卡片内部留白对齐。主操作按钮、滑块数值原有 6dp 右移、独立检查更新按钮和二级显示设置入口均保留。设备回归检查浅色/深色、大字体、窄屏、长内容、删除按钮点击与卡片打开互不误触，以及列表首尾不被导航遮挡；本地单元测试 `HistoryBatchPreviewTest` 检查完整预览、Unicode/多行内容、批次顺序不变与空内容降级，不代替实际布局验证。

### 液态玻璃的本地自动验证

`TabGlassMotionStateTest` 用 Compose 帧时钟验证拖动到回弹的连续交接、动画重定向以及不同刷新率下的收敛；`TabGlassFrameTest` 检查胶囊不越界和静止零折射；`TabGlassMaterialTest` 检查材质对比度与尺寸适配边界。这些测试包含在下方 Beta App 单元测试命令中。

`GlassBackdropMaterialTest` 检查菜单尺寸适配边界，`TabMenuPlacementTest` 检查实际锚点、窗口原点和屏幕边缘定位，`TabMenuPresentationTest` 检查退出保留、早期关闭、重复操作与焦点回调，`VisualEffectsPolicyTest` 检查系统与应用限制的优先级。数据模块 `SettingsStyleCodecTest` 覆盖旧字段默认值、三项开关的所有组合往返、滑块修改保留开关和旧值范围限制。

`DisplayEffectsSettingsStateTest` 覆盖三个应用开关与两个系统限制的 32 种组合、摘要与实际生效值、系统控制项的说明与可编辑状态，以及对比度取消后恢复原透明度选择。设备回归另需检查入口整行点击、弹窗返回/完成关闭、即时保存与重启恢复、浅色/深色、大字体及横屏的滚动边界和 TalkBack 单一开关节点。

可选脚本 `tools/validate_tab_glass.py` 使用原生 Skia 编译实际 Kotlin 文件中的 shader，并检查静止像素、真实网格位移、胶囊外像素隔离、透明度与前景清晰度。本机使用以下固定环境，不加入 Android 依赖或远程工作流：

- Python：`C:\Users\zhimi\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe`。
- 原生 Skia：`skia-python==138.0`，仅安装在 `D:\Barcode_build\glass-validation\python-packages`；numpy 使用该 Python 已有的版本。

在仓库根目录执行（不是 `android` 子目录）：

```powershell
& 'C:\Users\zhimi\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' android/tools/validate_tab_glass.py --runtime-package-dir D:\Barcode_build\glass-validation\python-packages
& 'C:\Users\zhimi\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' android/tools/validate_glass_backdrop.py --runtime-package-dir D:\Barcode_build\glass-validation\python-packages
```

背景脚本编译实际 `GlassBackdropShader.kt`，验证真实背景像素位移、圆角外透明隔离、局部对比保护及不透明结果；它不运行 Android 的 RenderEffect 高斯模糊链、Compose 布局、TalkBack 或设备 GPU 驱动。真机需在密集文字/图片背景、连续拖动和反复开关菜单下确认：静止无重影、菜单前景清晰、边缘无外溢、窗口变化不留旧菜单。帧率按设备实际刷新率的帧时间预算评估（如 60Hz 约 16.7ms，120Hz 约 8.3ms），比较修改前后的掉帧与帧耗时，不能以本地数学动画测试代替设备性能验收。

缺少上述环境时，这项可选像素检查不能运行，不应自动尝试其他 Python 或安装位置。需要合成对比图时追加 `--render D:\Barcode_build\glass-validation\comparison.png`，查看后删除该临时图片。它不是手机截图，也不能证明 Android GPU 驱动兼容性或真机帧率；发布后仍按前节做设备视觉回归。

在 PowerShell 中执行：

```powershell
& 'D:\Barcode_build\run-barcode-android-gradle.ps1' :architecture-tests:test verifyArchitectureModuleDependencies :core:domain:test :core:data:testDebugUnitTest :core:lan-share:testDebugUnitTest :app:testBetaDebugUnitTest :app:lintBetaRelease -PenableAppUnitTests=true --no-configuration-cache
```

该命令运行架构边界、领域、数据、LAN Share 和 Beta App 单元测试，并检查 Beta Release lint。单模块开发时可只运行对应任务。针对 Wi-Fi 连通、系统相机/文件选择器和生命周期的验证须使用 Android 真机；自动化测试不能代替设备验证。

若任务报告 `classes.jar` 等构建输出被占用，先查询并确认实际持有进程。确认锁来自与本项目验证无关、且没有其他正在进行任务的进程后，结束该占用进程，确认文件锁已释放，再只重跑失败的验证任务。若占用者是本项目的空闲 Gradle daemon，使用 `& 'D:\Barcode_build\run-barcode-android-gradle.ps1' --stop` 停止固定 Gradle User Home 下的 daemon 后再验证。不得删除构建目录、锁文件，也不得结束用途或状态尚未确认的进程。

## 架构 PR 检查

`.github/workflows/architecture-checks.yml` 会在面向 `beta` 或 `main` 的 PR 上显示架构检查。默认仓库变量未设置，检查任务会显示为跳过，不启动 Runner，也不影响合并；Beta/正式版 APK 工作流仍只负责打包。

需要启用时，在 GitHub 仓库打开 **Settings → Secrets and variables → Actions → Variables**，新增仓库变量 `ENABLE_ARCHITECTURE_PR_CHECKS`，值设为 `true`。后续 PR 将运行 Konsist 包层检查和 Gradle 模块依赖图检查。要关闭时删除该变量或改为 `false`。变量变更不会自动重跑已有 PR，需要重新触发或手动重跑工作流。

该工作流本身不会配置分支保护，也不会把检查设为合并必需项。若将来需要阻止未通过检查的 PR 合并，应另行在 GitHub 分支保护规则中设置必需状态检查。

本机专属的 Gradle 并行度、内存与缓存设置应放在本机 Gradle User Home 配置中，不提交机器规格相关参数到仓库。出现资源不足时，按本机可用内存和 CPU 调整。

## 远程打包

- Beta：将已验证的提交推送到 `beta`，再手动运行仓库的 **Build Android Beta APK** 工作流。工作流负责签名、打包并发布 Beta APK，不重复运行测试或 lint。
- 正式版：只有经用户确认后才将变更合入 `main`；正式版手动工作流负责签名、打包及发布，同样不代替本地验证。
- Beta 与正式版使用不同应用 ID；本仓库日常开发和测试先在 `beta` 完成。

版本号、签名 secrets、工作流输入以及产物命名以 `.github/workflows/` 内当前配置为准，本文只记录职责边界，不复制可能随发布策略变化的流水线细节。
