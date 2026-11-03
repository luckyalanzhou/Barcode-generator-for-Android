# 忽略更新提醒实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 用户可忽略当前更新版本，使它不再于启动时自动弹窗，同时保留手动检查查看该版本和新版本自动提醒。

**架构：** 用 `SettingsRepository` 暴露被忽略版本的读取与持久化，`SettingsStore` 将单个版本号保存在现有 DataStore。`UpdateViewModel` 仅在启动检查时按版本决定是否自动显示；手动检查路径保持强制显示。更新弹窗增加忽略回调，持久化完成后再关闭。

**技术栈：** Kotlin、Jetpack Compose、Preferences DataStore、JUnit/Robolectric、Gradle。

**规格：** `docs/superpowers/specs/2026-11-02-ignore-update-design.md`

## 全局约束

- 设置存储仍通过 Domain 的 `SettingsRepository` 边界，不让 UI 或 Presentation 直接访问 Android DataStore。
- 只记录一个被忽略的版本号；版本匹配时抑制启动弹窗，版本不匹配时允许启动弹窗。
- 手动点击“检查更新”仍显示已忽略版本的更新结果。
- “稍后更新”只关闭当前弹窗；“立即更新”下载、校验和安装流程不变。
- 保留工作区已有未提交修改；不提交、不推送。

## 文件与职责

- `android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/SettingsRepository.kt`：增加忽略版本的读取和持久化边界。
- `android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/SettingsStore.kt`：增加 DataStore 字段及实现。
- `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/update/UpdateViewModel.kt`：启动检查过滤已忽略版本，提供忽略当前版本的操作。
- `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/dialogs/ComposeUpdateDialogContent.kt`：加入忽略操作按钮及回调。
- `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/platform/ComposeUpdateDialogs.kt`：连接按钮与 ViewModel 忽略操作。
- `android/core/data/src/test/kotlin/com/luckyalanzhou/barcodegenerator/data/SettingsStoreUpdateIgnoredVersionTest.kt`：持久化读写测试。
- `android/app/src/test/kotlin/com/luckyalanzhou/barcodegenerator/presentation/update/UpdateStartupCheckTest.kt`：启动过滤、版本变化和忽略操作测试。
- `android/app/src/test/java/com/luckyalanzhou/barcodegenerator/ui/dialogs/UpdateAvailableDialogContentTest.kt`：按钮回调测试。

## Review Focus

- 已忽略版本与启动返回版本完全匹配：启动检查不自动弹窗；由 Task 2 的启动检查测试覆盖。
- 发布了比已忽略版本更新的版本：启动弹窗恢复；由 Task 2 的版本变化测试覆盖。
- 用户手动检查已忽略的版本：仍打开更新弹窗；由 Task 2 的手动检查行为测试覆盖。
- 忽略记录跨设置存储重新创建保留：由 Task 1 的 DataStore 测试覆盖。
- 忽略按钮之外的“稍后更新”“立即更新”动作不串线：由 Task 3 的 Compose 回调测试覆盖。
- 320dp 宽弹窗与 2.0 系统字号下三项标签完整可见：由 Task 3 的边界/可见性 Compose 测试覆盖。

---

### Task 1: 持久化忽略版本

**文件：**
- 修改：`android/core/domain/src/main/kotlin/com/luckyalanzhou/barcodegenerator/domain/SettingsRepository.kt`
- 修改：`android/core/data/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/SettingsStore.kt`
- 测试：`android/core/data/src/test/kotlin/com/luckyalanzhou/barcodegenerator/data/SettingsStoreUpdateIgnoredVersionTest.kt`

**接口：** `SettingsRepository` 提供 `suspend fun getIgnoredUpdateVersion(): String?` 和 `suspend fun setIgnoredUpdateVersion(version: String)`；`SettingsStore` 通过现有 `barcode_settings` DataStore 实现。写入通过现有串行队列并等待提交。

- [x] 先添加测试：新 store 读回 `null`；写入 `1.2.3` 后同一设置存储可读回该版本；重新创建 store 后仍读回 `1.2.3`。
- [x] 运行测试，确认因接口/实现缺失失败。
- [x] 在 Domain 和 `SettingsStore` 实现精确版本读写，不修改其他设置语义。
- [x] 重跑持久化测试并通过。

### Task 2: 启动过滤与忽略动作

**文件：**
- 修改：`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/update/UpdateViewModel.kt`
- 测试：`android/app/src/test/kotlin/com/luckyalanzhou/barcodegenerator/presentation/update/UpdateStartupCheckTest.kt`

**接口：** ViewModel 注入 `SettingsRepository`；增加 `ignoreAvailableUpdate()`。启动 `Available(version, ...)` 时，版本等于持久化忽略值则保留可用更新信息但不设置 `dialogShowing=true`。忽略操作持久化当前可用版本后关闭弹窗。手动 `checkForUpdates()` 的 Activity 桥接仍会显式显示弹窗。

- [x] 先增加红测：启动时匹配已忽略版本不弹窗；不匹配的新版本自动弹窗；忽略当前版本持久化该值并关闭弹窗；写入失败时保留弹窗并记录失败；手动检查仍能返回已忽略版本。
- [x] 运行 `UpdateStartupCheckTest` 目标测试并确认因行为缺失失败。
- [x] 实现 Repository 注入、启动匹配判断和忽略当前版本操作；保留手动检查行为。
- [x] 重跑启动检查测试，并运行已有 `UpdateCheckStateTest` 回归。

### Task 3: 更新弹窗按钮

**文件：**
- 修改：`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/dialogs/ComposeUpdateDialogContent.kt`
- 修改：`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/platform/ComposeUpdateDialogs.kt`
- 测试：`android/app/src/test/java/com/luckyalanzhou/barcodegenerator/ui/dialogs/UpdateAvailableDialogContentTest.kt`

**接口：** `UpdateAvailableDialogContent` 新增必需的 `onIgnore` 回调；按钮文案为“忽略更新”。Compose 宿主将回调连接到 `updateViewModel.ignoreAvailableUpdate()`。

- [x] 先增加 Compose 测试：点击“忽略更新”只调用 `onIgnore` 一次；“稍后更新”和“立即更新”仍调用各自回调。
- [x] 运行弹窗目标测试并确认忽略动作测试因按钮不存在失败。
- [x] 增加按钮并连接回调；在主要弹窗操作处添加简短中文意图注释。
- [x] 重跑弹窗及更新启动测试。

### Task 4: 完整门禁

**文件：** 无额外生产文件。

- [x] 在 `android` 目录运行 `./gradlew.bat verifyLocal --no-configuration-cache -PenableAppUnitTests=true`。
- [x] 运行 `git diff --check` 并审查本次更新功能差异；保留所有既有未提交改动。





