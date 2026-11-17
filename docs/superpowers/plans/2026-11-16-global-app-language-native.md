# 全局应用语言计划：原生 Android 界面

**Goal:** 在设置页加入全局语言选择，并让 Android 原生应用界面完整支持跟随系统、简体中文、繁体中文、英语、日语、韩语和西班牙语。

**Architecture:** AndroidX AppCompat 应用语言 API 管理应用 locale；Android 字符串资源承担原生 UI 翻译；Compose 通过 `stringResource` 取文案，Activity/平台边界通过当前 locale 的 `Context` 取文案。Domain/Data 不引用 Android `R`，展示错误由稳定类型或错误码交给 UI 转成资源文案。语言状态由 AppCompat locale 状态提供，不复制到主题偏好或用户数据中。

**Tech Stack:** Kotlin、Jetpack Compose、AppCompat 1.6.1、Android string resources、AndroidX `LocaleListCompat` / `AppCompatDelegate`。保留 `minSdk 26`、现有 AGP 与 Compose 版本，不增加依赖。

**Spec:** `docs/superpowers/specs/2026-11-16-global-app-language-design.md`

**Global Constraints:** 保留主题行为、页面结构、业务操作和用户数据；默认跟随系统，未支持的系统语言使用简体中文资源；支持标签为 `zh-Hans`、`zh-Hant`、`en`、`ja`、`ko`、`es`。不翻译用户输入、条码制式名称、协议字段、日志、第三方许可文本或系统界面。不得覆盖、清理或格式化本任务以外的既有工作；不提交、不推送。

**Review Focus:** 检查所有用户可见字符串是否已迁移、字符串格式占位符和复数是否匹配、locale 配置是否只公开六种受支持语言、切换 locale 是否导致输入/导航/收藏/后台服务状态丢失，以及长译文在浅色和深色主题中的可读性与溢出。

## Task 1: 建立原生文案清单与稳定展示错误

**Files:** `android/app/src/main/java/**`、`android/core/domain/**`、`android/core/data/**`、`android/app/src/test/**`。

- [ ] 用界面文字和字符串字面量检索所有运行时代码中的应用自有文案，区分用户可见内容、日志/协议/测试文字和用户数据；不要把测试夹具误当作运行界面。
- [ ] 按生成、结果、历史、收藏、设置、关于、文件传输、相机/OCR、更新、菜单/弹窗、权限说明、无障碍描述及通知反馈归类，记录每处文案的调用层。
- [ ] 对 Domain/Data 返回给用户的中文错误文本建立稳定错误类型/错误码，并让 Presentation/UI 在当前 locale 下映射到资源；保留日志文本、日志级别和业务错误语义。
- [ ] 确认所有动态字符串的参数、数量词和格式规则，列出需使用 `plurals` 或 locale-aware 日期/数字格式的文案。

**Done when:** 清单可覆盖全部运行时用户可见原生文案；所有跨层展示错误均有不依赖 `R` 的稳定映射键，日志和协议值未被误迁移。

## Task 2: 加入 locale 资源与 Android 平台配置

**Files:**

