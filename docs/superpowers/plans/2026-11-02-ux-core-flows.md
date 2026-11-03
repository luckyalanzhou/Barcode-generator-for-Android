# 核心流程体验优化实施计划

> **执行要求：** 使用 `superpowers:executing-plans` 逐任务执行；每个行为改动先写失败测试再实现。

**目标：** 改善批量输入反馈、整批准备进度和历史编辑可发现性，并提供修改后模拟器实图。

**架构：** 批量粘贴预览留在生成 UI 及其解析辅助器；结果准备进度继续消费现有 presentation 进度，不改变整批显示屏障；历史编辑动作仍由页面回调交给上层。

**技术栈：** Kotlin、Jetpack Compose、Material 3、Robolectric、Android API 35 模拟器。

**规格：** `docs/superpowers/specs/2026-11-02-ux-core-flows-design.md`

## 全局约束

- 保持本地条码输入草稿的当前保存、恢复和顺序语义。
- 保持批量导入的原子追加、CSV 规则和 `MAX_GENERATE_INPUT_ROWS` 上限。
- 保持结果页“整批准备完成后才显示结果”。
- 不回收 renderer/cache 共享 Bitmap。
- 不提交/推送；不覆盖工作区中本次任务无关的已有修改。
- 模拟器实图及日志只写入 `.local-validation/logs/`。

## 审阅关注输入

- 空白或仅 BOM 输入：不报告成错误风暴，草稿不变。
- CSV 格式错误及带逗号字段：指出原文行号，且不得部分追加。
- 接近批量上限：容量冲突可解释、原草稿不变。
- 最后一批次尚未完成：确定进度小于 100%，列表和工具栏仍隐藏。
- 小屏与深浅主题：历史三个操作仍可发现、可触摸、语义标签完整。

---

### Task 1: 批量粘贴前预览

**文件：**
- 修改：`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/generate/BatchBarcodeTextParser.kt`
- 修改：`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/generate/GenerateContent.kt`
- 测试：`android/app/src/test/kotlin/com/luckyalanzhou/barcodegenerator/ui/feature/generate/BatchBarcodeTextParserTest.kt`
- 测试：`android/app/src/test/java/com/luckyalanzhou/barcodegenerator/ui/feature/generate/InputFieldAccessibilityTest.kt`

**接口：** 新增对现有输入草稿和文本返回 `Empty`、`Ready(addedCount,totalCount)`、`Invalid(line?,reason)` 之一的纯预览函数。输入变化时仅展示摘要；只有用户点击添加时才应用现有解析结果。

- [x] 先为有效计数、错误行、容量冲突和草稿不可变写测试并运行至红。
- [x] 添加 Compose 测试验证预览摘要/错误就地显示且确认后才追加。
- [x] 实现纯预览状态和对话框反馈。
- [x] 运行 `BatchBarcodeTextParserTest` 与 `InputFieldAccessibilityTest`。

### Task 2: 历史编辑入口可发现

**文件：**
- 修改：`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/history/ComposeHistoryUi.kt`
- 测试：`android/app/src/test/java/com/luckyalanzhou/barcodegenerator/ui/feature/history/HistoryPageInteractionTest.kt`

**接口：** 在历史卡片中添加具备 `编辑这批条码` 语义标签的 48dp 编辑按钮，复用既有 `onEdit`；卡片长按仍保留。

- [x] 先添加显式编辑按钮交互测试并确认因节点不存在而失败。
- [x] 实现可见编辑按钮并保留查看、长按、删除动作隔离。
- [x] 测试历史交互及摘要测试。

### Task 3: 整批准备的确定进度

**文件：**
- 修改：`android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/results/ResultsContent.kt`
- 测试：`android/app/src/test/kotlin/com/luckyalanzhou/barcodegenerator/ui/feature/results/ResultsContentPreparationTest.kt`

**接口：** 使用现有 `completedRows` 和 `items.size` 显示进度比例，并清楚说明整批完成后才显示结果。

- [x] 扩展延迟第九项的 Compose 测试验证进度语义和整批说明，并确认旧界面下失败。
- [x] 增加确定进度条，保留进行中计数、错误重试和屏障。
- [x] 运行结果准备及工具栏回归测试。

### Task 4: 模拟器验证、完整门禁和实图

**文件：**
- 本地截图：`.local-validation/logs/ux-*.png`
- 本地记录：`.local-validation/logs/ux-validation-summary.txt`

- [x] 跑定向测试；完整 `verifyLocal --no-configuration-cache -PenableAppUnitTests=true` 待执行。
- [x] 构建/安装 beta debug 到 `BarcodeGenerator_Lite_API35`。
- [x] 验证批量预览、整批进度、结果和历史编辑入口。
- [x] 保存生成页批量预览、准备进度、结果、历史页截图；当前留存 light theme 实图。
- [x] 审查本次差异与 `git diff --check`；截图只保存在本地专用目录。
