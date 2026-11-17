# 全局应用语言计划：局域网文件传输网页

**Goal:** 使 Android 应用创建的局域网文件传输网页及其动态错误/状态文案跟随应用当前语言，覆盖简体中文、繁体中文、英语、日语、韩语和西班牙语。

**Architecture:** `core:lan-share` 不依赖 Android `R`。在 lan-share 模块的公开边界定义轻量 locale-provider 接口，由 app 平台边界实现并通过 `AppModule` 注入 `LanShareManager`。Manager 在每次页面/API 请求时读取当前 locale，将稳定 locale tag 传给 `LanShareServer`；浏览器静态模板、脚本文案和浏览器可见服务端反馈由 lan-share 模块自己的强类型翻译目录解析。语言切换不重启服务，已打开网页需刷新后加载新语言。

**Tech Stack:** Kotlin、现有 `core:domain` / `core:lan-share` Android library、NanoHTTPD/NanoWSD、现有 HTML/CSS/JavaScript 模板、AppCompat locale 状态读取。无新增依赖。

**Spec:** `docs/superpowers/specs/2026-11-16-global-app-language-design.md`

**Global Constraints:** 只本地化浏览器面向用户的标签、状态和反馈；不翻译用户聊天内容、文件名、文件内容、协议字段、日志或机器可读响应结构。保持 HTTP/WebSocket 路径、状态码、上传/下载流程、认证、容量限制及服务生命周期不变。不得覆盖、清理或格式化本任务以外的既有工作；不提交、不推送。

**Review Focus:** locale-provider 的默认/未知语言回退、请求期间 locale 一致性、HTML/JavaScript 字符串安全转义、所有动态分支是否都取翻译目录、API/WebSocket 兼容性，以及在更改语言后持续运行的分享会话是否无需重启。

## Task 1: 定义跨层 locale 契约并注入当前语言

**Files:**

- `android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/web/LanShareWebLocaleProvider.kt` (new)
- `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/di/AppModule.kt`
- `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/platform/AppLanShareWebLocaleProvider.kt` (new)
- `android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/server/LanShareManager.kt`
- `android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/server/LanShareServer.kt`
- relevant `core:lan-share` manager/server tests

- [ ] 在 lan-share 模块边界定义不依赖 Android 的 provider 契约，仅返回受支持的 locale tag；不向模块泄漏 app 的 `Context`、`R` 或 AppCompat 类型。
- [ ] 在 app 平台边界实现 provider：优先取 AppCompat 显式 app locale；“跟随系统”时读应用当前资源配置 locale；归一到六种支持语言，未知标签回退 `zh-Hans`。
- [ ] 通过 Hilt `AppModule` 注入单例 provider 到 `LanShareManager`，并由 Manager 在创建 Server 时传入一个每次请求都能读取当前值的提供者。
- [ ] 为现有直接创建 `LanShareServer` 的测试保留简体中文默认值，避免无关改动大量构造点；不要在 locale 变化时关闭或重建 LanShareServer。
- [ ] 定义读取时机：一次页面/API 请求内固定同一个 locale snapshot，避免同一响应中的 HTML 与注入字典跨语言。

**Done when:** provider 返回值只可能是六种支持 tag；测试能证明跟随系统、显式选择和未知 locale 的处理；语言变化时 server/session 实例不变。

## Task 2: 建立网页翻译目录并本地化静态模板及脚本

**Files:**

- `android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/web/LanShareWebTranslations.kt` (new)
- `android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/web/LanShareWebTemplates.kt`
- `android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/web/LanShareWebHead.kt`
- `android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/web/LanShareWebBody.kt`
- `android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/web/LanShareWebScript.kt`
- `android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/server/LanShareServer.kt`

- [ ] 先枚举 HTML、JavaScript 和服务端响应中的所有用户可见中文，按六种 locale 建立类型明确的翻译项；未知 tag 明确回退简体中文。
- [ ] 将网页标题、配对表单、连接状态、附件菜单、发送/上传控件、图片预览与键盘提示、空/加载/上传状态、文件操作提示和所有面向浏览器的错误纳入翻译目录。
- [ ] `LanShareWebTemplates.page(locale, strings)` 对文档设置正确 `<html lang>`，并将浏览器需要的文案字典以 JSON 数据安全注入；JavaScript 动态状态不得继续残留中文字面量。
- [ ] 页面模板与 JS 通过文案键取值；保留聊天文本、文件名、用户提交内容原样展示并继续按现有 HTML/JS 安全边界转义。
- [ ] 对服务端发给浏览器的 HTTP/WebSocket 错误使用稳定错误类型或键映射到请求时的 locale；保留 status code、JSON 字段、WebSocket 事件结构及客户端可预期语义。
- [ ] 修复提交语言切换后新加载/刷新的浏览器页面使用新语言；不增加“必须重启分享服务”的步骤，不要求已打开页面实时改语言。

**Done when:** 六种语言的初始 HTML 与全部脚本动态状态/错误都完整；`rg` 不再在网页用户文案路径命中遗漏中文；API/WebSocket 结构和文件传输行为未变化。

## Task 3: 兼容性、回归测试及运行验收

**Files:**

- `android/core/lan-share/src/test/java/com/luckyalanzhou/barcodegenerator/LanShareWebScriptTest.kt`
- `android/core/lan-share/src/test/**` 对应服务器/会话测试
- `android/app/src/test/**` provider 映射测试

- [ ] 为每个支持 locale 渲染页面并检查 `<html lang>`、静态按钮/标签、动态 JavaScript 字典和关键错误文案。
- [ ] 对未知 locale 验证简体中文回退；对注入 JSON 中含引号、反斜杠、`<` 等字符的值验证序列化安全，不允许破坏脚本或形成 HTML 注入。
- [ ] 保留并运行现有网页脚本测试；补充请求 locale 切换前后，连续请求返回不同语言但服务/认证 session 不变的回归测试。
- [ ] 验证 HTTP 错误状态码、上传失败提示、配对状态与 WebSocket 错误消息仍兼容原客户端；用户文件名和聊天内容不被翻译或损坏。
- [ ] 与原生计划的完整门禁和设备验收合并执行，避免重复启动模拟器；在浏览器真实检查六种语言的窄屏布局与长西语文案。
- [ ] 结束后清理本次专用 AVD/临时进程并确认 LAN share 测试服务退出；不触碰共享 ADB、Codex 或其他任务进程。

**Done when:** 网页渲染/服务端回归和完整项目门禁通过；六种语言的浏览器界面及切换刷新行为有可复现证据；未提交或推送。
