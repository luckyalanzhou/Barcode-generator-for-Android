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
| 本地启动器 | `D:\Barcode_build\run-barcode-android-gradle.ps1` |
| 模块构建输出 | 各模块的 `android\<module>\build` 目录 |

本机专用 Gradle worker 并行度保存在 `D:\Barcode_build\gradle-home\gradle.properties`，当前为 `org.gradle.workers.max=4`。不要把本机资源参数写入仓库 `gradle.properties`。

本机验证统一通过启动器运行。它只为本次 Gradle 进程设置 `GRADLE_USER_HOME`、`ANDROID_HOME`、`ANDROID_SDK_ROOT` 和 `JAVA_HOME`；启动前检查固定目录及 Gradle 9.5.0 是否存在。若检查失败会立即退出，不回退到 `C:\.gradle`、其他 SDK/JDK，也不会尝试下载缺失的 Wrapper。启动器位于仓库外，因此不会进入提交或影响远程工作流。

## 本地 Beta 验证

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
