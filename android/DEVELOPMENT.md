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

发布后由用户手动更新 Beta。浅色/深色下检查四个 Tab：点击和横向滑动时，选中胶囊应平滑跟随当前项，材质对比度适中、无明显塑料描边或大块高光；Android 13+ 的胶囊经过其他图标/文字时有轻微局部折射，但不能整组放大、重影或妨碍辨认，松手后恢复清晰。光影仅在胶囊边缘，不应有中心亮斑或胶囊外的蓝色光晕。中途抓住正在移动的胶囊、快速反向拖动、连续点击不同 Tab 时，不跳位、不被旧动画拉回。滑动经过其他 Tab 中心时页面立即切换。系统关闭动画时不应保留动态折射。长按历史、收藏、设置 Tab 时，不论该 Tab 当前是否选中，都应显示对应的 Tab 图标和标题；浮起 Tab 无描边或选中胶囊，操作菜单应与其同步上移并保持锚定在上方，背景模糊但不整体压暗。主 Tab 点击与滑动仍使用短距离进入动画，快速连续切换时不应叠影；三个条码结果页仍保持无切页动画。另检查重复点击收藏折叠、系统导航栏、长列表末尾，以及深色模式下生成、历史、收藏、设置和对话框卡片边缘是否柔和且可辨。编译与 lint 不替代真机视觉和帧率验证。

点击历史 Tab 应重新整理最新共享历史快照、刷新日期显示，并将列表滚动位置重置到顶部，无论点击前位于哪个页面。从历史结果页返回时应恢复离开历史页时的滚动位置；滑动经过历史 Tab 不触发点击刷新或滚动重置。

### 液态玻璃的本地自动验证

`TabGlassMotionStateTest` 用 Compose 帧时钟验证拖动到回弹的连续交接、动画重定向以及不同刷新率下的收敛；`TabGlassFrameTest` 检查胶囊不越界和静止零折射；`TabGlassMaterialTest` 检查材质对比度与尺寸适配边界。这些测试包含在下方 Beta App 单元测试命令中。

可选脚本 `tools/validate_tab_glass.py` 使用原生 Skia 编译实际 Kotlin 文件中的 shader，并检查静止像素、真实网格位移、胶囊外像素隔离、透明度与前景清晰度。本机使用以下固定环境，不加入 Android 依赖或远程工作流：

- Python：`C:\Users\zhimi\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe`。
- 原生 Skia：`skia-python==138.0`，仅安装在 `D:\Barcode_build\glass-validation\python-packages`；numpy 使用该 Python 已有的版本。

在仓库根目录执行（不是 `android` 子目录）：

```powershell
& 'C:\Users\zhimi\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' android/tools/validate_tab_glass.py --runtime-package-dir D:\Barcode_build\glass-validation\python-packages
```

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