- `android/app/src/main/res/values/strings.xml`
- `android/app/src/main/res/values-b+zh+Hant/strings.xml`
- `android/app/src/main/res/values-en/strings.xml`
- `android/app/src/main/res/values-ja/strings.xml`
- `android/app/src/main/res/values-ko/strings.xml`
- `android/app/src/main/res/values-es/strings.xml`
- `android/app/src/main/res/xml/locales_config.xml`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/beta/res/**/strings.xml`
- `android/app/src/official/res/**/strings.xml`

- [ ] 以 `values/strings.xml` 作为简体中文默认资源，新增六种完整 locale 目录；所有 locale 提供相同资源名、参数数量与格式类型。
- [ ] 通过 `<locale-config>` 和 manifest `android:localeConfig` 公开六种应用语言；使用当前 AGP 能力前先核实 DSL 与 flavor 资源合并结果，不升级 AGP。若自动生成结果不精确，使用显式 XML。
- [ ] 按 AppCompat 1.6.1 的要求配置旧系统 locale 自动存储 metadata；API 33+ 使用平台应用语言设置集成。manifest 合并时检查 debug、beta、official 三类变体均保留配置。
- [ ] 将各 flavor 的应用名称（release、beta、debug）补齐六种翻译，保持各渠道既有品牌区分；默认名称与既有简体中文名称一致。
- [ ] 添加不依赖 Android UI 的语言映射辅助逻辑：七个设置选项映射为 AppCompat locale list；“跟随系统”映射为空 locale list；系统不支持时使用默认简体中文资源。

**Done when:** 资源编译成功；六种语言在系统应用语言设置中的清单准确；API 26–32 能持久化用户选择；每个渠道仍显示正确应用名；没有增加或升级依赖。

## Task 3: 接通设置页语言选择与 locale 重建

**Files:**

- `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/settings/SettingsContent.kt`
- `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/settings/SettingsContentState.kt`
- `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/settings/ComposeSettingsComponents.kt`
- `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/ComposePageRoute.kt`
- `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/ComposeAppShellActions.kt`
- `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/MainActivity.kt`
- `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/platform/AppLanguageController.kt` (new platform-boundary helper)

- [ ] 在设置页将分组标题从“外观”改为“通用”，将主题行改为“应用主题”，保持现有主题选择器和保存行为不变。
- [ ] 在应用主题行下增加“语言”行；复用当前设置页的右侧锚定单选菜单和触控/无障碍模式，提供七项，其中语言名称采用简体中文、繁體中文、English、日本語、한국어、Español 等本地名称，“跟随系统”随当前 UI locale 翻译。
- [ ] 将当前 AppCompat locale 状态提供给设置页；选择语言后调用平台语言 API，选择“跟随系统”时清空 app-specific locale，不另建一份可能不同步的持久化设置。
- [ ] 明确处理 Activity 重建：确认 Activity ViewModel、导航返回位置、生成编辑草稿、收藏/历史数据和已运行 LanShareManager 会话不因 locale 更新而被清除、重复启动或重启。
- [ ] 所有新的无障碍标签、当前选中状态及系统语言列表名称均从资源读取；不添加只有视觉文本、无语义的选择器。

**Done when:** 当前设置值与 AppCompat/系统设置一致；七种语言都能即时切换并在重启后恢复；现有主题选择不回归；切换前后的当前任务和分享服务保持可用。

## Task 4: 将原生用户界面迁移到资源并校对七种语言

**Files:** 根据 Task 1 清单覆盖 `android/app/src/main/java/**`（仅处理含应用自有用户可见文案的运行代码，包含必要的 Presentation 和平台回调）及对应 `android/app/src/main/res/values*/strings.xml`。

- [ ] 将 Compose 文案迁为 `stringResource`；非 Compose 平台消息通过当前配置的 Context 读取资源。保持 UI/Presentation/Domain/Data 分层，不从 Domain/Data 直接访问 Android 资源。
- [ ] 覆盖页面标题、按钮、导航标签、菜单、上下文菜单、弹窗、空状态、加载状态、提示、表单校验、导出/导入/更新/相机/OCR反馈、权限说明和 `contentDescription` / `stateDescription`。
- [ ] 将动态文案改为参数化资源；计数类使用 `plurals`，日期、数字、文件大小按 locale 格式化。用户创建的条码内容、文件名、收藏标题和聊天内容仍原样显示。
- [ ] 补全全部六种翻译，并检查同一术语在页面、菜单、错误和提示中的一致性；校对占位符次序、HTML/格式标记、复数、按钮长度以及日/韩/繁中字形回退。
- [ ] 在切换入口对用户开放前完成全量资源迁移，禁止出现“设置页已变语言但其他页面大量残留中文”的中间交付状态。

**Done when:** 运行时检索不再发现未翻译的应用自有硬编码文案（明确列入非目标的内容除外）；各 locale 资源键集合和参数签名一致；中英日韩繁体显示没有乱码或占位符错误。

## Task 5: 自动与设备验收

**Files:** 资源/映射测试、已有 `android/app/src/test/**`；必要时在最小测试范围补回归用例，不移动或覆盖已有本地证据。

- [ ] 覆盖语言选项到 BCP-47 标签、跟随系统空 locale、未知系统 locale 回退和设置当前值读取的映射测试。
- [ ] 检查资源一致性：六种语言资源键齐全，参数类型/数量一致，manifest locale 配置只含支持语言；检查已本地化的 flavor app name。
- [ ] 按项目完整门禁执行 `./gradlew.bat verifyLocal --no-configuration-cache -PenableAppUnitTests=true`，失败则定位并修复本次变更引入的问题后重跑。
- [ ] 使用可用的 API 33+ Audit 模拟器检查系统应用语言设置同步；用 API 26–32 环境检查 AppCompat 持久化。若本机缺少相应系统镜像，记录具体缺失和未验证项，不以 API 35 单独宣称旧系统已验证。
- [ ] 七种语言逐一检查设置、生成、结果、历史、收藏、关于及主要菜单/弹窗；至少对浅色、深色各走一轮实际界面与交互，重点检查长西语、日/韩字形和窄屏溢出。
- [ ] 重启应用复查语言恢复；切换过程中保留生成输入、导航位置和收藏；LanShare 会话不得重启或中断。按项目规则关闭本次启动的 AVD/临时进程并检查退出。

**Done when:** 门禁和可用 API 档位验收结果可复现，所有缺失环境明确记录；浅色/深色交互和七种文案的关键流程均通过；未提交或推送。
