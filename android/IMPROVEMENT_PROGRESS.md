# 持续改进进度

按模块逐项检查有证据的问题和明确收益的优化。状态只表示已完成的验证，不以代码修改代替验证；未要求时不提交、推送、构建或发布。

## 模块清单

| 模块 | 检查重点 | 状态 | 依赖/说明 |
| --- | --- | --- | --- |
| `:core:data` | 持久化、数据库迁移、收藏快照和文件导入导出 | 检查中 | 依赖 `:core:domain`；本轮补充收藏备份 ID 分配溢出保护，并覆盖历史、收藏、设置与更新持久化；DataStore 实现依赖收回模块内部；APK 更新身份策略和中断下载临时包清理有回归测试；更新 tag 解析支持无位数上限的 SemVer（CI-112）；新版本缺 SHA-256 时在检查阶段即明确拒绝，不再呈现为可下载（CI-115）；旧版条码/设置迁移现传播取消与 JVM 致命错误，普通坏值仍保留既有默认值回退，迁移失败不清除源数据；损坏类型设置的默认值兼容性有集成回归保护（CI-127、CI-128、CI-131）；完整本地验证需按 CI-053 使用短 Java 临时路径（本轮已通过）；数据库/收藏回归中测试专用 SQLite 数据库名导致 Robolectric `SQLITE_CANTOPEN`，已改回类内标准隔离数据库并通过 12 项测试（CI-153）；条码缓存压缩现仅降级普通异常、致命错误传播（CI-154）；ZXing 图片识别现传播取消与程序级错误，普通解码异常仍返回未识别（CI-141）；收藏 JSON 解析仅将 `JSONException` 转为现有格式错误提示（CI-139）；ML Kit OCR 现保留普通失败回退，同时传播取消与 JVM 致命错误（CI-140）；Android PackageManager 对真实 APK 的解析分支仍未做平台集成验证；版本 1/2 数据库迁移回归覆盖缺口及权威旧版夹具待确认（CI-081） |
| `:core:domain` | 条码规则、收藏导入规划、更新与传输领域策略 | 已检查 | 本轮覆盖条码、身份、更新与 LAN 领域规则；版本比较现支持无位数上限的合法十进制段（CI-112） |
| `:core:lan-share` | HTTP/WebSocket、浏览器端、文件完整性与会话清理 | 检查中 | 依赖 `:core:domain`；Android 客户端真实 HTTP 上传取消和服务端部分文件清理由 CI-069 增加 loopback 回归测试；可导出的上传日志不记录用户文件名，异常消息与 URI/路径会在日志路径脱敏；文件与图片预览下载在响应声明 `Content-Length` 时都会校验实际字节数后再替换目标，截断响应保留旧目标（CI-135）；HTTP 错误响应详情在普通请求和上传路径统一限制最多读取 4 KiB + 1 字节，超长内容不再完整缓冲（CI-159）；`/api/files` 与 WebSocket 首次同步目前都完整构造文件快照/JSON，潜在峰值内存需按真实规模测量后再决定是否流式解析或分页（CI-160）；网页图片预览边长和磁盘缓存均有上限，临时文件与 Bitmap 在完成/失败后回收；上传目标使用唯一前缀且拒绝覆盖，未发现正常上传流程导致旧预览缓存复用（CI-161）；WebSocket 每连接出站队列有界（32 条），写入超时 5 秒，溢出时关闭慢连接（CI-162）；NanoHTTPD 请求临时文件公告经实际请求路径核查，当前未触发受影响解析分支（CI-143）；WebSocket 64 KB 聊天限制发生在库层完整帧分配/重组之后，超大帧可能先耗尽进程内存（CI-144，修复需选择受限帧解析实现）；HTTP 上传在读取前做长度、并发、房间容量和磁盘空间预留，暂存后校验再提交（CI-147）；8 位配对码使用安全随机数，按客户端地址限频且限制跟踪地址总数，鉴权失败不泄露缓存内容（CI-148）；下载路由对客户端声明的所有 `image/*` 均返回 `inline`，含潜在活动 SVG 类型（CI-169，修复与 loopback seam 确认待处理） |
| `:app` | 页面状态、导航、生成/结果/历史/收藏/设置与系统交互 | 检查中 | 启动快照读取增加一次安全重试；设置与库数据启动错误现已区分汇总；SAF 文档选择不再要求旧存储权限，旧版遗留持久 URI 授权改为一次性迁移清理；Beta 日志初始化移出首屏调用线程、跨日运行触发过期日志清理、异常摘要保留类型/堆栈但省略异常消息，旧日志导出前脱敏；图片解码、外部 Activity 启动失败和 SAF 缺少 URI 会留下安全诊断及用户反馈；LAN 下载失败或返回时会话失效会尝试删除 SAF 新建的空/部分目标文档；局域网相机返回时若会话已失效则清理临时照片并提示重连；关闭/切换 LAN 会话会立即回收活动上传照片；持久化队列、收藏备份、SAF 旧授权迁移及 Activity 启动现仅将普通 `Exception` 作为可恢复失败，取消与致命 `Error` 不再伪装成普通失败（CI-091、CI-093–CI-097、CI-100–CI-101）；更新安装路径使用 `SavedStateHandle` 保留待安装状态（CI-027，本地协调器测试复跑通过，真实进程回收待设备）；安装权限设置往返失败时会恢复待安装提示（CI-102）；LAN host 启动期间来自 NanoHTTPD 工作线程的连接事件可收敛到 UI（CI-113）；对高风险集合访问未发现未保护的 `first/single`；收藏组的内存次序现与 Room 的 `savedAt DESC, id DESC` 对齐（CI-121）；收藏列表分页读取失败现保留取消传播、记录不含查询内容的诊断并提供单独重试入口（CI-125）；自动备份默认覆盖 Room 条码库和 DataStore 设置，当前仅排除临时 LAN 文件与诊断日志，云备份/换机范围待产品确认（CI-129）；全局明文网络权限因局域网客户端需访问动态路由器 IPv4，尚无不改变连接方式的静态子网白名单，当前客户端与更新器均有协议/主机边界保护，残余平台级范围待确认（CI-130）；SAF 提供器和真实安装器行为仍需设备恢复演练；沙箱内 ADB 本机服务启动受限，授权后的只读设备枚举可运行但没有连接设备，当前未做真机验证；历史记录 500 条可见上限待产品确认 |
| `:architecture-tests` | 模块依赖、渲染/日志等架构约定的覆盖有效性 | 已检查 | 修复变体源码输入漏项及 configuration cache 不兼容 |
| 构建与发布配置 | Beta/正式版工作流、版本分配及签名边界 | 检查中 | Beta 同一 workflow run 在发布后重试会沿用已发布 versionCode（CI-110，本地验证）；正式版 workflow 对并发组启用最多 100 个等待 run 的队列，避免默认单等待位替换旧手动发布（CI-116）；Beta 稳定 Release 明确采用最新等待构建策略（CI-119）；恢复已公开但附件不完整的正式版时会先转草稿，附件校验后才重新公开（CI-114）；PR 门禁已接入 Release 编译与 Lint（CI-117）；Gradle Wrapper 9.5.0 的分发 ZIP 摘要与 Wrapper JAR 均锁定官方值（CI-118）；Beta 工作流权限与跨 job 产物交接已静态核对；提交 `bea226b` 已推送，Beta 远端验证、签名构建及 Release 发布均成功（CI-158）；依赖版本集中管理，但仓库无自动更新 PR、锁文件或制品摘要校验配置，自动更新目标分支待确认（CI-155） |
| 项目文档 | README、目录结构、架构、协议、贡献和性能基线文档 | 已检查，无需修改 | README 安全说明曾与实际 LAN 配对/令牌实现相反，已修正（CI-150）；目录与架构文档和 Gradle 模块核对一致（CI-151）；协议文档已说明 64 KiB 聊天校验不是 WebSocket 帧内存上限（CI-152）；本地 Markdown 文件链接静态核对无断链 |

### 最近跨模块验证（2026-10-22）

- `:core:lan-share` 图片预览采样和 EXIF 方向定向复跑通过：`D:\Barcode_build\run-barcode-android-gradle.ps1 --offline :core:lan-share:testDebugUnitTest --tests '*LanShareImagePreviewDecoderTest' --tests '*LanShareExifOrientationTest' --rerun-tasks`，BUILD SUCCESSFUL，17 项任务执行；不组装 APK。普通沙箱运行无法连接本机 Gradle daemon，使用同一固定脚本在获批的沙箱外重试后通过。
- Beta pending 策略显式化：新增契约在未声明 `queue: single` 时按预期失败；加入配置后定向架构测试通过。完整 `D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline verifyLocal` 成功，191 项任务（4 执行、187 up-to-date），不组装 APK（CI-119）。
- 收藏组内存序与分页游标回归通过（相关 8 项）；完整 `D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline verifyLocal` 成功，191 项任务（4 执行、187 up-to-date），覆盖架构测试、Beta 单测、Beta/Official Release 编译与 Lint，不组装 APK。`git diff --check` 通过；ADB 服务未能启动，因此本轮没有真机验证（CI-121）。
- App 系统权限回调审查未发现可证实的状态消费缺陷；现有测试覆盖请求状态保存、一次性消费及拒绝授权的 fail-closed，真实 ActivityResultRegistry 进程恢复仍待设备验证（CI-122）。发现并修复嵌套打开局域网分享时设置返回路由被覆盖的问题；两个定向回归通过，完整无筛选 App 单测 435 项、架构契约 72 项均通过；`verifyLocal` 成功，191 项任务（14 执行、177 up-to-date），不组装 APK（CI-123）。
- 相邻 Activity 状态恢复路径已检查：配置变更保留 Activity ViewModel；进程重建时从 Bundle 恢复当前路由和设置返回目标；结果页通过 `SavedStateHandle` 保存稳定 ID 与返回路由。未发现可证实缺陷；真实系统进程回收仍需设备场景补验（CI-124）。
- 收藏分页失败路径新增回归：原实现的 Room 分页读取异常会逃出 `viewModelScope`，成为未捕获协程错误；修复后普通异常写安全诊断并显示就地重试，成功后清除错误。App 单测 436 项、架构测试 72 项均 0 失败/错误；`verifyLocal` 成功，191 项任务（15 执行、176 up-to-date），包含 Beta/Official Release 编译与 Lint，不打包 APK（CI-125）。
- 旧版条码迁移失败传播：取消和 `AssertionError` 回归在旧实现下均失败；修复后 Data 单测 73 项、0 失败/错误。完整 `verifyLocal -PenableAppUnitTests=true --offline` 成功，191 项任务（27 执行、2 缓存、162 up-to-date），覆盖五模块测试、架构契约、Beta/Official Release 编译和 Lint，不组装 APK（CI-127）。Robolectric 需用 `D:\Barcode_build\t` 作为短 Java 临时目录（CI-053）。
- 旧版设置迁移致命错误保护：新增回归测试先在旧实现下失败，证实 `runCatching` 吞掉 `AssertionError` 后会继续完成迁移并清理旧偏好；改为只对普通异常回退并传播取消/致命错误后，Data 单测 74 项、0 失败/错误。完整 `verifyLocal -PenableAppUnitTests=true --offline` 成功，191 项任务（30 执行、1 缓存、160 up-to-date），覆盖五模块测试、架构契约、Beta/Official Release 编译和 Lint，不组装 APK（CI-128）。
- 旧版设置迁移默认值兼容：新增 Robolectric 集成回归，分别向八个旧设置键写入错误类型，确认迁移仍将缺陷字段回退到 `StyleSettings` 默认值、成功保存 DataStore 门闩并清除旧键；完整 `:core:data:testDebugUnitTest` 成功，75 项、0 失败/错误（CI-131）。
- 自动备份范围：`allowBackup=true`；Room `barcode_generator.db` 与 DataStore `barcode_settings` 均处于 Android 默认备份目录，现有 Android 11 及以下/Android 12+ 规则只排除临时 LAN 与调试日志。代码注释表明这是为保留用户资料而设计；云备份和设备间迁移目前都包括这些数据。行为有产品影响，已请求用户选择，暂不调整（CI-129）。[Android Auto Backup 官方文档](https://developer.android.com/identity/data/autobackup?hl=en)
- 明文网络范围：Manifest 使用 `usesCleartextTraffic=true`。静态调用点仅有 LAN HTTP 客户端；它要求有效 IPv4 会话且在发请求前检查目标属于当前 Wi-Fi 路由器子网，外部更新目录/安装包使用 HTTPS 并拒绝降级。Network Security Config 的例子按固定 domain 配置，无法直接表达动态路由器子网 CIDR；为保留任意家庭 Wi-Fi 的 LAN 功能而未改成静态域名白名单。全局平台许可仍比单一 LAN 目标更宽，记录为待确认的安全取舍，不把调用点检查等同于 OS 级限制（CI-130）。[Android 网络安全配置文档](https://developer.android.com/privacy-and-security/security-config)
- Gradle Wrapper 完整性：新增契约先分别在缺发行摘要、旧版 Wrapper JAR 上失败；更新为 Gradle 官方 9.5.0 ZIP 摘要及匹配版本 JAR 后通过。完整 `verifyLocal -PenableAppUnitTests=true --offline` 成功，191 项任务（4 执行、187 up-to-date），覆盖 Beta 单测、架构检查、Beta/Official Release 编译与 Lint，不组装 APK（CI-118）。
- PR 门禁接入 Release 验证后，使用 `D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline verifyLocal` 对当前工作树做完整本地合跑：`BUILD SUCCESSFUL`，191 项任务（3 执行、2 缓存、186 up-to-date）；覆盖 Beta 单测、架构检查、Beta/Official Release 编译与 Lint；未组装 APK（CI-117）。
- 正式版 Release 恢复可见性：旧测试按预期失败，验证原逻辑会保持 `draft=false`；修复后正式版脚本 15/15、Beta 与正式版版本分配脚本合计 22/22 通过。未调用 GitHub API 或触发发布（CI-114）。
- 正式版发布并发队列契约：新增测试在缺少 `queue: max` 的原配置下按预期失败；加入队列后定向架构测试通过。完整 `verifyLocal -PenableAppUnitTests=true --offline` 成功，191 项任务（33 执行、158 up-to-date），不组装 APK（CI-116）。
- 更新目录 SHA-256 状态契约回归：旧实现下缺失摘要的 Beta 更新被错误标记 `Available`；修复后定向测试 3/3 通过，完整 `:core:data:testDebugUnitTest :core:domain:test` 与 `verifyLocal -PenableAppUnitTests=true --offline` 均成功。后者 191 项任务（30 执行、1 缓存、160 up-to-date），包含 Beta 单测、架构测试、Beta/正式版编译及 Release lint，不组装 APK；`git diff --check` 通过，仅有既有换行风格提示（CI-115）。
- `:core:domain:test :core:data:testDebugUnitTest :core:lan-share:testDebugUnitTest`：`BUILD SUCCESSFUL`，51 项任务，1 项执行、50 项 up-to-date。
- `-PenableAppUnitTests=true :app:testBetaDebugUnitTest :architecture-tests:test`：`BUILD SUCCESSFUL`，76 项任务，11 项执行、65 项 up-to-date；包含 CI-070 公共路径回归测试。
- 本轮 `:core:lan-share:testDebugUnitTest`：`BUILD SUCCESSFUL`，17 项任务，5 项执行、12 项 up-to-date；包含 CI-075 上传文件名回归测试。
- 本轮 Beta 日志队列、导出截断与 `DebugLogContractTest` 定向验证：`BUILD SUCCESSFUL`，76 项任务，2 项执行、74 项 up-to-date。
- 本轮完整 `-PenableAppUnitTests=true :app:testBetaDebugUnitTest :architecture-tests:test`：`BUILD SUCCESSFUL`，76 项任务，2 项执行、74 项 up-to-date；覆盖当前工作树下全部 Beta 单测和架构契约。
- 本轮 `verifyLocal -PenableAppUnitTests=true`：`BUILD SUCCESSFUL`，191 项任务（42 项执行、1 项缓存命中、148 项 up-to-date）；覆盖五模块测试、架构边界、Beta/正式版 Release 编译与 Lint，不打包 APK。
- 补齐旧日志 Android 私有目录脱敏、条码识别及结果图片写入诊断后，再次运行 `verifyLocal -PenableAppUnitTests=true`：`BUILD SUCCESSFUL`，191 项任务（27 项执行、164 项 up-to-date）；不打包 APK。
- 追加旧日志带空格路径回归后，运行 `verifyLocal -PenableAppUnitTests=true`：`BUILD SUCCESSFUL`，191 项任务（15 项执行、176 项 up-to-date）；不打包 APK。
- `:app:testBetaDebugUnitTest`：`BUILD SUCCESSFUL`，验证 CI-091 恢复器只拦截普通异常；此前新增的致命 `Error` 回归测试在修复前按预期失败。该测试运行不打包 APK。
- `:architecture-tests:test --rerun-tasks`：`BUILD SUCCESSFUL`；新增 CI-092 workflow artifact 重试契约测试，两个发布 workflow 均允许同一 run 重传临时 artifact。该测试运行不打包 APK。
- `-PenableAppUnitTests=true :app:testBetaDebugUnitTest :architecture-tests:test`：`BUILD SUCCESSFUL`，Beta 单测报告 398 项、0 失败；架构契约测试也通过，包含 CI-092 workflow 重试契约和 CI-095 预览异常分类。76 项任务中 2 项执行、74 项 up-to-date；不打包 APK。
- 本轮修复 CI-096 后，`PersistenceWriteQueueTest` 8/8 通过；完整 `-PenableAppUnitTests=true :app:testBetaDebugUnitTest :architecture-tests:test` 成功，Beta 单测 402 项、架构契约 59 项均 0 失败/错误（76 项任务，2 项执行、74 项 up-to-date）。未打包 APK。
- 本轮修复 CI-097 后，完整 Beta 单测 405 项与架构契约 59 项均 0 失败/错误；`verifyLocal -PenableAppUnitTests=true` 成功，191 项任务（25 项执行、1 项缓存、165 项 up-to-date），包含 Beta/正式版 Release 编译与 Lint，不打包 APK。
- 本轮完成 CI-100/101 后，完整 Beta 单测 409 项与架构契约 60 项均 0 失败/错误；新增两处异常传播回归契约均先在旧实现下按预期失败，再通过。通过 `D:\Barcode_build\run-barcode-android-gradle.ps1` 验证；不打包 APK。
- 本轮完成 CI-102 后，完整 Beta 单测 409 项与架构契约 61 项均 0 失败/错误；安装权限拒绝后返回的恢复契约先在旧实现下失败，修复后通过；不打包 APK。
- 正式版/Beta 版本分配器本机回归分别 15/15、5/5 通过；两份 workflow 静态核对确认只手动触发、各自限制 main/beta 分支、发布串行保护与上传验证后发布。未触发远端 Actions。
- `:app:testBetaDebugUnitTest --tests '*LanSharePreviewDownloadResultTest'`：`BUILD SUCCESSFUL`，普通异常仍转成可降级失败，取消与致命错误传播；修复前后 2 项预期失败均由红转绿。该测试运行不打包 APK。
- 本轮完成 CI-103、CI-105–CI-108 后：完整 Beta 单测 429 项、架构契约 66 项均 0 失败/错误；`verifyLocal -PenableAppUnitTests=true --offline` 成功，191 项任务（15 执行、176 up-to-date），覆盖 Beta/Official Release 编译与 Lint，不组装 APK。
- CI-110 后：Beta 版本分配 Python 测试 7/7 通过；架构契约 67 项、Beta 单测 429 项均 0 失败/错误；固定验证脚本的 `verifyLocal -PenableAppUnitTests=true --offline` 成功，191 项任务（4 执行、187 up-to-date），没有组装 APK。
- CI-111 后：GlassBackdrop 原生 Compose 绘制回归测试 1/1 通过；临时关闭重入保护时测试按预期复现 `IllegalStateException`，恢复保护后通过。完整 Beta 单测 430 项、架构契约 67 项、Domain 23 项、Data 66 项、LAN Share 66 项均 0 失败/错误；`verifyLocal -PenableAppUnitTests=true --offline` 成功，191 项任务（4 执行、1 缓存、186 up-to-date），不组装 APK。
- CI-112：官方版本分配脚本 Python 测试 15/15 通过；定向 `UpdateSecurityTest` 5/5、`UpdateApkAssetNameTest` 6/6 通过。完整 Beta 单测 430 项、Domain 25 项、Data 68 项、LAN Share 66 项、架构契约 67 项均 0 失败/错误。固定工具执行 `verifyLocal -PenableAppUnitTests=true --offline` 成功，191 项任务（6 执行、1 缓存、184 up-to-date）；首次默认 temp 下触发 CI-053 已记录的 SQLite 路径问题，重跑时只对本次进程设置 `_JAVA_OPTIONS=-Djava.io.tmpdir=D:/Barcode_build/t` 后通过。未组装 APK。
- CI-113：LAN host 启动状态测试模拟 NanoHTTPD 工作线程在初始快照读取期间发送连接事件，定向测试通过；未改产品代码。测试最初因主线程重入及异步 collector 调度等待不当而失败；按生产后台线程模型和项目已有的有界 Looper 泵送方式后，定向测试通过。未运行 APK 或全量验证。
- 上述均为单元/架构测试，不构建 APK，也不代表真机、远端 Actions 或发布验证。

## 工作记录

### CI-001 — `:core:data` 收藏持久化与快照一致性

- **检查范围**：收藏数据的 Room 写入、快照同步、导入导出、迁移，以及直接依赖的 `:core:domain` 接口与测试。
- **问题依据/优先级**：已确认 P2 数据保留问题。删除最后一个收藏关联时，`deleteFavoriteGroups` 删除关联和分组，但不清理 `inHistory=false` 的条码行；`inHistory=false` 来自用户删除历史，行因此不再出现在历史或收藏中，却仍留在数据库。其他收藏关联和历史记录必须保留。
- **状态和依赖**：已验证，完成；核对了 `:core:domain` 收藏模型、ID/顺序契约及数据仓库接口。
- **修改摘要**：增加事务内清理条件“非历史且无收藏关联”；覆盖收藏分组删除、文件夹删除、清空收藏、快照收藏变更、收藏备份替换及收藏关系写入。仍在历史或被其他收藏文件引用的行不会删除。
- **验证及结果**：新增 3 个 Room 回归测试，先在旧实现上以孤立条码断言失败，再在修复后通过；覆盖分组删除、文件夹删除、清空收藏、覆盖导入、关系变更，并确认历史项/其他收藏关联保留。`:core:data:testDebugUnitTest :core:domain:test` 成功；Data 单测实际运行，迁移集成测试类 9/9 通过，未变更的 Domain 测试任务为 up-to-date。测试临时目录使用 `D:\Barcode_build\t` 规避 Robolectric 默认临时路径过长。
- **阻碍/恢复条件/下一步**：无外部阻碍。检查 `:core:data` 中更新包下载、APK 校验及 HTTPS 重定向；该区域完成后继续设置持久化。

### CI-002 — `:core:data` 应用更新下载与 APK 安全校验

- **检查范围**：更新目录读取、HTTPS 重定向策略、APK 下载与校验；追踪至 `:core:domain` 更新安全规则及对应测试。
- **问题依据/优先级**：复核后未确认缺陷。检查了近期提交 `e9b9373` 相关 HTTPS 跳转、下载及包校验链路。
- **状态和依赖**：已检查，无需修改；依赖的 `:core:domain` 更新规则保持纯 Kotlin。
- **修改摘要**：无代码修改。下载禁用自动跳转、每跳限制 HTTPS/目标主机信息并最多 5 次；下载同时限制 500 MB，要求 SHA-256，大小与摘要验证后才改名；安装前校验包名、版本递增和签名一致。
- **验证及结果**：`:core:data:testDebugUnitTest` 整套测试通过，包含重定向策略、APK 文件名测试及后续增加的更新包身份策略测试（见 CI-059）。Android PackageManager 对真实 APK 档案及已安装包签名的提取仍无平台级集成测试；静态核对实现，没有发现绕过校验的路径。
- **阻碍/恢复条件/下一步**：无外部阻碍。继续检查 `:core:data` 设置持久化和写入队列。

### CI-003 — `:core:data` 设置持久化与并发写入

- **检查范围**：DataStore 偏好读写、设置编码、异步写入队列、失败与取消语义；追踪至 `:core:domain` 设置模型及对应测试。
- **问题依据/优先级**：已确认 P2 即时行为不一致。设置页先更新 OCR 纠错选项并异步写 DataStore，但相机识别入口从仓库缓存读取；用户快速切换后立即识别时会使用旧纠错规则。
- **状态和依赖**：已验证，完成；依赖 `:core:domain` 的 `StyleSettings`、`SettingsRepository` 和写入完成语义。
- **修改摘要**：OCR 识别读取最新 ViewModel 设置快照，避免异步 DataStore 写入尚未完成时仍使用旧纠错选项。
- **验证及结果**：新增回归测试，旧实现中“切换后写入未完成即读取”断言失败；修复后通过。`-PenableAppUnitTests=true :app:testBetaDebugUnitTest :core:data:testDebugUnitTest :core:domain:test` 成功（90 个任务，17 个执行、73 个 up-to-date）。
- **阻碍/恢复条件/下一步**：无外部阻碍。继续审查收藏备份导入/导出的格式校验、损坏输入处理和事务原子性。

### CI-004 — `:core:data` 收藏备份导入与导出

- **检查范围**：收藏备份编码、解析、版本兼容、输入边界校验，以及导入写入的事务/失败恢复；仅追踪直接调用链。
- **问题依据/优先级**：未确认导入/导出缺陷。现有实现限制 ZIP 输入 64 MB、解压内容 32 MB、单收藏 JSON 1 MB、条目数 2,048；解析校验文件名、条码内容、格式数量和两级文件夹。导入替换通过 Room `withTransaction` 执行。
- **状态和依赖**：已检查，无需行为修复；依赖 `:core:domain` 收藏导入规划与备份数据结构。
- **修改摘要**：新增两条回归保护：无中央目录的截断 ZIP 必须拒绝；导入中发生唯一排序位置冲突后，原收藏文件与条码仍须完整保留。未改产品行为。
- **验证及结果**：新增截断包测试确认当前解析器拒绝缺少中央目录的归档；事务测试确认写入失败会回滚整个替换。最终完整 `:core:data:testDebugUnitTest` 在所有新增测试后通过。
- **阻碍/恢复条件/下一步**：无外部阻碍。继续审查历史持久化、排序和清理边界。

### CI-005 — `:core:data` 历史与条码顺序

- **检查范围**：历史记录写入/删除/清空、分页边界、快照顺序与 `position` 恢复；追踪到领域排序规则和直接测试。
- **问题依据/优先级**：已确认 P1 数据丢失。`StartupBarcodeSnapshot` 明确仅限制启动加载窗口，但历史批量删除将最多 500 条内存快照传入 `saveItems`；旧实现把快照缺项解释为删除，连带清除所有未加载的更早历史。
- **状态和依赖**：已验证，完成；依赖 `:core:domain` 中有界启动快照语义及历史关联清理规则。
- **修改摘要**：`saveItems` 只更新显式传入行，不再删除快照外历史；仍清理明确标记为非历史且无收藏关联的条目。移除不再使用的 DAO 删除查询，并在 Repository 契约注明快照缺项不代表删除。
- **验证及结果**：新增 501 条 Room 回归测试，旧实现失败、修复后通过；确认显式删除的历史行被清理且第 501 条更早记录保留。`-PenableAppUnitTests=true :core:data:testDebugUnitTest :app:testBetaDebugUnitTest :core:domain:test` 成功（90 个任务，17 个执行、73 个 up-to-date）。
- **阻碍/恢复条件/下一步**：无外部阻碍。进入 `:core:domain`，先检查条码校验与生成规则的一致性和边界。

### CI-006 — `:core:domain` 条码校验与生成规则

- **检查范围**：格式识别、内容校验、批量生成过滤、格式化与回归测试；追踪到直接调用方但不扩展到 UI。
- **问题依据/优先级**：待检查；校验和生成规则不一致可能造成可生成内容被错误拒绝，或无效条码进入结果/历史。
- **状态和依赖**：已检查，无需行为修复；纯 Kotlin 领域规则，无项目模块依赖。
- **修改摘要**：增加 Codabar 小写保护符与 ZXing 编码器一致性测试；实际编码和校验规则一致，无需改动。
- **验证及结果**：新增 parity 测试的 3 组输入均通过；`-PenableAppUnitTests=true :app:testBetaDebugUnitTest` 成功。前序 `:core:domain:test` 随后也在 CI-007 验证通过。
- **阻碍/恢复条件/下一步**：无外部阻碍。检查收藏身份冲突规划和版本比较规则。

### CI-007 — `:core:domain` 收藏身份规划

- **检查范围**：收藏文件路径/名称身份、重复备份冲突、覆盖策略；追踪到 `FavoriteFileIdentity` 和导入调用方。
- **问题依据/优先级**：已确认 P2 导入误跳过。规划器用 NUL 拼接文件夹和名称，导入 JSON 可包含 NUL，导致字段不同的身份元组被视为同一个文件并跳过导入。
- **状态和依赖**：已验证，完成；依赖备份文件字段解析与导入规划契约。
- **修改摘要**：规划比较改用 `FavoriteFileIdentity` 结构化字段；对外冲突展示字符串保持原格式，避免不必要的接口变化。
- **验证及结果**：新增字段边界回归测试，旧代码失败、修复后通过；`:core:domain:test` 与 `-PenableAppUnitTests=true :app:testBetaDebugUnitTest` 成功（75 个任务，16 个执行、59 个 up-to-date）。
- **阻碍/恢复条件/下一步**：无外部阻碍。检查剩余领域模型：LAN 会话地址、预览键/图片类型及尺寸约束。

### CI-008 — `:core:domain` LAN 会话与预览模型

- **检查范围**：分享 URL 解析、远端预览缓存键、图片类型识别、预览尺寸/缓存上限、实时状态集合边界。
- **问题依据/优先级**：复核后未确认缺陷。分享 URL 限定 HTTP、IPv4、根路径、有效端口与唯一 43 字符令牌；远端预览 ID 哈希为固定 64 位十六进制文件名；预览文件上限小于缓存总上限。
- **状态和依赖**：已检查，无需修改；直接依赖后续 `:core:lan-share` 网络实现。
- **修改摘要**：无代码修改。
- **验证及结果**：`LanShareSessionTest`、`LanSharePreviewKeyTest`、`LanShareImageNameTest` 随前序 Domain/App 测试任务通过；静态检查只允许根 URL，路径、片段、非 IPv4、无效令牌及重复令牌均拒绝。
- **阻碍/恢复条件/下一步**：无外部阻碍。开始检查 LAN 服务 HTTP/WebSocket、上传接收与原子落盘。

### CI-009 — `:core:lan-share` 文件收发与 HTTP 路由

- **检查范围**：HTTP 授权与配对、固定长度/分块上传、容量预留、临时文件及中断清理、下载/预览写盘与文件发布；追踪直接网络客户端和服务端测试。
- **问题依据/优先级**：确认 P1 数据安全风险：`downloadToFile` 与 `downloadPreview` 原先先删除已有目标，再尝试将完成的临时文件改名；若后一步失败，用户原文件已不可恢复。服务端上传已有长度/容量上限、请求授权、同目录暂存与异常清理；文件读取使用规范路径并拒绝共享目录之外的对象，当前未发现可证实的越权或部分文件发布问题。
- **状态和依赖**：已验证，完成；依赖 `:core:domain` 分享会话/文件模型，服务端行为可由本地 HTTP 测试验证。
- **修改摘要**：新增下载暂存文件和目标替换工具：暂存文件固定创建在目标同目录，完成后通过 `Files.move(..., REPLACE_EXISTING)` 替换，不预先删除目标；移动失败时保留原目标和暂存内容。原始下载和图片预览共用这条安全提交路径；下载目录缺失时会先创建并验证。
- **验证及结果**：回归测试覆盖移动失败时旧文件与暂存文件均保留、成功替换、暂存目录一致，以及真实本地 HTTP 下载覆盖旧文件。先运行测试确认缺失实现导致编译失败，再实现后定向测试通过；`:core:lan-share:testDebugUnitTest` 全量通过（17 个任务，2 个执行、15 个 up-to-date）。
- **阻碍/恢复条件/下一步**：无外部阻碍。继续核对同模块网页与图片预览、WebSocket 连接生命周期（CI-010）。

### CI-010 — `:core:lan-share` 浏览器运行时与图片预览

- **检查范围**：浏览器页面初始化、会话/客户端标识存储、文件与消息 DOM 更新、图片预览的 EXIF 方向处理；追踪 Android 旧版本解码分支和模板契约测试。
- **问题依据/优先级**：确认两项 P2 兼容/显示缺陷。浏览器脚本在初始化时直接读取/写入 `localStorage` 与 `sessionStorage`，访问受限时会抛异常并中断整段传输页脚本；Android 8/8.1 的 `BitmapFactory` 回退分支只映射旋转方向，EXIF 镜像、垂直翻转、转置和反转置会显示错误方向。
- **状态和依赖**：已验证，完成；依赖浏览器 Web Storage 异常边界及 `:core:lan-share` 图片解码适配。
- **修改摘要**：增加存储安全读写包装器，存储不可用时依赖 cookie/本次页面内生成的客户端标识继续工作，配对流程不再因 sessionStorage 写失败中断；提取共享 EXIF 八方向映射并由两个旧版 BitmapFactory 预览分支复用，变换后回收被替换的原始 Bitmap。
- **验证及结果**：浏览器静态契约回归在旧实现上失败，修复后网页脚本测试通过；EXIF 八方向映射及图片采样边界定向测试通过。最终 `:core:lan-share:testDebugUnitTest` 全量通过（17 个任务，4 个执行、13 个 up-to-date）。静态复核预览缓存 128 MB 上限、输出边长 4,096、TIFF 像素预算和失败回退；WebSocket 单设备队列有界、写超时关闭底层连接，心跳/断线宽限与停止清理均有对应测试，未发现额外可证实缺陷。
- **阻碍/恢复条件/下一步**：无外部阻碍。进入 `:app`，先检查启动/导航与生成、结果、历史、收藏、设置流程的崩溃和数据传递边界。

### CI-011 — `:app` 相机文字与条码识别异常边界

- **检查范围**：外部相机/相册返回后的 Bitmap 识别、OCR/条码网关调用、失败提示与位图生命周期；追踪 `MainActivity` 到 `CameraOcrViewModel`、`CameraOcrFacade` 和 Data 网关。
- **问题依据/优先级**：确认 P2 崩溃风险。OCR 普通异常原先记录后重新抛出，异常从 `viewModelScope` 逃逸且没有界面兜底；条码解码网关能处理 ZXing 失败，但进入网关前的 Bitmap PNG 编码仍可能抛普通异常，而 Activity 生命周期协程没有捕获该异常。
- **状态和依赖**：已验证，完成；依赖 `CameraOcrViewModel` 的一次性事件和既有未识别提示契约。
- **修改摘要**：将 OCR 识别及事件映射集中在可测试的挂起流程；普通异常记录日志并发送“文字识别失败，请重试”，取消仍向上传播。条码识别开始、成功、取消和失败均记录同一操作 ID；成功日志只记是否有结果，不写条码内容。普通异常仍返回既有空结果，Bitmap 继续在 `finally` 回收。
- **验证及结果**：新增 OCR 网关异常测试，验证出现失败提示且 Bitmap 被回收；条码识别测试覆盖开始/失败、开始/成功、取消传播及日志不含条码内容。全量 `verifyLocal -PenableAppUnitTests=true` 通过（191 项任务，27 项执行、164 项 up-to-date）。未做真机相机/相册测试。
- **阻碍/恢复条件/下一步**：无外部阻碍。继续检查 `:app` 的其他状态突变和系统回调；相机异常已通过 JVM/Robolectric 验证，真机验证留待连接设备后补做。

### CI-012 — `:app` 历史列表有界加载行为

- **检查范围**：启动历史快照、Repository 读取上限、历史页展示与后续加载入口。
- **问题依据/优先级**：确认行为限制但产品意图未明。Room 启动查询只返回按时间倒序的 500 条历史；Repository 将其作为 `StartupBarcodeSnapshot`；`HistoryScreen` 只过滤并展示传入列表，`HistoryViewModel` 没有加载更多接口。数据库中更早历史仍保留，但页面无法访问，用户可能误以为旧记录丢失。
- **状态和依赖**：待确认；依赖用户决定是否保留“最近 500 条”产品限制，还是支持完整历史分页。期间不扩大启动加载范围或改变 UI 行为。
- **修改摘要**：无代码修改。
- **验证及结果**：静态核对 `BarcodeDao.loadStartupItems` 的 `LIMIT 500`、`RoomBarcodeRepository` 调用链及 History UI/ViewModel；未发现历史页分页加载入口。数据库更早记录保留已由 CI-005 的 501 条回归测试证实。
- **阻碍/恢复条件/下一步**：等待用户选择“保留 500 条”“分页查看全部”或“保留并提示更早记录”；继续检查与该产品决策无关的 `:app` 流程。

### CI-013 — `:architecture-tests` 输入完整性与 configuration cache

- **检查范围**：Konsist 源码范围、静态源码契约读取路径、Gradle 测试输入声明、模块依赖校验 task 与本地验收入口。
- **问题依据/优先级**：确认 P2 验证可靠性缺陷。`DebugLogContractTest` 读取 `app/src/beta` 和 `app/src/official`，但原测试任务输入只包含 `app/src/main` 与 Core；变体源码变化时检查可能保持 UP-TO-DATE。另，根工程 `verifyArchitectureModuleDependencies` 在 task action 中访问 `Project` 和 build script 顶层对象，项目启用 configuration cache 时使整个组合验收失败。
- **状态和依赖**：已验证，完成；依赖生产变体源集与 Gradle configuration-cache 配置。
- **修改摘要**：将 Beta/正式版 Kotlin 源码加入架构测试输入，并加防回归断言；把模块图和领域禁止依赖收集移到 task 配置阶段，task action 仅消费不可变快照，不再捕获 Gradle `Project` 或脚本对象。
- **验证及结果**：`:architecture-tests:test verifyArchitectureModuleDependencies` 成功；重复执行同一命令显示 `Reusing configuration cache` 且成功。架构测试与模块依赖规则均执行/保持有效。
- **阻碍/恢复条件/下一步**：无外部阻碍。继续 `:app` 的 LAN 会话生命周期与文件传输 ViewModel 检查，再核对 workflow/版本/签名边界。

### CI-014 — `:app` 局域网会话与传输状态生命周期

- **检查范围**：房间加入/关闭时的刷新代次、上传任务取消与临时文件清理、下载目标写入、外部文件选择结果的会话读取。
- **问题依据/优先级**：复核后未确认新的缺陷。刷新结果由房间对象和代次双重约束；切换/关闭房间会使旧刷新失效、取消上传并清理暂存文件；上传成功/失败事件只在对应房间仍有效时更新 UI。下载先完成到临时文件后再复制到用户选择的目标，关闭分享页不会破坏已由用户发起的独立下载。
- **状态和依赖**：已检查，无需修改；依赖 `:core:lan-share` 下载暂存与原子提交行为（CI-009）。
- **修改摘要**：无产品代码修改。
- **验证及结果**：`LanShareRefreshGuardTest`、`LanShareTransferCleanupTest` 与 `LanShareUploadingContentTest` 定向运行通过；此前 `:core:lan-share:testDebugUnitTest` 已全量通过。检查包含旧房间/同地址重入/关闭后的延迟刷新拒绝，以及暂存传输状态清理。
- **阻碍/恢复条件/下一步**：无外部阻碍。继续 `:app` 系统 Activity 结果、权限和导航回调检查；待产品确认的历史 500 条展示不纳入本项。

### CI-015 — Beta/正式版工作流与版本分配

- **检查范围**：Beta/正式版 GitHub Actions 触发条件、权限/签名边界、草稿恢复、产物与校验文件验证，以及 Python 版本分配脚本。
- **问题依据/优先级**：确认并修复一个 P3 版本边界 off-by-one：Beta 脚本原先拒绝分配 `2,100,000,000`，而该值是 Android Developers 文档注明的 Google Play 最大合法 `versionCode`，正式版分配器也按含上界处理。Beta 与正式版/官方约束对最大值的理解不一致，会让 Beta 少使用最后一个合法编号。参考：[Android Developers — Version your app](https://developer.android.com/studio/publish/versioning)。其余手动触发、分支、签名 Secrets 与发布校验静态检查未发现新缺陷。
- **状态和依赖**：已修复，待远端验证；最大值边界修复及回归测试已包含在 `origin/beta` 的 `bea226bb4af1eff2fcf7631844aaf1085ed926f4` 中。远端已有的 `versionCode=4` 成功运行早于该修复，不能证明上界场景。
- **修改摘要**：Beta 版本分配现在允许基准值或下一版本恰好等于 `2,100,000,000`，并仍拒绝超出上限的值；新增上界回归测试。未修改工作流或正式版发布逻辑。
- **验证及结果**：当前 Beta 版本脚本单测 7/7 通过，正式版版本脚本单测 15/15 通过；已确认修复提交是 `origin/beta` 的祖先。正式版测试首次因系统临时目录 ACL 不可写而失败，将本次进程的 `TEMP/TMP` 指向 `D:\Barcode_build` 后重跑通过；未修改产品代码或正式版发布逻辑。未触发新的远端 Actions 或 APK 构建。
- **阻碍/恢复条件/下一步**：仍需一次包含上界修复的手动 Beta workflow 运行来确认远端执行；本轮 `D:\gh.exe auth status` 报告 GitHub CLI token 无效，因此未能发起运行。重新认证后可运行 Beta workflow，并同时验证 CI-163 的 Node 24 Artifact Action。继续不依赖远端的模块审查。

### CI-016 — `:app` 权限 ActivityResult 判定

- **检查范围**：多权限请求的发起、Activity 重建后的请求状态恢复、系统回调结果分类与相机/图库/文件选择后续动作。
- **问题依据/优先级**：确认 P2 权限回调误判。`RequestMultiplePermissions` 在取消/无结果时返回空映射；旧逻辑执行 `result.values.all { it }`，Kotlin 对空集合返回 `true`，因此会把未获授权误判为全部授权，并继续进入相机或文件选择操作。缺少某个请求项时也会因只检查已返回值而误放行。
- **状态和依赖**：已验证，完成；依赖 AndroidX Activity Result 回调与 `SavedStateHandle` 保存请求身份。
- **修改摘要**：协调器现在同时保存一次性请求编号及其权限名，并返回明确的 `PermissionRequestResult`；只有每个请求权限都在回调映射中显式为 `true` 才视为授权。Activity 以此结果决定是否继续；取消、空映射、缺项和拒绝均走原有未授权提示，不改变权限类型或产品功能。
- **验证及结果**：先新增取消回调回归测试，旧分类器红灯（`AssertionError`）；修复后取消/缺项/拒绝、全部授权、SavedStateHandle 重建的一次性消费测试通过。完整 `:app:testBetaDebugUnitTest` 成功。未构建 APK，未做真机权限弹窗测试。
- **阻碍/恢复条件/下一步**：无外部阻碍。继续检查设置状态与异步持久化反馈；用户待确认的历史列表上限继续保持不变。

### CI-017 — `:app` 结果与收藏保存状态交接

- **检查范围**：结果编辑回填、生成结果持久化后导航、收藏文件保存/重命名/移动及分页/搜索刷新边界。
- **问题依据/优先级**：未确认新缺陷。结果编辑先发布完整输入快照再切页；生成条码图片成功且持久化提交完成后才进入结果页。收藏保存按结果 ID 顺序构造文件并等待事务提交；失败时恢复持久化快照且保持对话框输入。分页缺失的收藏关系按“未知”处理，不会被误清除。
- **状态和依赖**：已检查，无需新增修复；依赖既有 `:core:data` 事务与 `:core:domain` 顺序/身份契约（CI-001、CI-005、CI-007）。
- **修改摘要**：无产品代码修改。
- **验证及结果**：本轮完整 `:app:testBetaDebugUnitTest` 通过，涵盖结果状态、收藏有序保存、分页/局部加载变更与写入失败恢复。检查结果页编辑逻辑及保存对话框只在提交成功后完成导航。
- **阻碍/恢复条件/下一步**：无外部阻碍。开始检查设置页主题、滑块/开关即时状态、持久化队列及失败回滚/提示。

### CI-018 — `:app` 设置即时状态与持久化反馈

- **检查范围**：设置状态唯一所有者、滑块提交时机、主题切换持久化完成后应用、OCR 纠错即时值与失败提示。
- **问题依据/优先级**：复核未确认新缺陷。滑块拖动期间使用草稿值，完成后才提交；设置更新通过不可变快照写入。OCR 入口读取 ViewModel 最新值而非滞后的 DataStore 缓存（CI-003）。异步写完成后记录成功/失败，失败通过应用级通知并触发持久化数据恢复。
- **状态和依赖**：已检查，无需修改；依赖 DataStore `SettingsRepository` 与 `:core:data` 设置序列化。
- **修改摘要**：无本项产品代码修改。
- **验证及结果**：本轮完整 `:app:testBetaDebugUnitTest` 通过，其中包含设置写入完成状态、失败提示和 OCR 新值即时读取回归测试；UI slider 仅在 `onValueChangeFinished` 提交。
- **阻碍/恢复条件/下一步**：无外部阻碍。继续检查条码图片渲染/缓存、结果列表生命周期及保存分享输出边界。

### CI-019 — `:app` 结果图片文件导出失败清理

- **检查范围**：结果图片合成后保存到文件的系统文档选择器启动、待处理文件状态及临时 PNG 生命周期。
- **问题依据/优先级**：确认 P2 可重试性/缓存残留缺陷。`launchExternalActivity` 在无可用系统选择器或启动异常时返回 `false`；结果图片文件保存调用方原先忽略该返回值，导致 `pendingResultImageFile` 未清空且临时 PNG 不删除。后续文件导出因 pending 状态检查而直接退出，直到 Activity 重建才恢复。
- **状态和依赖**：已验证，完成；依赖 `MainActivity.launchExternalActivity` 的布尔启动契约。
- **修改摘要**：增加待处理导出文件启动协调函数；只有启动成功时才保留待处理状态，失败或抛错时清空引用并删除临时文件。成功路径保持文件等待系统回调。
- **验证及结果**：新增成功/失败两条回归测试；失败验证引用清空且临时文件删除，成功验证两者保留。`PendingDocumentLaunchTest` 定向运行通过。
- **阻碍/恢复条件/下一步**：无外部阻碍。继续审查条码位图缓存、结果图片大批量合成与分享/相册输出资源回收。

### CI-020 — `:app` 条码图片缓存与结果导出资源生命周期

- **检查范围**：ZXing 图片生成、内存/磁盘缓存上限与锁、结果列表预览位图所有权、大批量 PNG 合成释放、相册/分享/系统文件选择器写入与失败清理。
- **问题依据/优先级**：未发现新的可证实缺陷。内存缓存按 64 MiB 的 LRU 权重限制，磁盘缓存按 64 MiB/512 张维护；条码尺寸由领域设置限制。结果预览批量生成受并发数限制，部分失败会回收已创建行图；结果导出逐行合成并在 `finally` 释放行图和原始条码图，导出总像素有上限。相册和目标 URI 写入失败会删除未完成 MediaStore 项或临时文件。
- **状态和依赖**：已检查，无需修改；依赖 `:core:data` 缓存策略、领域渲染尺寸规则以及 CI-019 的待处理文件启动回滚。
- **修改摘要**：无本项产品代码修改；CI-019 的系统文档选择器启动失败清理已通过本次完整回归复验。
- **验证及结果**：`:app:testBetaDebugUnitTest` 与 `:core:data:testDebugUnitTest` 同次运行成功（88 个任务，4 个执行、84 个 up-to-date）；`git diff --check` 无空白错误。未构建 APK 或执行设备/系统分享器测试。
- **阻碍/恢复条件/下一步**：无外部阻碍。继续检查 `:app` 的生成输入解析、验证/批量输出与结果数量边界；历史只显示最近 500 条的产品选择仍待确认，不在本项改变。

### CI-021 — `:app` 生成页批量粘贴解析上限

- **检查范围**：逐行/单列 CSV 解析、空行与 BOM 处理、源行号、重复值保留、最多 100 行限制，以及追加草稿的原子性。
- **问题依据/优先级**：确认 P2 输入压力风险。解析器虽然将有效输入限制为 100 行，但原先先用正则 `split` 将整段粘贴文本物化成行列表，再遇到第 101 行时拒绝；巨量文本会先产生与输入行数同规模的临时字符串和列表项，可能造成明显卡顿或内存压力。
- **状态和依赖**：已验证，完成；依赖现有批量粘贴输入和逐行 CSV 单字段规则，不改变条码格式校验与生成用例。
- **修改摘要**：改为按行扫描 `CharSequence`，只为当前行分配字符串；越过行容量即返回，不再解析或分割余下文本。保留 CRLF/LF/CR、BOM、空白行、引号转义、原始行号、重复值和原子追加行为。
- **验证及结果**：新增计数型 10 万行/50 万字符输入回归测试，确认达到容量上限后仅读取前缀即在第 3 行（测试上限为 2）失败；既有解析规则测试一并通过。随后完整 `:app:testBetaDebugUnitTest` 通过，末次整理后解析器定向测试也通过。未构建 APK。
- **阻碍/恢复条件/下一步**：无外部阻碍。继续核查 `:app` 收藏文件夹重命名、移动和层级路径在多级目录下的一致性；历史 500 条展示范围仍待产品确认。

### CI-022 — `:app` 收藏文件夹重命名目标冲突

- **检查范围**：一级/二级文件夹重命名、二级编辑框仅显示末级名称并在保存时恢复父路径、已知路径与收藏身份路径的合并校验、UI 保存反馈、Room 文件夹主键约束。
- **问题依据/优先级**：确认 P2 数据一致性风险。重命名策略验证层级并保护收藏文件身份不冲突，但没有拒绝已存在的目标目录或目标目录的隐式父路径。UI 原先仅检查 `dataState.folders`；旧数据若只有收藏分组路径而缺少对应文件夹表行，可能绕过 UI 重名检查，导致路径合并，或撞上 `favorite_folders.name` 主键约束。
- **状态和依赖**：已验证，完成；依赖收藏状态中显式文件夹路径、收藏文件路径身份和 Room 文件夹表。
- **修改摘要**：重命名策略现在拒绝占用的目标路径及其隐式父目录，同时允许同路径不变；UI 用显式文件夹与当前已加载分组路径做前置提示；协调器拒绝时返回失败，弹窗保持打开并提示，而不再无反馈关闭。
- **验证及结果**：新增目标目录/隐式父目录冲突测试，旧规则先失败；策略修复后定向 `FavoriteFolderRenamePolicyTest` 通过，完整 `:app:testBetaDebugUnitTest` 通过。2026-10-22 针对当前工作区强制重跑 `-PenableAppUnitTests=true --offline :app:testBetaDebugUnitTest --tests '*FavoriteFolderRenamePolicyTest' --tests '*ComposeFavoriteTreeTest' --rerun-tasks` 成功，合计 12 项测试、0 失败/错误，并编译 Beta Debug 主源码及测试源码。续查确认弹窗与文件夹行均调用同一父路径组合策略；持久化队列失败会进入共享恢复通道。固定本地脚本强制重跑 `:app:testBetaDebugUnitTest --tests '*FavoritesPartialLoadMutationTest' --tests '*LibraryDataRecoveryTest' --rerun-tasks` 成功，6 项、0 失败/错误。未做设备端旧数据迁移场景测试，也未构建 APK。
- **阻碍/恢复条件/下一步**：本地代码路径未发现新增阻碍；辅助策略与异步写入恢复已有回归覆盖，但没有用设备重放用户操作。若要验证完整弹窗交互，按 CI-079 的测试接缝确认后再补端到端覆盖；该事项不阻塞其他模块。历史页是否扩展至 500 条之外仍待产品决定。

### CI-023 — `:app` 收藏树搜索与分页协调

- **检查范围**：目录树投影、搜索自动展开/手动折叠、Locale 大小写匹配、收藏列表与搜索结果分页游标、页面变更期间的过期请求保护。
- **问题依据/优先级**：确认 P2 多语言搜索错误。ViewModel 将查询按 `Locale.ROOT` 归一化，但树投影用设备默认 Locale 对目录名、收藏名和条码内容转小写；土耳其语环境下查询 `i` 会把 `ITEM` 转成 `ıtem`，匹配结果与输入归一化不一致。分页与树状态其余路径未确认缺陷：分页游标、数据变更代次、搜索独立投影和折叠恢复均有直接测试。
- **状态和依赖**：已验证，完成；依赖统一的 locale-independent 搜索规则与现有游标分页契约。
- **修改摘要**：树投影与搜索自动展开统一使用 `Locale.ROOT`，使目录/文件/条码内容的匹配与输入查询一致，不受设备系统语言影响。
- **验证及结果**：新增土耳其语默认 Locale 回归测试；旧实现失败，修复后通过。完整 `:app:testBetaDebugUnitTest` 成功，覆盖收藏树和 `FavoritesQueryCoordinatorTest` 分页/过期请求场景。未做多语言真机测试。
- **阻碍/恢复条件/下一步**：无外部阻碍。继续检查收藏文件内容加载、历史/收藏条码编辑与删除操作的选择状态和事务反馈；历史 500 条可见上限仍待用户决定。

### CI-024 — `:app` 收藏文件内容缓存的过期写入

- **检查范围**：收藏文件内容异步读取、写入 regular/search 内存缓存、结果页渲染后的有效性检查，以及备份导入替换快照期间的并发读取。
- **问题依据/优先级**：确认 P2 数据一致性竞态。读取完成后到缓存写回之间，目录可能已变化；原实现无条件用旧快照覆盖新组与条码。第一条测试复现版本戳变化时覆盖；进一步测试发现 `savedAt` 是毫秒时间戳，同毫秒变更仍可保持相同值，旧快照仍会覆盖新条码。另，备份导入替换数据库并重载内存快照时未使并发读取失效。
- **状态和依赖**：已验证，完成；以显式的原子变更代次保护异步读取，不依赖时间戳唯一性；依赖所有库变更路径在交接边界推进该代次。
- **修改摘要**：`FavoriteGroupContentCoordinator` 在读取前记录代次，读取返回、缓存写入及结果渲染后分别校验；regular/search 中任一同 ID 快照版本不一致也会拒绝。普通库变更推进代次；收藏保存在异步写入前先失效，导入开始与重新加载完成均使并发读失效。`FavoritesViewModel` 对过期缓存不再交接结果页。
- **验证及结果**：两种过期缓存测试均曾在旧实现上变红，包含同毫秒时间戳碰撞；新增挂起的 repository 读取期间推进代次测试。协调器定向测试及完整 `:app:testBetaDebugUnitTest` 通过；未构建 APK，未做设备测试。
- **阻碍/恢复条件/下一步**：无外部阻碍。已复核历史批量删除/清空、单条条码编辑/删除及持久化失败恢复，进入下一项 `:app` 生命周期与页面状态恢复检查；历史 500 条可见上限仍待用户决定。

### CI-025 — `:app` 历史与单条条码变更的持久化反馈

- **检查范围**：历史批量删除/清空、单条条码编辑/删除、收藏关联同步、写失败恢复与界面状态发布。
- **问题依据/优先级**：未确认新的可证实缺陷。历史删除只清除历史标记并持久化共享快照，不会误删仍收藏的条码；清空使用仓库事务清理历史；单条删除同步移除收藏关联并更新组时间；无效编辑在入库前拒绝；持久化失败通过有缓冲的失败通道触发从数据库恢复共享快照。
- **状态和依赖**：已检查，无需修改；依赖 `:core:data` 历史/收藏事务与写入队列恢复语义（CI-001、CI-005、CI-014）。
- **修改摘要**：无本项产品代码修改。
- **验证及结果**：复核 `HistoryCoordinatorTest`、`BarcodeItemMutationCoordinatorTest` 与全局持久化失败恢复路径；最近完整 `:app:testBetaDebugUnitTest` 全量通过，覆盖历史、单条编辑/删除及失败回滚行为。
- **阻碍/恢复条件/下一步**：无外部阻碍。继续检查 `:app` Activity 重建、进程恢复和页面级暂存状态交接；历史 500 条可见上限仍待用户决定。

### CI-026 — `:app` Activity 重建与页面暂存状态恢复

- **检查范围**：主页面与设置返回路径、生成输入草稿、结果页稳定 ID 快照、相机请求状态、待启动的结果图片文档选择器状态。
- **问题依据/优先级**：未确认新的可证实缺陷。Activity Bundle 只保存有界生成草稿和少量路径/标志；结果页通过条码 ID 从仓库重建并校验缺项；相机和权限请求身份保存在 `SavedStateHandle`，回调按请求身份一次性消费。超限草稿会记录省略原因，不会越界写入 Bundle。
- **状态和依赖**：已检查，无需修改；依赖 Android `SavedStateHandle`/Activity 实例状态、Room 稳定 ID 和现有 Activity Result 协调器。
- **修改摘要**：无本项产品代码修改。
- **验证及结果**：检查 `MainActivity` 保存/恢复路径及 `SavedResultStateTest`、`GenerateEditorInstanceStateTest`、`NavigationStateCoordinatorTest`、`CameraRequestCoordinatorTest`；完整 `:app:testBetaDebugUnitTest` 成功。未做系统杀进程后真机恢复演练。
- **阻碍/恢复条件/下一步**：真机杀进程恢复场景尚未演练，连接设备后按“生成草稿恢复、结果页 ID 恢复、相机请求取消/完成”补验；继续检查 `:app` 启动更新检查与更新安装 Activity 交接。历史 500 条可见上限仍待用户决定。

### CI-027 — `:app` 更新安装权限跳转的进程恢复

- **检查范围**：更新下载完成后的 APK 校验、未知来源安装权限设置跳转、`onResume` 自动继续安装及 `FileProvider` URI 授权。
- **问题依据/优先级**：确认 P2 更新流程中断风险。待安装 APK 路径只保存在普通 `StateFlow`；跳转系统设置期间若 Android 回收应用进程，ViewModel 重建后该路径丢失，用户返回时无法自动继续安装，需要重新下载。
- **状态和依赖**：已修复，待验证；依赖 Android `SavedStateHandle` 状态保存/恢复契约和设备在系统设置期间的真实进程回收行为。
- **修改摘要**：`UpdateCoordinator` 通过 `SavedStateHandle` 保存并恢复待安装路径；取消或一次性消费时同步移除已保存值，避免恢复后重复安装。APK 仍保存在 `cacheDir`；若系统回收缓存，安装入口会检测文件不存在或为空并提示重新下载。
- **验证及结果**：新增协调器重建回归测试，手动把 `SavedStateHandle` 键值复制到新实例，验证待安装路径可恢复且只消费一次；这证明协调器状态映射，不等同于 Android `SavedStateRegistry` 序列化或真实进程恢复。2026-10-22 使用本地验证工具强制重跑 `-PenableAppUnitTests=true --offline :app:testBetaDebugUnitTest --tests '*UpdateCheckStateTest' --rerun-tasks`，`BUILD SUCCESSFUL`。静态确认应用保存状态仅包含字符串、布尔值及受 24 KiB/1,000 行上限约束的字符串列表；`installApkCompose` 对缺失/空 APK 提示“更新文件不存在，请重新下载”。未构建 APK，未做设备端进程回收演练。
- **阻碍/恢复条件/下一步**：需在授权构建并安装后，下载更新、进入未知来源设置、回收应用进程再返回，验证路径及 APK 文件仍可用。本轮直接调用 SDK 内 `adb devices -l`，设备列表为空；等待设备连接后补验。随后继续检查其他尚未覆盖的 `:app` 系统交互。历史 500 条可见上限仍待用户决定。

### CI-028 — `:app` SAF 文件选择器结果交接

- **检查范围**：收藏备份的 `ACTION_CREATE_DOCUMENT`/`ACTION_OPEN_DOCUMENT`、结果图片保存、局域网文件选择器回调、Activity Result 请求身份恢复与取消路径。
- **问题依据/优先级**：未确认新的可证实缺陷。所有系统选择器共用固定注册的 Activity Result launcher，并由 `CameraRequestCoordinator` 按请求 ID 分派；请求身份通过 `SavedStateHandle` 跨 Activity 重建，启动失败时会清除请求；导入设有输入字节上限，导出在 IO 线程执行并清理临时 ZIP，图片保存对空输出流和异常有失败反馈及临时图清理。
- **状态和依赖**：已检查，无需修改；依赖 AndroidX Activity Result 注册顺序、系统 SAF 临时 URI 授权及现有 `CameraRequestCoordinator`。
- **修改摘要**：无产品代码修改。
- **验证及结果**：静态追踪各请求码分派和取消/失败分支；`CameraRequestCoordinatorTest` 已覆盖 Activity 请求身份重建及一次性消费；本轮完整 `:app:testBetaDebugUnitTest` 通过。未做不同 OEM 文档提供器的设备兼容测试。
- **阻碍/恢复条件/下一步**：无当前阻碍；如现场出现特定文件提供器 URI 权限或写入异常，可按该设备日志复现。继续检查 `:app` 其余未覆盖系统能力边界，不重复审查已记录并通过的功能。

### CI-029 — `:app` 组件暴露与明文网络范围

- **检查范围**：应用 Manifest 中 Activity/Provider 暴露、运行权限、FileProvider 授权以及明文 HTTP 的实际调用范围。
- **问题依据/优先级**：没有确认到可直接利用的组件暴露问题：唯一导出的 Activity 是应用启动入口；FileProvider 为 `exported=false` 且仅按 URI 临时授权。发现一个需要产品决策的 P2 安全取舍：`usesCleartextTraffic=true` 对整个进程生效，而局域网分享使用动态 IPv4 的 HTTP。Android Network Security Config 通过主机名规则配置域，没有 RFC1918 子网/CIDR 规则；Android 17 的隐式本机例外只适用于 loopback，不覆盖动态 Wi‑Fi LAN 地址。项目客户端确实以 `HttpURLConnection` 连接 LAN 会话 URL；更新目录和 APK 下载则强制 HTTPS。该配置是平台尽力执行的网络策略，不应被误称为原始 Socket 的硬隔离边界。
- **状态和依赖**：待确认；依赖是否保留现有 HTTP 局域网兼容性，或接受对分享协议/浏览器互通进行架构级改造。
- **修改摘要**：无代码修改。建议当前维持现状并明确记录风险，不在本轮贸然收紧全局明文策略造成局域网分享失效；如要消除全局放行，需单独设计并验证端到端 HTTPS/WSS 及浏览器证书信任方案。
- **验证及结果**：静态检查 Manifest、FileProvider 路径、`LanShareManager` 动态 `http://` 会话地址、`LanShareClient` 的 `HttpURLConnection`、浏览器端按页面协议建立 WS/WSS 的代码及更新 HTTPS 校验；对照 [Android Network Security Config 官方文档](https://developer.android.com/privacy-and-security/security-config) 中的 `domain-config`、loopback 例外和 NetworkSecurityPolicy 说明。官方配置结构没有 CIDR/子网条目，因此“静态配置不能覆盖任意动态 LAN IP”是基于其声明语法的结论。2026-10-22 再次执行 app 全局明文开关静态门禁，确认 `usesCleartextTraffic="true"` 仍开启；通过本地验证工具强制重跑 `LanShareFileIdentityTest` 与 `LanShareSessionTest`，均成功，分别覆盖路由子网同/异网段判断及连接地址语法约束。未运行 release manifest 合并，也未做动态网络安全测试。
- **阻碍/恢复条件/下一步**：需要确认产品优先级：A) 保留 HTTP 局域网分享和浏览器互通，接受平台级全局明文许可，并继续将应用发起的 URL 限制在可信会话/强制 HTTPS 更新路径；B) 单独设计并验证端到端 HTTPS/WSS 与浏览器证书信任，再收紧许可。静态 XML 不能达到“仅放行任意局域网子网”的目标。该决策不阻断其他模块检查；继续检查其他独立的 `:app` 边界。

### CI-030 — `:app` 自动备份数据范围

- **检查范围**：Room 数据库、DataStore 偏好、缓存文件、旧版条码图片及 Beta 调试日志在 Android Auto Backup/设备迁移规则中的实际路径和排除范围。
- **问题依据/优先级**：未确认数据泄漏或用户数据丢失缺陷。Room 数据库位于数据库域，按当前规则会随 Android 备份保留；偏好设置保留；临时图像和 LAN 传输存放在缓存域，系统默认不备份；旧版 `files/barcode-data/images`、LAN 文件及 Beta `files/debug-logs/`、兜底 `files/debug.log` 均有排除规则。用户库可随系统备份迁移与 README/当前规则意图一致。
- **状态和依赖**：已检查，无需修改；依赖 Android Auto Backup 对数据库、文件和缓存域的定义，以及当前 Room/文件存储实现。
- **修改摘要**：无代码修改。保留收藏与历史数据库备份，同时确保调试日志和临时传输附件不进入备份。
- **验证及结果**：对照 `BarcodeDatabase.create`、`SettingsStore`、`LocalBarcodeFileStore`、Beta 日志后端与两份 XML 规则静态核对；未运行设备云备份/换机恢复演练。
- **阻碍/恢复条件/下一步**：用户数据恢复策略没有进一步要求；如要停止云端保存历史/收藏，需要单独确认，因为会改变现有用户数据迁移行为。其余未完成项为未知来源设置期间的真机进程恢复、明文 HTTP 范围取舍及历史列表 500 条上限选择。

### CI-031 — 项目模块清单与持续集成测试门禁

- **检查范围**：Gradle 实际 included modules、进度表是否遗漏模块、PR 架构检查和 `verifyLocal` 覆盖的测试/静态检查任务。
- **问题依据/优先级**：未发现模块清单或测试门禁遗漏。`settings.gradle.kts` 实际包含 `:app`、`:core:domain`、`:core:data`、`:core:lan-share`、`:architecture-tests` 五个模块，进度表已逐一列出；PR workflow 与 `verifyLocal` 都显式覆盖五个模块的测试以及架构依赖检查，`verifyLocal` 另覆盖 beta/official release Kotlin 编译和 lint。
- **状态和依赖**：已检查，无需修改；依赖当前 Gradle included-build 配置、根 `verifyLocal` 任务和 `.github/workflows/architecture-checks.yml`。
- **修改摘要**：无产品代码或 CI 配置修改；本记录补充模块清单与测试门禁的交叉核对证据。
- **验证及结果**：静态比对 `settings.gradle.kts`、`android/build.gradle.kts` 和 PR workflow 的实际 Gradle 命令；本轮通过固定本地验证脚本执行 `verifyLocal -PenableAppUnitTests=true`，全部五模块测试、架构边界、Beta/正式版 Release 编译与 Lint 均通过。
- **阻碍/恢复条件/下一步**：无。继续推进 `:app` 未完成的外部验证和待确认事项；出现新代码变化或测试失败时再复核对应门禁。

### CI-032 — `:app` Compose 页面状态订阅生命周期

- **检查范围**：页面导航、生成、历史、收藏、结果、设置、局域网分享和更新弹窗中的 ViewModel 状态订阅。
- **问题依据/优先级**：未发现页面状态在后台持续高频重组的缺陷。检索到的 Compose ViewModel 状态渲染都使用 `collectAsStateWithLifecycle`，未发现裸 `collectAsState`；页面路由只组合当前对应页面的状态采集。
- **状态和依赖**：已检查，无需修改；依赖 Compose Lifecycle 收集 API 对 `STARTED` 状态的订阅控制和当前页面路由组合方式。
- **修改摘要**：无产品代码修改。
- **验证及结果**：静态检查 `ComposeAppShell`、`ComposePageRoute` 和更新弹窗的 Flow 状态读取；未运行专项 UI 生命周期测试，已有页面/导航单测不替代后台恢复真机验证。
- **阻碍/恢复条件/下一步**：无本项阻碍。继续处理待设备验证和用户待确认事项；后续若增加状态订阅，保持生命周期感知收集。

### CI-033 — 跨模块异步工作与线程资源归属

- **检查范围**：自建 CoroutineScope、Handler、线程池及 LAN WebSocket 写队列的所有者、停止/关闭路径和队列边界。
- **问题依据/优先级**：未确认线程泄漏或脱离所有者的全局协程。条码持久化与设置写队列由 Hilt 单例持有，明确按进程生存以保证离开页面不取消已排队写入；Beta 诊断写入是应用进程单例、单个 daemon worker 且队列有界；LAN server 的心跳/连接状态 executor 在 `stop()` 中取消任务并 `shutdownNow()`，各 WebSocket writer 在连接清理时关闭；结果页亮屏 Handler 在恢复设置及 Activity 销毁时移除回调。
- **状态和依赖**：已检查，无需修改；依赖 Hilt 单例生命周期、现有持久化写队列契约及 LAN server 的显式 stop/peer cleanup。
- **修改摘要**：无产品代码修改。
- **验证及结果**：静态追踪 scope/线程创建点到 provider 和关闭路径；复核已有持久化及 LAN 生命周期测试记录。未做长时间设备后台/反复启动的线程计数压力测试。
- **阻碍/恢复条件/下一步**：长时设备压力验证仍可补做，但静态检查未显示当前泄漏路径；继续推进待设备验证与待用户确认项，不因该缺少压力环境而停止其他工作。

### CI-034 — `:app` 生成提交期间切页导致持久化与共享状态分叉

- **检查范围**：结果图片准备完成后，生成条码经应用级持久化队列插入数据库、写入共享库状态并导航至结果页的取消边界。
- **问题依据/优先级**：确认 P2 一致性问题。`BarcodePersistenceCoordinator` 的写入队列由进程级 scope 所有；Tab 切换会取消 `ResultsViewModel` 中等待队列结果的协程，但不会取消已入队的 Room 写入。实测旧流程可出现数据库已有新历史条码、`LibraryStateStore` 和结果快照均无该条码的状态分叉，当前界面暂时看不到已保存记录。
- **状态和依赖**：已验证，完成；依赖“数据库插入开始后，内存状态提交必须完成；过期 UI 导航可以取消”的边界规则。
- **修改摘要**：生成条码插入开始后，在 `NonCancellable` 区间内完成数据库返回值接收、共享库提交及结果快照更新；若用户在期间切换 Tab 或 ViewModel 被清理，仅抑制过期导航。提交完成前继续保持准备状态，避免同一 ViewModel 接受重复生成请求。
- **验证及结果**：新增 Robolectric 回归测试，真实调用 `ResultsViewModel` 与生产持久化协调器，并用闸门暂停仓库插入。修复前测试稳定失败于“数据库写入结束后共享库仍缺少该条码”；修复后该定向测试通过，完整 `:app:testBetaDebugUnitTest` 通过，差异 `git diff --check` 通过。未构建 APK，未做真机验证。
- **阻碍/恢复条件/下一步**：无本项阻碍。继续按模块清单检查 `:app` 尚未覆盖的高影响调用链；HTTP 明文范围与历史 500 条上限仍为待确认决策，不阻断无关检查。

### CI-035 — `:app` 收藏备份提交后的快照刷新失败

- **检查范围**：共享库启动快照读取失败恢复；收藏备份导入事务提交后的快照刷新与 Activity 层反馈。
- **问题依据/优先级**：确认 P2 恢复缺口。冷启动快照首次读取若临时失败，应用只显示默认页提示，收藏/历史状态维持未就绪直到重启；收藏导入提交后同一读取故障还会被误报成“导入失败”，尽管数据库已写入。
- **状态和依赖**：已验证，完成；依赖 `BarcodePersistenceCoordinator` 的只读 `loadStartupSnapshot`，以及导入 use case 成功返回表示事务已提交。
- **修改摘要**：在持久化协调器中只对启动快照读取重试一次；写队列等待与旧数据迁移仍只执行一次。持续失败时保留异常及第二次错误供诊断；导入调用方将其包装为带已提交数量的异常，明确告知数据库已写入但列表刷新失败。导入事务不会重跑。
- **验证及结果**：新增冷启动瞬时/持续读取失败测试与真实 `FavoritesViewModel` 导入测试。冷启动用例先在旧实现上失败（只读取一次），修复后共享快照定向测试通过；导入测试确认读取恢复且导入事务恰好执行一次，持久失败消息保留数量；完整 `:app:testBetaDebugUnitTest` 通过。`git diff --check` 无空白错误（仅提示其他既有文件的 LF/CRLF 转换）。未构建 APK、未做设备验证、未提交或推送。
- **阻碍/恢复条件/下一步**：持续读取失败仍会保持未就绪，现会显示准确的库数据加载失败提示；仍需用户重启或后续增加明确的页面内重试操作。真机进程回收、全局明文网络策略和历史 500 条上限仍分别待设备验证或产品确认，不阻断其他检查。

### CI-036 — `:app` 启动设置与库数据错误提示被合并标记吞掉

- **检查范围**：`MainActivity` 中设置 DataStore 初始化与收藏/历史启动快照加载的失败顺序、提示内容及一次性提示门闩。
- **问题依据/优先级**：确认 P2 错误反馈缺陷。两个独立读取路径共用 `LibraryDataViewModel.consumeStartupFallbackNotice()`；设置读取先失败时会显示“数据加载失败，已使用默认页面启动”，错误类别不准确，并消费唯一提示资格，使之后真实的收藏/历史加载失败不再提示。
- **状态和依赖**：已验证，完成；依赖设置与库数据初始化分别返回结果，启动数据读取仍由 `LibraryDataCoordinator` 管理。
- **修改摘要**：新增纯 Kotlin `StartupFailureNoticeCoordinator`，分别记录两项启动结果，待两项均完成后最多生成一次准确提示；设置单独失败、库数据单独失败、两者同时失败分别有对应说明。`MainActivity` 仅汇总并显示结果，移除 ViewModel 中与错误来源无关的一次性布尔标记；没有改变读取、持久化或默认状态行为。
- **验证及结果**：新增 5 项协调器测试，覆盖单项/双项失败、结果顺序、并发结果及只提示一次；完整 `:app:testBetaDebugUnitTest` 通过，Activity 接线参与 Beta Kotlin 编译。此前 `:core:data`、`:core:domain`、`:core:lan-share` 测试任务由 Gradle 判定为 `UP-TO-DATE`，架构测试实际执行并通过。未构建 APK、未做设备验证、未提交或推送。
- **阻碍/恢复条件/下一步**：无本项代码阻碍；未在 Android Activity 上做交互式弹窗演练，Activity 接线已由 app Kotlin 编译和单元测试覆盖。继续检查 `:app` 余下的独立状态边界；明文网络范围、历史 500 条上限及更新安装进程回收仍待用户确认或设备验证。

### CI-037 — `:app` 路由状态的冗余转发封装

- **检查范围**：`AppNavigationViewModel`、`AppRouteStateFacade`、`NavigationStateCoordinator` 的调用关系、状态所有权和仓库引用。
- **问题依据/优先级**：确认 P3 维护性问题。`AppRouteStateFacade` 只有 `AppNavigationViewModel` 一个调用方；它不维护额外状态、不做转换或适配，只把两种调用原样转发给 `NavigationStateCoordinator`。ViewModel 已是 Compose shell 与 Activity bridge 的状态入口，保留该 facade 只增加一层跳转，不提供隔离或复用收益。
- **状态和依赖**：已验证，完成；依赖 `NavigationStateCoordinator` 继续唯一负责路由状态变更，ViewModel 对外方法签名保持不变。
- **修改摘要**：`AppNavigationViewModel` 直接持有 `NavigationStateCoordinator`，移除无行为的 `AppRouteStateFacade`；不改路由选择逻辑、公开 ViewModel API 或 Activity/Compose 调用方。
- **验证及结果**：仓库搜索确认已无 `AppRouteStateFacade`/`routeFacade` 引用；完整 `:app:testBetaDebugUnitTest` 和 `:architecture-tests:test` 均通过，覆盖现有导航 coordinator 测试及架构约束；`git diff --check` 通过。未构建 APK、未做设备验证、未提交或推送。
- **阻碍/恢复条件/下一步**：无本项阻碍。继续检查 `:app` 尚未覆盖的独立高影响状态边界；不重复已完成模块检查，待确认的明文网络策略、历史 500 条上限及安装权限进程恢复仍按各自依赖追踪。

### CI-038 — `:app` SAF 文件选择被旧版存储权限拦截

- **检查范围**：局域网分享中的图片图库与普通附件选择；对照 `minSdk 26`、`targetSdk 37`、Manifest 权限上限及 Activity 权限回调。
- **问题依据/优先级**：确认 P2 可用性缺陷。Android 8–12（API 26–32）选择普通附件时，应用先强制请求 `READ_EXTERNAL_STORAGE`，用户拒绝就不启动 `ACTION_OPEN_DOCUMENT`。但系统 Storage Access Framework 由用户选定文件并通过 URI 授权，官方说明此流程不需要系统存储权限；因此旧门槛无谓阻断了本来可用的文件选择。
- **状态和依赖**：已修复，待设备验证；依赖 Android SAF 对 `ACTION_OPEN_DOCUMENT` 结果 URI 的授权，以及旧版图库仍使用 `ACTION_PICK` 的权限要求。
- **修改摘要**：提取 `LanSharePickerPermissionPolicy`，普通文档选择不再要求广泛存储权限，直接打开系统文件选择器；API 26–32 旧版图片图库继续检查 `READ_EXTERNAL_STORAGE`，API 33+ 继续使用系统照片选择器。URI 读取与上传行为未改变；旧版持久授权由 CI-040 单独处理。
- **验证及结果**：新增策略回归测试。旧策略下测试先失败，修复后 API 26–37 文档选择无需存储权限、API 26/32 图库仍需权限、API 33 图库不需旧权限的断言通过；完整 `:app:testBetaDebugUnitTest` 通过。官方依据：[Android SAF 文件访问](https://developer.android.com/training/data-storage/shared/documents-files)明确由用户选文件并授予 URI 访问、不要求系统权限；[最小化权限请求](https://developer.android.com/privacy-and-security/minimize-permission-requests)建议用 SAF 打开文档。未构建 APK、未做真机/不同 OEM 选择器验证、未提交或推送。
- **阻碍/恢复条件/下一步**：安装 Beta 后，在 API 26–32 设备上拒绝/从未授予媒体权限，仍应能从局域网分享页打开系统文档选择器并选文件；随后确认图库选择仍按旧系统权限工作。继续审查 `:app` 其他独立系统交互；不因该设备补验阻塞无关工作。

### CI-039 — `:app` 系统动效与高对比度监听释放

- **检查范围**：`rememberVisualEffectsPolicy` 对系统动画缩放 `ContentObserver`、Animator 动效比例监听、无障碍高对比度监听和 Lifecycle observer 的注册/释放路径。
- **问题依据/优先级**：检查后未发现可证实的资源泄漏或策略不同步问题。所有监听都在同一 `DisposableEffect` 中注册；`onDispose` 对称注销各观察器/监听器，删除 Lifecycle observer，并清除该 effect 独占 Handler 上的待处理回调。页面恢复到前台时会重读系统动画与无障碍设置。
- **状态和依赖**：已检查，无需修改；依赖 Compose `DisposableEffect` 生命周期和 Android observer/listener 对应的注销 API。
- **修改摘要**：无代码修改。
- **验证及结果**：静态逐项比对注册与释放；现有 `VisualEffectsPolicyTest` 覆盖系统/用户减弱动效与高对比度策略合并，但不模拟 Android 系统监听器注册和销毁。未执行系统设置切换的真机压力测试。
- **阻碍/恢复条件/下一步**：无已证实缺陷。真机切换系统动效比例/高对比度时的即时更新仍可随设备验收补测；继续检查 `:app` 其他未覆盖的系统交互，不重复策略解析单测已覆盖的纯逻辑。

### CI-040 — `:app` 局域网附件 URI 授权生命周期

- **检查范围**：`ACTION_OPEN_DOCUMENT` 的 URI 授权申请、附件等待发送与上传生命周期、应用进程重启后的状态恢复及授权释放。
- **问题依据/优先级**：确认 P2 最小权限与残留授权问题。旧流程请求可跨设备重启保留的 URI 授权，但待发送 URI 和上传任务仅存在于内存 ViewModel/`viewModelScope`，进程重启后无法恢复；源码中没有对应释放调用，因此该持久权限没有恢复收益且可能长期残留。
- **状态和依赖**：已修复，待设备验证；依赖系统文件选择器授予临时 URI 读取能力，以及旧版本已有持久授权只用于 LAN 附件这一源码事实（全仓只有该处曾申请持久授权）。
- **修改摘要**：文档选择器不再申请持久授权，选择附件时不再调用 `takePersistableUriPermission`；应用启动时执行一次旧授权迁移清理，仅在全部释放成功后记录完成标记，清理失败则下次进程启动重试，避免之后每次启动清理未来功能创建的授权。
- **验证及结果**：架构契约测试先在旧代码上失败，确认旧持久授权调用存在；新增迁移单测覆盖只释放实际授予的读/写模式、无模式授权跳过及单条失败后继续处理。完整 `:app:testBetaDebugUnitTest`（371 项）与 `:architecture-tests:test` 均通过；Beta 与正式版 Release Kotlin 编译也通过。测试使用 API 35 Robolectric 配置；未构建 APK、未在设备上验证启动迁移或实际文件选择上传。
- **阻碍/恢复条件/下一步**：需在设备上升级安装后，确认系统文档选择器返回的 URI 可完成 LAN 上传，并检查一次性旧授权清理；当前没有设备运行验证证据。继续检查 `:app` 调试日志存储的保留上限、文件轮转及导出一致性；更新安装进程恢复仍待设备演练，明文网络范围与历史 500 条上限继续待确认。

### CI-041 — `:app` Beta 调试日志初始化阻塞首屏线程

- **检查范围**：`MainActivity.onCreate()` 中日志初始化相对 Compose 首屏挂载的时序，以及 Beta 后端目录创建、旧日志合并和过期日志清理的线程归属。
- **问题依据/优先级**：确认 P2 启动响应风险。`MainActivity` 在 `buildComposeShell()` 前同步调用 `DebugLog.initialize()`；Beta 后端原先在调用线程执行目录创建、旧日志读取/原子合并和日期文件扫描。即使旧日志受容量限制，这些磁盘操作也可能延迟首帧。
- **状态和依赖**：已修复，待设备验证；依赖诊断写队列单线程 FIFO 顺序及日志初始化先于后续记录入队。
- **修改摘要**：Beta 初始化仅同步设置日志目录引用，将建目录、旧日志迁移和过期文件清理提交到现有诊断写队列；随后提交的日志仍按 FIFO 在准备任务之后写入，不改变导出格式或日志内容。
- **验证及结果**：新增架构契约测试，旧实现因初始化在调用线程直接做磁盘准备而失败；修复后定向测试通过。完整 `:app:testBetaDebugUnitTest` 与 `:architecture-tests:test` 通过；Beta 与正式版 Release Kotlin 编译也通过。未构建 APK、未用设备测量冷启动首帧耗时。
- **阻碍/恢复条件/下一步**：需要设备上检查 Beta 冷启动和 Activity 重建时日志正常落盘、导出完整，并对比首帧表现；继续检查日志事件是否包含条码正文、用户文件路径或凭据等敏感信息。更新安装进程恢复仍待设备演练，明文网络范围与历史 500 条上限继续待确认。

### CI-042 — 调试日志中的远端 URL 与用户文件名

- **检查范围**：Beta 调试日志所有直接调用点及 LAN 文件收发日志，检查是否记录条码正文、访问凭据、远端 URL 或用户文件名。
- **问题依据/优先级**：确认 P2 隐私暴露风险。更新对话框日志把远端 APK URL 原样写入可导出的诊断日志，URL 查询参数可能携带签名或临时凭据；LAN 客户端上传完成日志写入用户选择的文件名。当前默认 Release URL 不一定带凭据，但日志不应依赖该假设。
- **状态和依赖**：已验证，完成；依赖更新下载继续保留 HTTPS/校验逻辑，LAN 上传协议仍需将文件名发送给接收端，但本地日志不必保留它。
- **修改摘要**：更新日志移除原始 APK URL，保留预期大小及 SHA-256 是否存在；LAN 上传日志移除文件名，保留字节大小。没有改网络请求、文件名传输或更新校验行为。
- **验证及结果**：新增架构隐私契约测试，旧实现先在更新 URL 字段处失败；修复后契约通过。`:app:testBetaDebugUnitTest`、`:core:lan-share:testDebugUnitTest` 与 `:architecture-tests:test` 全部通过；Beta 与正式版 Release Kotlin 编译也通过。直接调用点中未发现条码正文/会话访问令牌被主动拼入日志；错误对象堆栈是否由特定系统提供器附带 URI，仍需按设备日志样本确认。
- **阻碍/恢复条件/下一步**：异常堆栈中的系统生成文本可能随 Android 提供器实现变化，未有实际泄露样本，暂不全面改写异常日志以免削弱排障能力；后续如日志样本显示 URI/路径，应按错误类别做字段级脱敏。继续检查 `:app` 剩余系统交互与生命周期边界；待设备验证和待产品确认项保持独立追踪。

### CI-043 — `:app` 图片解码失败静默结束

- **检查范围**：相机/图库选择后的图片 URI 解码、EXIF 旋转、缩略图回退，以及解码结果为空时的提示和诊断记录。
- **问题依据/优先级**：确认 P2 可诊断性与体验缺口。`decodeRecognitionBitmap()` 用 `runCatching(...).getOrNull()` 吞掉解码异常；当图库 URI 无法读取/解码且没有相机缩略图时，`MainActivity` 的识别协程直接返回，既没有用户提示，也没有日志事件。
- **状态和依赖**：已验证，完成；依赖图片解码失败不改变条码/文本识别算法，已有相机缩略图仍可作为原回退结果。
- **修改摘要**：记录不含 URI、文件名或图片内容的解码异常类型；没有得到可用位图时，记录请求码并提示用户重新选择图片。相机缩略图回退不变。
- **验证及结果**：新增架构回归契约，修复前在缺少解码诊断的断言上失败，修复后通过；完整 `:app:testBetaDebugUnitTest`、`:architecture-tests:test` 和 Beta/正式版 Release Kotlin 编译均通过。未构建 APK、未用设备验证 OEM 图片提供器行为。
- **阻碍/恢复条件/下一步**：设备验证不同相机/图库提供器的损坏图片、撤销 URI 授权和解码失败反馈仍待后续验收。继续检查 `launchExternalActivity` 启动系统选择器失败分支是否记录足够诊断；其余设备待验证和产品待确认事项保持独立追踪。

### CI-044 — `:app` 外部 Activity 启动失败缺少日志

- **检查范围**：统一 `ActivityResultLauncher` 启动入口及应用内所有直接 `startActivity()` 的异常处理、待处理请求清理、用户提示和 Beta 调试日志。
- **问题依据/优先级**：原始 P3 排障信息缺口已修复。追加检查发现相机、图库、SAF 选择器经 `launchExternalActivity()` 统一保护；条码文本/图片分享、收藏备份 chooser、APK 安装和未知来源设置的直接启动也都处在 `runCatching` 或 `try/catch` 中，没有发现未保护、可由常见启动异常直接逃逸的调用。APK 安装异常仍会持久化系统异常消息，单独由 CI-073 追踪。
- **状态和依赖**：已检查，无新增未处理的 Activity 启动崩溃路径；CI-073 的持久化隐私问题不属于本项完成范围。
- **修改摘要**：本次无产品代码修改；补充扩展检查范围及证据。
- **验证及结果**：对 `android/app/src/main/java` 搜索 `startActivity()`、`startActivityForResult()`、`startIntentSenderForResult()` 和 Activity Result launcher；逐一核对 `BarcodeUi.kt`、`ComposeUpdateDialogs.kt`、`FavoriteBackupActivityBridge.kt` 及 `MainActivity.launchExternalActivity()` 的捕获/清理路径。此为静态审查，不替代设备上的 OEM 无匹配 Activity 场景验证。
- **阻碍/恢复条件/下一步**：无新增代码阻碍；真实系统拒绝场景仍待设备验证。继续检查其他不依赖设备或产品选择的 `:app` 边界。

### CI-045 — `:app` SAF 返回成功但没有 URI 时未处理

- **检查范围**：局域网文件上传/下载的 SAF Activity Result 成功、取消及缺少 URI/传输状态分支。
- **问题依据/优先级**：确认 P2 失败反馈与临时状态清理缺口。原实现仅在 `RESULT_OK` 且 `data?.data` 非空时执行后续动作；上传选择器无 URI 时静默结束，下载选择器无 URI 时既无提示也没有清除 `pendingDownloadId`；下载任务已失效时也无明确反馈。
- **状态和依赖**：已验证，完成；依赖现有 SAF 结果契约和 LAN 分享 ViewModel 的待下载 ID 状态。
- **修改摘要**：分别处理上传与下载结果。成功但 URI 缺失时写入不含 URI/文件名的诊断事件并提示重试；下载分支在解释结果前先清除待处理 ID，取消行为不变；若 URI 存在但连接/下载任务已失效，则记录静态事件并提示重新连接。
- **验证及结果**：新增 `MainActivityResultContractTest` 覆盖上传缺失 URI 提示/记录、下载缺失 URI 提示/记录及清除待处理 ID；定向契约先在旧代码上失败，修复后通过。完整 `:app:testBetaDebugUnitTest`、`:architecture-tests:test` 和 Beta/正式版 Release Kotlin 编译通过；`git diff --check` 通过。未构建 APK、未在设备上验证系统文件提供器返回异常结果的行为。
- **阻碍/恢复条件/下一步**：需在设备上验证 SAF 上传选择、下载保存位置、取消和连接已结束场景；继续按模块检查高风险业务边界，设备待验证项与产品待确认项独立追踪。

### CI-046 — `:app` 结果图片保存选择器异常成功时静默丢弃

- **检查范围**：结果图片“保存到文件”临时文件生命周期及 `ACTION_CREATE_DOCUMENT` Activity Result 成功/取消回调。
- **问题依据/优先级**：确认 P3 用户反馈缺口。系统选择器返回 `RESULT_OK` 但没有目标 URI 时，旧回调与取消路径共用静默清理逻辑：暂存图片被删除，用户没有得到保存失败提示，也没有可检索诊断。临时源文件因缓存清理或 Activity 状态恢复丢失时也会静默结束。
- **状态和依赖**：已验证，完成；依赖现有结果图片导出暂存文件及统一 Activity Result 回调。
- **修改摘要**：区分用户取消与异常成功。成功但暂存源文件失效或目标 URI 缺失时，记录不含路径/URI 的静态诊断事件并提示用户重试；图片写入记录开始、完成、取消或失败及同一操作 ID，不记录 URI/路径；暂存文件清理行为保持不变。
- **验证及结果**：`MainActivityResultContractTest.resultImageSaveMissingUriIsNotSilent` 在修复前失败，修复后通过；本轮全量 `verifyLocal -PenableAppUnitTests=true` 通过（191 项任务，27 项执行、164 项 up-to-date），`git diff --check` 通过。未构建 APK、未在设备上触发系统提供器异常结果。
- **阻碍/恢复条件/下一步**：需在设备上验证图片保存到系统文档提供器、取消以及提供器异常返回；继续检查 `:app` 其余尚未覆盖且有明确触发依据的核心流程，不把受产品选择限制的事项当作代码缺陷。

### CI-047 — `:app` 关键结果访问的空值与异常兜底复核

- **检查范围**：`MainActivity` 系统文件选择结果、结果图片渲染，以及编辑菜单的单条批次取值路径；只复核潜在崩溃的非空断言/集合首项。
- **问题依据/优先级**：复核后未确认额外缺陷。文件 URI 的非空断言位于同一结果回调的 `RESULT_OK`、有效暂存文件和非空 URI 条件之后；`barcodeFormats.first()` 使用源码固定且包含 8 项的非空目录；批次 `first()` 仅在 `size == 1` 时调用；渲染位图的 `checkNotNull` 被 `produceState` 的异常回退捕获，并呈现“条码准备失败”状态、记录错误且回收部分已生成位图。
- **状态和依赖**：已检查，无需修改；依赖上述调用点的局部前置条件与结果页异常兜底。
- **修改摘要**：无代码修改。
- **验证及结果**：静态追踪调用前置条件、渲染异常捕获/资源释放；本轮完整 `:app:testBetaDebugUnitTest` 与 `:architecture-tests:test` 通过。该检查不是对所有 Android 系统 Intent 或设备提供器行为的真机验证。
- **阻碍/恢复条件/下一步**：无本项阻碍；SAF 与图片提供器真实返回边界仍需设备验证。继续检查其他模块的高影响业务边界；遇到仅由产品政策决定的历史上限等事项继续待确认，不擅自改变。

### CI-048 — `:app` Beta 日志保留策略在长驻进程中失效

- **检查范围**：Beta 调试日志每日文件轮转上限、过期清理的调用时机、导出时的源文件集合，以及日志队列调度。
- **问题依据/优先级**：确认 P2 私有存储累积风险。每日文件达到 2 MiB 后会轮转保留最近记录，但原先过期清理只在应用初始化和导出快照时执行；进程跨日持续运行且不导出日志时，每天仍会留下新的日志文件，7 天策略不能约束长驻期间的磁盘增长。
- **状态和依赖**：已修复，待设备验证；依赖现有单线程诊断队列、日志写锁和按自然日的文件命名规则。
- **修改摘要**：增加每日清理调度器：启动与导出仍会清理；长驻进程的首条新日期日志也会触发清理，同一自然日后续记录不重复扫描目录。保留原 7 天阈值、每日日志大小与导出格式。
- **验证及结果**：新增日期调度单测覆盖首次清理、同日去重、跨日和日期回拨；架构契约验证 `writeNow` 在日期变化时调用清理并成功后标记日期。契约在旧实现上先失败，修复后通过；完整 `:app:testBetaDebugUnitTest`、`:architecture-tests:test` 及 Beta/正式版 Release Kotlin 编译通过。未做跨午夜长驻设备运行或私有目录磁盘占用测试。
- **阻碍/恢复条件/下一步**：设备安装并连续跨日运行 Beta 后可核验实际旧文件删除与存储上限；不需要该场景也不应阻止其他独立模块审查。继续检查 `:app` 调试日志导出/写入失败回退与隐私边界，已覆盖的日期调度不重复审查，除非相关实现再次变化。

### CI-049 — `:app` 异常堆栈和旧日志可能导出用户 URI/路径

- **检查范围**：Beta 持久日志与兜底日志的 Throwable 序列化、SAF 导入/导出异常、日志迁移和快照导出；追踪到 Android `ContentResolver` 打开 URI 的失败路径，并复核当前各日志调用点的数据字段。
- **问题依据/优先级**：确认 P2 隐私暴露。Android `ContentResolver` 在没有可用提供器时将 URI 拼入 `FileNotFoundException` 消息；旧日志后端把完整异常堆栈写入可导出文件，而收藏备份读写路径将这类异常传给调试日志。因此导出可能包含 `content://` URI、访问参数；历史日志还可能包含旧版本直接记录的更新 URL或上传文件名。复查确认旧格式清理还会漏掉 Android 私有目录路径及带空格路径的后半段。
- **状态和依赖**：已修复，待设备验证；依赖现有 Beta/正式版日志变体边界，正式版仍为空日志实现。
- **修改摘要**：新的异常日志保留异常类名、cause/suppressed 关系与有限堆栈帧，但省略异常消息；Beta 持久、兜底和 Logcat 写出路径统一使用安全摘要。快照导出会清理旧日志中的常见 URI、路径、旧上传文件名及异常消息；迁移旧日志和触发轮转时也先清理历史内容。脱敏规则覆盖 `/data/data/...`、`/data/user_de/<用户编号>/...` 等私有目录；由于旧日志中的无引号 Unix 路径可能含空格，命中已知敏感根目录后会遮蔽该行剩余内容，避免猜错路径终点。调用方原有事件文案及用户可见错误提示不变。
- **验证及结果**：格式器回归测试以含私有 `content://`、token、存储路径和条码正文的异常验证消息省略及堆栈保留；旧日志测试覆盖更新 URL、上传文件名、URI、常见路径、Android 私有目录和带空格文件路径。私有目录和带空格路径用例在旧规则下可复现失败，修复后通过。复核当前日志调用点：未发现原始条码/消息正文/文件名/访问令牌写入日志，LAN 服务启动只记录不含令牌的本地地址，请求错误仅记录剔除 query 的路由。完整 `verifyLocal -PenableAppUnitTests=true` 通过（191 项任务，15 项执行、176 项 up-to-date）。未构建 APK、未用真机导出实际日志抽样。
- **阻碍/恢复条件/下一步**：需对设备上真实导出的日志做隐私抽样，确认历史日志清理覆盖实际格式；继续按模块检查可独立验证的高影响问题，不等待设备条件才推进。

### CI-050 — `:core:lan-share` 当前下载替换与 EXIF 方向改动复核

- **检查范围**：复查工作区已有的 `LanShareDownloadFiles` 下载暂存/替换实现及两处图片预览解码器共用的 EXIF 方向处理；未改动这些既有代码。
- **问题依据/优先级**：当前改动针对两项明确风险：下载替换前删除旧目标会在后续重命名失败时丢失已有文件；旧解码路径只处理旋转，无法呈现 EXIF 镜像/转置方向。新实现先写同目录暂存文件、完成后替换；方向映射包含旋转与镜像组合。
- **状态和依赖**：已检查，无需追加修改；依赖同目录暂存约束和 Android API 26+ 的 `java.nio.file.Files` 支持。
- **修改摘要**：本次复核无额外代码修改；确认测试分别检查替换失败时原文件与暂存文件均保留，以及八种 EXIF 方向值的映射。
- **验证及结果**：`:core:lan-share:testDebugUnitTest` 成功（17 项任务均为 `UP-TO-DATE`，Gradle 输入快照判定当前测试输出有效）；未在设备上模拟磁盘满、文件系统异常或相机生成的镜像 EXIF 图片。
- **阻碍/恢复条件/下一步**：实际设备文件系统替换与预览方向仍可在用户允许构建并安装后补验；继续处理不依赖设备或待确认产品选择的 `:app` 检查。

### CI-051 — `:core:data` 收藏备份 ID 分配溢出

- **检查范围**：收藏备份追加导入时，条码与收藏文件主键的计算、冲突文件跳过顺序，以及提交持久化前的失败边界。
- **问题依据/优先级**：确认 P3 草稿层边界与错误反馈改进，不是已证实的数据损坏。旧逻辑在 ID 空间耗尽时会让中间 `TransferEntities` 草稿 ID 回绕为负数；但生产 `FavoritesBackupUseCase` 随后调用 `RoomBarcodeRepository.commitFavoriteImport()`，该方法在同一 Room 写事务中按数据库最新最大 ID 重新映射草稿 ID，并在插入前拒绝耗尽空间，因此没有证据表明旧实现会把负 ID 持久化。提前校验的实际收益是维持转换层 ID 不回绕，并给出明确的中文上限提示。
- **状态和依赖**：已验证，完成；依赖收藏身份仍按文件夹路径和名称判重、备份内重复收藏保留的既有规则；Room 提交事务的最新 ID 重映射是最终持久化防线。
- **修改摘要**：先按现存收藏身份筛出实际追加项，再分别计算条码/分组的分配数量并校验可用 ID 区间；使用有界偏移生成草稿 ID，拒绝溢出，同时允许恰好使用 `Long.MAX_VALUE` 作为最后一个合法 ID。没有可追加收藏时不因 ID 空间耗尽而拒绝仅包含文件夹的导入。持久化 ID 仍由 Room 提交事务基于最新数据库状态决定。
- **验证及结果**：新增条码 ID 与收藏文件 ID 溢出回归测试，并确认最后一个可用 ID 仍可分配。两个溢出用例先在旧实现上失败，修复后定向 `FavoritesTransferManagerTest` 全部通过；完整 `-PenableAppUnitTests=true :core:data:testDebugUnitTest :architecture-tests:test :app:testBetaDebugUnitTest` 成功（91 项任务，9 项执行、82 项 up-to-date）；`git diff --check` 通过。2026-10-22 针对当前工作区版本再用固定脚本强制重跑 `:core:data:testDebugUnitTest --tests '*FavoritesTransferManagerTest' --rerun-tasks`，18 项测试、0 失败/错误。后续 CI-053 增加 Room 级陈旧草稿 ID 重映射回归验证。
- **阻碍/恢复条件/下一步**：无本项外部阻碍。没有构建 APK 或设备验证需求；继续检查 `:app` 中尚未覆盖、且不依赖待确认产品选择或真机条件的系统交互/恢复路径。

### CI-052 — `:app` 生成与设置控件的可访问语义

- **检查范围**：生成页格式选择、设置页下拉项/开关/滑块、底部 Tab 的名称、状态和辅助操作语义；仅检查这组主要控件，不代表全应用无障碍验收。
- **问题依据/优先级**：未确认可证实缺陷。格式入口和设置下拉按钮提供控件名称及展开状态；滑块提供标题和当前值；开关语义只绑定实际开关触控区，内部视觉轨道清除重复语义；Tab 提供名称、选中状态和长按快捷操作，图标作为装饰子项不重复播报。
- **状态和依赖**：已检查，无需修改；依赖 Compose 合并语义、开关 `toggleable` 角色及 Material Slider 的原生调整语义。
- **修改摘要**：无产品代码修改。
- **验证及结果**：静态追踪上述语义修饰符及父子节点；`-PenableAppUnitTests=true :app:testBetaDebugUnitTest --tests '*SettingsSwitchTargetTest' --tests '*TabBarInteractionTest'` 成功（73 项任务，1 项执行、72 项 up-to-date）。该测试验证开关标签/颜色规则及 Tab 交互，不等同于 TalkBack 真机验收。
- **阻碍/恢复条件/下一步**：无本项阻碍；若后续开展系统级无障碍验收，仍需使用 TalkBack/辅助功能服务验证焦点顺序、实际播报与字号放大。继续检查其他尚未覆盖且可由源码/自动化验证的 `:app` 风险；更新安装回收、SAF/OEM 提供器和真实日志样本等设备验证仍待具备条件后补验。

### CI-053 — `:core:data` 收藏导入事务中的陈旧 ID 重映射

- **检查范围**：备份导入规划/快照准备与 `RoomBarcodeRepository.commitFavoriteImport()` 事务提交之间的 ID 时序；确认其他写入先落库时会否碰撞或覆盖现有条码/收藏文件。
- **问题依据/优先级**：生产实现已有显式并发防线，但原 Room 集成测试没有直接断言：导入快照携带与现存记录相同的 ID 时，提交仍保留原记录，并为新增记录及其关联重新分配 ID。这是 P2 数据完整性不变量的测试覆盖缺口，未发现当前生产缺陷。
- **状态和依赖**：已验证，完成；依赖 Room `withTransaction` 写事务、`allocateImportedSnapshotIds()` 在事务内读取最新最大 ID，并同步重映射条码、分组及关联 ID。
- **修改摘要**：增加 Room Robolectric 集成测试，模拟导入规划后出现另一写入形成陈旧草稿 ID；断言原记录保留、新记录分配新 ID，且关联表正确指向新 ID。未改生产逻辑。
- **验证及结果**：新增 `favoriteImportRebasesStaleDraftIdsAgainstDatabaseInsideCommitTransaction`，断言陈旧条码/分组 ID 在事务提交时重新分配，原记录及关联保持正确。Windows 本机默认 `%TEMP%` 下强制重跑迁移测试类会复现 `SQLiteCantOpenDatabaseException`（12 项中 1 项失败）；同一测试类指定短临时目录 `D:\Barcode_build\t` 后通过，随后完整 `:core:data:testDebugUnitTest` 在同一短目录设置下通过。结果表明这是当前本机 Robolectric/SQLite 临时路径条件，不是 ID 断言失败；未为本机绝对路径修改项目 Gradle 配置或生产代码。`git diff --check` 通过（仅有若干既有文件 LF/CRLF 提示）。
- **阻碍/恢复条件/下一步**：此 Windows 环境使用默认临时目录时测试不可重复通过；本机验证需在启动 Gradle/测试 worker 前设置 `JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=D:\Barcode_build\t`，并提供现有 Android SDK 环境变量。跨平台 CI 是否也需要该设置，当前无证据，不做推断。继续检查尚未覆盖且可由源码/自动化验证的 `:app` 风险；需设备或产品决策的事项继续独立保留。

### CI-054 — `:app` 启动失败通知汇总与生命周期取消

- **检查范围**：设置和收藏/历史启动加载结果的合并顺序、并发一次性通知，以及 Activity 生命周期取消是否会被归类成真实加载故障。
- **问题依据/优先级**：未确认缺陷。`StartupFailureNoticeCoordinator` 只在两类结果都到达后生成一次对应提示，并用同步保护并发回调；`MainActivity` 在通用异常处理前单独重新抛出 `CancellationException`，避免 Activity 销毁/重建时把协程取消记录成启动故障。UI 初始化分支为同步平台操作，没有吞掉挂起调用的取消信号。
- **状态和依赖**：已检查，无需修改；依赖 Activity `lifecycleScope` 取消语义及独立的设置/库数据加载结果。
- **修改摘要**：无产品代码修改。
- **验证及结果**：`-PenableAppUnitTests=true :app:testBetaDebugUnitTest --tests '*StartupFailureNoticeCoordinatorTest'` 通过；测试覆盖单项失败、双项失败、结果先后顺序和并发最多提示一次。随后完整 `:app:testBetaDebugUnitTest` 成功（6 项执行、67 项 up-to-date）。该测试验证纯协调逻辑，未模拟真实 Activity 销毁/重建。
- **阻碍/恢复条件/下一步**：无本项代码阻碍；生命周期真机恢复演练仍保留为设备验证项。继续检查其他独立的源码/自动化范围；未决产品策略和设备验证继续单独追踪。

### CI-055 — `:app` 持久化失败后的恢复吞掉协程取消

- **检查范围**：`LibraryDataViewModel` 消费持久化失败事件后重新加载快照的挂起恢复调用与诊断日志。
- **问题依据/优先级**：确认 P3 诊断准确性缺陷。旧 `runCatching { coordinator.loadPersistedData() }` 会捕获 `CancellationException`；当 ViewModel 生命周期取消恢复任务时，日志仍记录为“写入失败后的重载失败”，污染 Beta 可导出的调试证据。该路径不改变已提交数据库数据。
- **状态和依赖**：已验证，完成；依赖 `viewModelScope` 生命周期取消与 `AppLogger` 的持久诊断语义。
- **修改摘要**：将恢复边界提取为内部 suspend helper，优先原样重抛 `CancellationException`，普通恢复异常仍调用原日志回调；恢复时机、重试次数和持久化策略不变。
- **验证及结果**：新增两项回归测试，分别验证取消原样传播且不记录失败、普通异常仍报告一次。取消断言在修复前失败，修复后通过；完整 `-PenableAppUnitTests=true :app:testBetaDebugUnitTest` 成功（6 项执行、67 项 up-to-date）。`git diff --check` 通过（仅有若干既有文件 LF/CRLF 提示）。
- **阻碍/恢复条件/下一步**：无本项阻碍；继续检查 `:app` 中未覆盖且可自动验证的独立边界，设备验证和产品待确认项不阻断代码检查。

### CI-056 — `:app` 取消收藏备份分享后残留部分 ZIP

- **检查范围**：收藏备份“准备分享”时写入缓存 ZIP、协程取消后的清理和取消传播。
- **问题依据/优先级**：确认 P3 临时数据清理/隐私风险。旧代码用 `runCatching` 把 ZIP 写入过程中的 `CancellationException` 转成失败结果；取消后的 `withContext(Dispatchers.Main)` 不会进入 `onFailure` 清理回调，因此可能把未完成的收藏备份留在应用缓存。
- **状态和依赖**：已验证，完成；依赖 Activity `lifecycleScope` 的取消传播和分享 ZIP 的私有缓存生命周期。
- **修改摘要**：提取分享备份准备边界；取消时先删除可能不完整的 ZIP，再原样重抛取消异常。普通失败仍沿用原有诊断、提示和清理路径，未改变导出内容或分享流程。
- **验证及结果**：新增临时文件回归测试，模拟只写入部分内容后取消，断言文件被删除且同一取消异常继续传播；定向测试通过。包含后续备份文件名碰撞测试的完整 `-PenableAppUnitTests=true :app:testBetaDebugUnitTest` 成功（测试任务执行，72 项其他任务为 up-to-date）。最终 `git diff --check` 复查通过（仅有既有 LF/CRLF 提示；见 CI-058）。
- **阻碍/恢复条件/下一步**：无本项阻碍；继续检查 `:app` 中其他独立且有明确收益的边界，不重复已验证路径；待设备验证和产品决策仍单独追踪。

### CI-057 — `:app` 同秒启动收藏备份分享会复用缓存 ZIP 名称

- **检查范围**：收藏备份分享文件名生成精度、重复触发方式及缓存文件打开/覆盖行为。
- **问题依据/优先级**：确认 P2 导出完整性竞态。分享文件名只含秒级时间戳，导出在后台执行且入口无进行中门闩；用户可在前一次准备完成前重新打开备份菜单再次分享。同名 `File(cacheDir, name)` 会被两个 `outputStream()` 截断并并发写入，可能生成损坏的分享备份。
- **状态和依赖**：已验证，完成；依赖同一时间戳内允许重复启动分享任务，且文件写入操作并发。
- **修改摘要**：缓存文件以 `createNewFile()` 原子预留名称；首个导出保留原名，名称冲突时追加递增序号，不覆盖已存在的备份文件。分享标题和正常导出命名保持不变。
- **验证及结果**：回归测试用相同请求名连续创建两份备份，验证路径不同、首份内容不变且新内容写入独立文件；与取消清理用例一同通过。最终完整 `-PenableAppUnitTests=true :app:testBetaDebugUnitTest` 成功（测试任务执行，72 项其他任务为 up-to-date）。最终 `git diff --check` 复查通过（仅有既有 LF/CRLF 提示；见 CI-058）。
- **阻碍/恢复条件/下一步**：无本项阻碍；继续检查其他未覆盖且可自动验证的 `:app` 边界，保留设备验证和产品决策待办。

### CI-058 — `:app` 同秒导出调试日志会覆盖快照

- **检查范围**：Beta 调试日志快照、正式版故障兜底快照的缓存文件命名、并发写入与失败清理；复核导出前日志队列屏障。
- **问题依据/优先级**：确认 P2 诊断完整性竞态。Beta 与兜底快照均以秒级时间戳生成同一路径；分享入口没有进行中门闩且在 `Dispatchers.IO` 执行，重复触发可并行对同一文件 `writeText`，造成两个导出互相截断或覆盖。此前的 `DiagnosticWriteQueue.flush()` 会等待屏障之前已排队日志，本轮未发现该屏障会漏掉先前提交的记录。
- **状态和依赖**：已验证，完成；依赖 `File.createNewFile()` 在同一缓存目录中的原子独占创建。
- **修改摘要**：新增日志快照文件名原子预留函数；首份保留原时间戳名称，冲突时递增 `-1`、`-2` 后缀，Beta 正常导出与通用兜底导出共用。快照内容写出失败时删除未完成文件并继续原有错误回退。
- **验证及结果**：唯一命名测试先因未实现 helper 无法编译，接入实现后测试又捕获到扩展名后缀拆分错误；修正后定向 `LogExportFileReservationTest` 通过，确认重复请求路径不同、首份内容不变、扩展名正确。架构契约同时检查 Beta 与兜底路径接入原子预留。完整 `-PenableAppUnitTests=true :app:testBetaDebugUnitTest :architecture-tests:test :app:compileBetaReleaseKotlin :app:compileOfficialReleaseKotlin` 成功（109 项任务，8 项执行、1 项缓存、100 项 up-to-date）。最终 `git diff --check` 通过（仅既有 LF/CRLF 提示）。
- **阻碍/恢复条件/下一步**：无本项阻碍；继续检查 `:app` 中其他尚未覆盖且可自动验证的高影响边界，保留设备/产品待确认项；本轮未构建 APK、未安装、未提交或推送。

### CI-059 — `:core:data` APK 更新身份策略的行为测试

- **检查范围**：`AndroidApkValidationGateway` 对 APK 包名、版本递增、下载 APK 签名证书集合与当前安装包证书集合的判定；区分 Android `PackageManager` 数据提取和纯策略判定。
- **问题依据/优先级**：确认 P2 安全回归覆盖缺口，而非已证实的生产绕过。CI-002 已静态检查更新校验，但没有独立测试锁定包名/版本/签名拒绝条件；这使后续改动可能误放行错包、非递增版本或不同签名 APK。
- **状态和依赖**：纯策略已修复覆盖并验证；Android `PackageManager` 对真实 APK 档案与已安装包签名的提取仍待平台级验证。
- **修改摘要**：将包名、版本递增和签名集合相等的纯决策提取为内部函数供适配器调用；保留 API 28 前后的 `PackageManager` 标志与签名读取分支、现有异常消息及错包时提前拒绝行为。没有改变更新公开接口或接受条件。
- **验证及结果**：新增测试覆盖更高版本且同一多签名集合（顺序无关）接受、包名不符拒绝、相同/更低版本拒绝、下载/已安装签名缺失或集合不一致拒绝。测试先因策略函数缺失而编译失败；实现后定向测试通过。完整 `:core:data:testDebugUnitTest` 与 `:app:compileBetaReleaseKotlin :app:compileOfficialReleaseKotlin` 成功（67 项 Gradle 任务，13 项执行、54 项 up-to-date）。未构建 APK、未在设备/系统 PackageManager 上解析真实 APK。
- **阻碍/恢复条件/下一步**：真实签名档案读取需用签名 APK 夹具做 Robolectric/Android 集成验证，或在授权构建后用设备验证；当前环境没有连接设备，且目标未授权 APK 构建/部署。继续其他不依赖此条件的检查。

### CI-060 — `:core:data` 进程中断后的更新临时包清理

- **检查范围**：APK 下载临时文件命名、并发下载期间的清理保护、下载异常/取消的 `finally` 清理，以及完成 APK 的待安装恢复用途。
- **问题依据/优先级**：确认 P3 缓存空间回收缺口。正常异常和协程取消会执行 `finally` 删除 `.apk.part`；但进程被系统直接终止时不会执行 `finally`。下载器为每次请求生成唯一临时文件，且未发现其他孤儿清理入口，重复中断可能在 `cacheDir` 留下最多 500 MiB 的部分包。完成的 `.apk` 则供未知来源安装设置返回后恢复，不能纳入清理。
- **状态和依赖**：已验证，完成；依赖更新下载网关的 Hilt 单例作用域，用共享活动文件集合保护同一进程并发下载。
- **修改摘要**：每次开始更新下载时清理本应用命名空间下、未被当前进程下载占用的 `.apk.part`；活动临时文件由并发安全集合保护。清理仅匹配专用前缀和 `.apk.part` 后缀，绝不触碰完整 `.apk` 或其他缓存文件；清理数量写入更新诊断日志，不记录路径。
- **验证及结果**：新增文件系统回归测试，先在空实现上因孤儿文件仍存在而失败；实现后通过，并验证活动下载文件、已完成 APK、其他来源 `.part` 与相似扩展名均保留。完整 `:core:data:testDebugUnitTest :app:compileBetaReleaseKotlin :app:compileOfficialReleaseKotlin` 成功（67 项任务，7 项执行、1 项缓存、59 项 up-to-date）。未构建 APK、未安装、未提交或推送；没有设备可用于真实进程终止演练。
- **阻碍/恢复条件/下一步**：无代码验证阻碍；设备侧强制结束下载进程后重新启动/重试的演练待真机可用时补验。继续推进独立的 `:app` 或其他模块检查，不重复已完成的更新策略测试。

### CI-061 — `:app` 挂起调用周边的协程取消传播复核

- **检查范围**：筛查 Presentation 与 Activity 桥接中 `runCatching` 包裹挂起调用的路径，重点追踪局域网文件刷新/预览、收藏备份导入导出和持久化写队列。
- **问题依据/优先级**：未发现新的已证实缺陷。局域网刷新在结果返回后立即 `ensureActive()`；预览抓取在每个文件前检查取消，外层在合并状态前再次检查。收藏备份若挂起调用抛出取消，当前 lambda 后续语句不会继续执行，生命周期切回主线程也受取消检查保护。持久化队列将单次写入失败封装为 `Result` 是隔离独立写入的既有契约，观察任务仍由其所属协程作用域控制。
- **状态和依赖**：已检查，无需修改；依赖 Kotlin 协程取消语义及当前局域网刷新代次校验。
- **修改摘要**：无代码修改；没有把静态搜索到的每个 `runCatching` 机械改写，也未新增仅验证语言默认异常行为的测试。
- **验证及结果**：静态追踪所有相关挂起调用及取消检查点；未发现取消后继续写状态、错误上报或跳过资源清理的未防护路径。无行为改动，因此未运行测试。
- **阻碍/恢复条件/下一步**：无本项阻碍。继续检查尚未覆盖且可由当前环境验证的 `:app` 系统边界；待设备验证和产品决策继续独立追踪。

### CI-062 — `:app` SAF 局域网下载失败留下空/部分目标文档

- **检查范围**：局域网文件保存选择器返回 URI 后的网络暂存、复制到 `ContentResolver` 输出流、失败提示及缓存临时文件清理。
- **问题依据/优先级**：确认 P2 文件完整性/结果清理缺口。URI 来自 `ACTION_CREATE_DOCUMENT`，用户确认保存位置后系统提供新目标文档；网络下载先写应用缓存临时文件，再复制到该 URI。网络暂存创建/下载失败、输出流拒绝写入或复制中断时，旧代码只删除缓存文件并显示失败提示，未撤销新建目标，可能让空文件或部分文件留在用户选择的位置。
- **状态和依赖**：已验证，完成；依赖 SAF `ACTION_CREATE_DOCUMENT` 返回本次新建文档 URI，以及 `ContentResolver.delete()` 的提供器行为。
- **修改摘要**：新增失败回滚边界，下载暂存与目标复制任一环节失败时尝试删除本次新建文档；删除返回未确认时记一条不含 URI 的安全诊断。若删除抛错，将其作为 suppressed 信息附加到原传输异常，不覆盖原错误。缓存临时文件仍由 `finally` 删除，成功文件不回滚。
- **验证及结果**：测试在目标文档写入部分字节后模拟磁盘错误，旧无回滚实现先失败，修复后确认目标与缓存临时文件都被清理且原异常对象保留；另外覆盖清理失败时原始传输错误优先、成功路径不执行回滚。完整 `-PenableAppUnitTests=true :app:testBetaDebugUnitTest :app:compileBetaReleaseKotlin :app:compileOfficialReleaseKotlin` 成功（106 项任务，8 项执行、98 项 up-to-date），3 项新测试全部通过。未构建 APK、未连接设备验证不同 OEM 文档提供器的删除行为。
- **阻碍/恢复条件/下一步**：无本地验证阻碍；设备可用时补验常见系统文件选择器下，失败传输会清除新目标、成功传输保留完整文件。继续其他不依赖设备/产品决策的 `:app` 检查。

### CI-063 — `:app` SAF 返回时局域网会话已失效留下空文档

- **检查范围**：`ACTION_CREATE_DOCUMENT` 返回成功后，待下载 ID 与局域网会话在系统选择器期间被清除/进程恢复时的 Activity Result 处理。
- **问题依据/优先级**：确认 P3 空文件清理缺口。目标 URI 已由系统文档选择器新建，但 `pendingDownloadId` 或 session 可能在用户停留于选择器期间因 Activity/进程恢复或分享会话结束而丢失；旧分支只提示“分享连接已结束”并返回，空目标文件会留在用户位置。
- **状态和依赖**：已验证，完成；依赖该分支只在成功选择 URI 后执行，且 URI 来自 `ACTION_CREATE_DOCUMENT`。
- **修改摘要**：发现无有效传输上下文时尝试删除新建目标；删除异常或提供器未确认删除时，仅记录异常类型/结果，不记录 URI，并保留原提示文案。
- **验证及结果**：新增 `MainActivityResultContractTest` 源码契约；旧实现先因缺少 `ContentResolver.delete(uri, null, null)` 断言失败，修复后通过。完整 `-PenableAppUnitTests=true :architecture-tests:test :app:testBetaDebugUnitTest :app:compileBetaReleaseKotlin :app:compileOfficialReleaseKotlin` 成功（109 项任务，15 项执行、94 项 up-to-date）。未构建 APK，也未在真实 SAF 提供器上验证删除结果。
- **阻碍/恢复条件/下一步**：无本地代码验证阻碍；OEM/云盘文档提供器拒绝删除时会保留空文件并写安全诊断，需设备验收常见系统选择器。继续检查独立的应用系统边界。

### CI-064 — `:core:data` APK 签名元数据适配器的 Robolectric 覆盖边界

- **检查范围**：`AndroidApkValidationGateway.validate()` 中 `PackageManager.getPackageArchiveInfo()` 与 `getPackageInfo()` 的签名/版本元数据读取分支；不重复 CI-059 已完成的纯身份策略测试。
- **问题依据/优先级**：确认 P2 平台适配器测试覆盖缺口；当前回归测试只调用纯策略函数，不能捕获 Android API 分支或 PackageManager 元数据缺失。仓库已使用 Robolectric 4.17，官方测试 API 提供按路径注入 APK `PackageInfo` 的 seam；Android `SigningInfo` 在 API 35 有可构造签名信息的公开构造器。
- **状态和依赖**：待确认。按 `tdd` 技能的 seam 约定，已向用户请求是否在现有 `AndroidApkValidationGateway.validate()` 端口增加 Robolectric 集成测试。补充只读核查确认该类已公开实现 `ApkValidationGateway`，无需增加生产接口；不等待确认期间继续其他独立检查。
- **修改摘要**：尚未修改代码或测试。
- **验证及结果**：只读核对适配器与 `AndroidApkValidationPolicyTest`：现有测试只覆盖 `validateApkUpdateIdentity()`，未触发 `PackageManager.getPackageArchiveInfo()`、API 28 前后签名来源选择或已安装包证书读取。适配器已有公开端口可作为测试候选；未确认测试边界前没有改代码或测试，也未运行新增测试。
- **阻碍/恢复条件/下一步**：等待用户选择该测试边界；即使 Robolectric 覆盖通过，真实 APK 签名解析和设备 PackageManager 行为仍需分开说明。本轮 SDK 内 `adb devices -l` 结果为空，暂时不能做设备侧复验。

### CI-065 — `:core:data` 更新临时包清理与并发下载登记顺序

- **检查范围**：`AndroidApkDownloadGateway.download()` 创建临时路径、登记活动文件、清理孤儿文件和 `finally` 释放登记的时序；Hilt 注入作用域与应用进程声明。
- **问题依据/优先级**：检查潜在 P2 文件完整性风险；重点验证并发请求启动时，孤儿清理是否可能把另一条正在创建的下载误判为孤儿并删除。
- **状态和依赖**：已检查，无需修改。该网关由单例注入；未发现额外 Android 进程声明。
- **修改摘要**：无代码修改。每次下载在执行目录扫描清理前先将唯一 UUID 临时路径加入并发安全的活动文件集合；其他并发下载也先登记，因此清理会跳过正在活动的路径。下载结束在 `finally` 清理临时文件并移除登记；完成包为不同 `.apk` 后缀，不匹配清理条件。
- **验证及结果**：只读追踪创建/登记/清理/释放时序，并核对 CI-060 已通过的文件系统测试覆盖活动文件、孤儿文件、完成 APK 和非本应用文件；本次未重复运行测试。
- **阻碍/恢复条件/下一步**：当前证明范围是同一应用进程内的并发下载；未发现多进程使用此网关的 manifest 配置。若未来加入独立进程更新服务，需要重新评估进程间锁/原子文件所有权。

### CI-066 — `:core:data` APK 更新签名证书轮换兼容性

- **检查范围**：下载 APK 与已安装应用的签名判定，以及正式版/测试版签名密钥轮换对应用内更新的影响。
- **问题依据/优先级**：确认 P3 兼容性限制，不是当前崩溃或签名绕过。适配器读取 API 28+ 的 `SigningInfo.apkContentsSigners`，策略要求下载与已安装证书集合完全相等；Android 文档区分当前 APK 内容签名者与证书轮换历史，并指出历史可通过经平台验证的轮换证明授权新证书。因此目前实现会在进入系统安装器前拒绝有效轮换后的更新包。
- **状态和依赖**：待确认；依赖产品是否计划更换/轮换应用签名密钥。当前仓库未发现用户已授权轮换密钥或改变更新信任策略。
- **修改摘要**：无代码修改。若长期固定 `barcode-release.jks`，建议保持严格相等并将“不能直接换签名密钥”列为发布运维约束；若计划采用 Android 证书轮换，需单独设计单签名 lineage 与多签名集合判定、跨 API 回归测试，并仍由系统安装器做最终验证。
- **验证及结果**：只读核对适配器和身份策略；依据 [Android `SigningInfo` 文档](https://developer.android.com/reference/android/content/pm/SigningInfo) 区分当前签名与历史签名。未构造轮换 APK，也未改动安全接受条件。
- **阻碍/恢复条件/下一步**：请在需要签名密钥轮换时再确认目标策略；当前按原严格签名集合校验继续其他独立检查，不把未来限制冒充为现有故障。

### CI-067 — GitHub Actions 第三方依赖固定与 Beta 发布隔离

- **检查范围**：架构检查、Beta、正式版三个 workflow 的第三方 Action 引用；Beta 构建/签名与 Release 创建、元数据刷新、产物交接及临时签名文件清理。
- **问题依据/优先级**：确认第三方 Action 使用可移动版本标签存在供应链风险；并确认一次实际 Beta 门禁失败是有效捕获架构违规，而非分支竞态：`78cd965` 的生成 UI 导入 `presentation.generate.BatchBarcodeTextParser`，导致两个架构测试失败。后续 `bb7a34b` 将解析器移入 `ui.feature.generate` 并移除反向依赖。另核实 `contents: read` 本身不等于显式 PAT 无写权限，不能据此推断发布故障。
- **状态和依赖**：SHA 固定与 build/publish 双 job 改动已推送到 `origin/beta`；现有远端成功记录对应旧的单 job 流程，不能验证当前双 job 交接。等待当前 `origin/beta` HEAD 的新远端 Beta workflow run 验证。
- **修改摘要**：三个 workflow 将第三方 Actions 固定到完整 commit SHA；Beta 拆分签名构建与 Release 发布 job，并用 artifact 和 job outputs 交接 APK、版本及校验值；发布 job 使用显式 `RELEASE_PUBLISH_TOKEN`，`GITHUB_TOKEN` 保持只读，签名文件在 build runner 清理。以上改动已包含在远端 Beta 分支，不是未提交工作树状态。
- **验证及结果**：只读核对远端运行 `39563328301` 在 `:architecture-tests:test` 中有两项失败，具体为生成 UI 对 presentation/app composition 的依赖边界；随后提交 `bb7a34b` 修复依赖方向。远端运行 `39564048987`（SHA 与当时 HEAD 一致）完整成功，`verifyLocal`、Beta APK 构建、Release 更新和临时 keystore 清理各步均成功。远端 Release `android-test-v9.9.9` 为已发布状态，`versionName=9.9.9`、`versionCode=4`；Release 正文 SHA-256 与 GitHub APK asset digest 一致，附件状态为 uploaded。未下载 APK，故未独立重算安装包字节摘要。该远端成功运行使用旧的单 job workflow；当前 `origin/beta` 的双 job 和 SHA 固定改动尚无远端执行证据。
- **本地补充核验**：本次逐条检查三个 workflow 的 14 个外部 `uses:` 引用，全部为 40 位 commit SHA 且附版本注释；与各 Action 官方 release 页核对时，checkout、setup-java、setup-gradle、upload-artifact、download-artifact 的 SHA 前缀与相应版本标签匹配并显示已验证签名。`softprops/action-gh-release` 的固定 SHA 前缀与官方仓库 v3.0.3 发布记录一致。该项只证明当前引用来源/格式，不替代推送后的托管端运行验证。
- **阻碍/恢复条件/下一步**：需取得当前 Beta workflow run 的托管端执行结果，验证 build/publish artifact 交接及 SHA 固定 Action 的实际运行；与 CI-163 共用下一次远端验证。继续其他本地审查。

### CI-068 — `:app` 结果页四个操作的语义与回调隔离

- **检查范围**：结果页编辑、收藏、分享、保存按钮的 Compose 语义、点击回调、导出忙碌状态及对应 UI 回归测试。
- **问题依据/优先级**：针对核心操作可能串线或 TalkBack 无法识别的线索做定向检查；未发现已证实缺陷。四个操作各自有独立 key 与回调；共享玻璃按钮组件提供 Button 角色和调用方传入的内容描述；只有分享/保存共用导出互斥状态。
- **状态和依赖**：已检查，无需修改；设备级 TalkBack 播报与焦点顺序不在本次源码/Compose 测试证明范围内。
- **修改摘要**：无产品代码修改。
- **验证及结果**：核对 `ResultsContent.kt`、`GlassRoundActionButton.kt` 及 `ResultToolbarInteractionTest`；现有浅色/深色测试断言四个操作各自调用正确回调，并断言导出期间对应操作不可用。本次未重跑该测试，也未做 TalkBack 真机验收。
- **阻碍/恢复条件/下一步**：若后续发现实际播报或焦点顺序问题，在设备上按 TalkBack 复验并补充 Compose 语义断言；继续检查 `:app` 中尚未覆盖且可本地验证的边界。

### CI-069 — `:core:lan-share` Android 客户端上传取消与部分文件清理

- **检查范围**：Android `LanShareClient.upload()` / `cancelUpload()` 的真实 HTTP 连接取消，并追踪到服务端临时文件提交与失败清理。
- **问题依据/优先级**：发现 P2 核心传输取消路径的集成测试缺口，而非已确认生产故障。原有服务端测试覆盖断开连接后的部分上传清理，浏览器取消测试仅检查脚本结构；Android 客户端此前没有真实请求级取消回归测试。
- **状态和依赖**：已验证，完成；测试使用 loopback HTTP 服务，不依赖手机、Wi-Fi 或远端环境。
- **修改摘要**：新增 `LanShareClientUploadCancellationTest`：通过实际客户端流式发送 4 MiB 文件，在服务端已暂存部分内容时暂停输入，调用客户端取消，再恢复输入；断言上传不报告成功且服务端不残留暂存或已提交文件。未修改生产代码或现有用户改动。
- **验证及结果**：`JAVA_HOME=D:\Java17`、`ANDROID_HOME=D:\Android\Sdk`、临时目录 `D:\Barcode_build\t` 下运行 `:core:lan-share:testDebugUnitTest --tests '*LanShareClientUploadCancellationTest'` 成功（17 项任务，2 项执行、15 项 up-to-date）。这是单测，不是 APK 构建；没有执行设备传输验收。
- **阻碍/恢复条件/下一步**：本测试覆盖中途取消及服务端清理，不证明 Android ViewModel 在真实生命周期切换时的行为；继续检查 `:app` 的传输编排和其他未覆盖边界。

### CI-070 — `:app` 关闭/切换局域网会话时活动上传临时文件清理延迟

- **检查范围**：`LanShareViewModel` 的手动取消、关闭会话、加入新会话及重新启动主机会话时，活动上传任务、HTTP 请求与相机临时文件的清理时序。
- **问题依据/优先级**：确认 P3 资源清理缺口。手动 `cancelUpload()` 会取消请求后立即删除 `ActiveUploadTask.temporaryFile`；但 `invalidateRoomRefresh()` 在关闭/切换会话时只取消 Job 并调用网关取消，临时文件仅由 `invokeOnCompletion` 删除。若任务尚在读取 `ContentResolver` URI，取消未必能立即打断提供器调用，临时文件会在 Job 真正结束前继续占用缓存空间。
- **状态和依赖**：已验证，完成；使用 ViewModel 的公开会话与上传 API 覆盖生命周期，不改变网络协议。
- **修改摘要**：`invalidateRoomRefresh()` 取消每个活动上传任务并通知网关后，立即删除该任务关联的相机临时文件；Job 完成回调继续作为兜底清理。保留了其他现有下载回滚改动。
- **验证及结果**：新增 Robolectric 回归测试，先在旧实现上因阻塞上传期间临时文件仍存在而失败；加入清理后通过。完整 `-PenableAppUnitTests=true :app:testBetaDebugUnitTest :architecture-tests:test` 成功（76 项任务，11 项执行、65 项 up-to-date）。未构建 APK，未进行真机验证。
- **阻碍/恢复条件/下一步**：代码及自动化验证完成；真实 ContentResolver/相机文件提供器与会话切换仍可在后续真机验收时补验。继续检查其他独立的 `:app` 问题。

### CI-071 — `:app` 相机回调时局域网会话缺失会静默丢弃自动上传

- **检查范围**：局域网相机输出文件经 `REQUEST_LAN_SHARE_CAPTURE` 返回后，`selectLanShareAttachment(autoUpload = true)` 消费待上传项、查找当前会话并启动上传的交接。
- **问题依据/优先级**：确认 P3 可靠性与临时文件清理缺口。`takePendingUpload()` 会清空暂存项；旧调用对可空 session 使用安全调用，因此没有会话时既不启动上传，也不提示用户，传入的相机临时文件也不会进入上传完成回调清理。若外部相机期间 Activity/进程恢复后分享会话不存在，拍摄结果会被静默丢弃。
- **状态和依赖**：已验证，完成；会话有效时仍由原上传任务负责临时文件生命周期，会话无效时立即回收本次拍照临时文件并提示重新连接。
- **修改摘要**：抽出可测试的相机上传交接函数并接入原调用点。有效会话仍调用原 `uploadFile`；会话缺失时删除临时照片，若删除失败只记录不含路径的诊断，并提示“分享连接已结束，请重新连接后重试”。未改变正常上传、预览或会话恢复行为。
- **验证及结果**：新测试先因无会话路径没有回收临时照片而失败，再在修复后通过；同时验证有效会话照常转交且不提前删除照片。`-PenableAppUnitTests=true :app:testBetaDebugUnitTest --tests '*LanShareCaptureUploadHandoffTest'` 通过；完整 `-PenableAppUnitTests=true :app:testBetaDebugUnitTest :architecture-tests:test` 通过（76 项任务，2 项执行、74 项 up-to-date）。未构建 APK。
- **阻碍/恢复条件/下一步**：自动化测试已覆盖会话缺失的实际交接分支；外部相机期间进程回收/恢复需真机演练，待设备与授权测试包可用后补验。继续其他不依赖设备或产品决策的 `:app` 检查；CI-070 已用 ViewModel 公共路径回归测试覆盖并完成修复。

### CI-072 — `:app` Android 组件与 FileProvider 暴露面

- **检查范围**：应用 Manifest 中导出的 Activity/Provider、Intent 过滤器、FileProvider 路径配置及所有 `getUriForFile()` 调用点。
- **问题依据/优先级**：针对外部应用可直接调用组件或读取应用私有文件的安全线索进行核查；未发现已证实的暴露缺陷。`MainActivity` 是唯一导出的组件且只有系统启动入口；FileProvider `exported=false`，URI 仅在相机、分享和安装流程中逐个授权。缓存目录配置较宽，但源码中未发现外部传入路径后直接生成 URI 的调用。
- **状态和依赖**：已检查，无需修改；明文 LAN 范围单独追踪在 CI-029，需产品决定是否改变浏览器互通策略。
- **修改摘要**：无产品代码修改，仅记录检查证据。
- **验证及结果**：静态核对 `AndroidManifest.xml`、`file_paths.xml`、`MainActivity` 的 Intent 处理以及所有 FileProvider URI 生成点；未发现自定义深链/任意外部 URI 消费入口。此项是源码审查，不替代设备端 Intent/URI 授权测试。
- **阻碍/恢复条件/下一步**：无当前代码阻碍；若新增分享接收/深链入口或扩大 FileProvider 授权范围，应重新审核。继续处理未完成的自动化验证项与不依赖用户决策的模块检查。

### CI-073 — `:app` 安装失败异常文本被持久化

- **检查范围**：APK 安装启动失败时，异常消息的持久化、日志和用户提示路径。
- **问题依据/优先级**：确认 P3 隐私/诊断数据质量问题。原异常消息会由 `installApkCompose()` 传给 `SettingsViewModel.recordUpdateError()` 并写入 DataStore 的 `last_update_error`；Android 提供器异常可能包含 URI 或路径。该字段没有读取/展示入口，旧 SharedPreferences 迁移也会搬运该值。
- **状态和依赖**：已验证，完成；覆盖安装失败处理、DataStore 写入/读取清理及旧设置迁移。
- **修改摘要**：Domain 定义统一的 `UPDATE_FAILURE_SUMMARY`；安装失败仅持久化通用摘要，当前 Toast 仍保留系统异常详情。DataStore 写入端强制保存通用摘要，并在加载时把旧版本已保存的原始异常替换为通用摘要；旧 SharedPreferences 迁移继续走该安全写入边界。
- **验证及结果**：新增 app 回归测试，先以含虚构 `content://private/path/update.apk` 的异常稳定复现原始内容被持久化，再验证摘要安全且 Toast 详情不变；新增 Robolectric 数据层测试覆盖新写入、旧 DataStore 值和旧 SharedPreferences 迁移。固定本地验证脚本下完整 `:core:data:testDebugUnitTest`、完整 `:app:testBetaDebugUnitTest -PenableAppUnitTests=true` 及总验收 `verifyLocal -PenableAppUnitTests=true` 均通过。2026-10-22 再次强制运行 `:core:data:testDebugUnitTest --tests '*SettingsStoreUpdateErrorTest' --rerun-tasks`，3 项通过、0 失败，34 项任务执行。
- **阻碍/恢复条件/下一步**：无代码阻碍。未构建 APK 或在真机触发 Android 系统安装器异常；若后续新增错误详情展示/导出入口，需继续按隐私策略审查。继续其他不依赖用户决策或设备的模块检查。

### CI-074 — `:core:lan-share` 浏览器端对端数据的 DOM 输出安全

- **检查范围**：WebSocket/HTTP 返回的文件名、消息文本和上传进度名称进入浏览器 DOM 的渲染路径。
- **问题依据/优先级**：针对远端对端文本触发 HTML/脚本注入的线索进行核查；未发现已证实缺陷。文件名、消息和进度名称均通过 `textContent` 写入文本节点；标签、下载名和无障碍描述通过 DOM 属性 API 设置。下载/预览 URL 使用固定相对路由并对文件 ID 编码，没有将对端文本拼接进 HTML。
- **状态和依赖**：已检查，无需修改；不涉及 LAN HTTP 明文策略（CI-029）。
- **修改摘要**：无产品代码修改。
- **验证及结果**：静态追踪 `createFileItem()`、`createMessageItem()`、`updateUploadItem()` 与 `renderTimeline()`；文件中未发现 `innerHTML`、`insertAdjacentHTML`、`document.write` 或动态代码执行 sink。当前工作树的 `:core:lan-share:testDebugUnitTest` 通过，含 `LanShareWebScriptTest`；该测试是脚本结构断言，不等同于真实浏览器 DOM 安全运行测试。
- **阻碍/恢复条件/下一步**：本次静态范围无代码缺陷；若引入 HTML 模板渲染或新 DOM sink，应补浏览器运行级恶意文本回归测试。继续处理仍未完成的模块检查项；CI-073 已完成。

### CI-075 — `:core:lan-share` 合法 `.part` 上传文件被过滤

- **检查范围**：服务端上传文件名清理、临时文件命名、提交后的文件快照和 `/dl/` 下载路由；同时核对路径分隔符及同名并发上传的覆盖保护。
- **问题依据/优先级**：确认 P2 文件可用性缺陷。服务端临时文件名为隐藏的 `.lan-upload-*.part`，但 `isCommittedSharedFile()` 又无差别排除所有 `.part` 后缀。用户上传合法的 `report.part` 时，文件实际完成提交且收到成功响应，却不会进入共享文件快照，下载路由也将其视为未找到。
- **状态和依赖**：已验证，完成；范围限于 `:core:lan-share` 上传/共享文件规则，不改传输协议。
- **修改摘要**：提交文件过滤改为排除隐藏文件；当前上传暂存文件已经以点号开头，仍不会泄露到文件列表或下载路由。新增真实 loopback HTTP 回归测试，验证 `.part` 文件上传后可列出并下载；另新增路径分隔符测试，确认编码后的 `../../` 文件名清理后仍落在共享目录内。
- **验证及结果**：新 `.part` 回归测试在旧实现中因文件快照缺失而失败，修复后通过；完整 `:core:lan-share:testDebugUnitTest` 成功（17 项任务，5 项执行、12 项 up-to-date）。测试最初尝试直接读取 `/api/files` 时遇到 JVM Android `org.json` stub 的 “not mocked”，因此回归断言改走服务端文件快照与真实下载路由；该测试环境限制未被误报为产品故障。静态核对 `safeFileName()` 会替换路径分隔符；提交在锁内拒绝已存在目标，时间戳碰撞最多导致安全拒绝，不会覆盖已有文件。
- **阻碍/恢复条件/下一步**：本项不依赖设备或远端服务。继续按模块清单检查下一项；CI-073 已完成，不阻塞独立审查。

### CI-076 — `:core:lan-share` 客户端下载暂存与目标替换

- **检查范围**：Android 客户端原文件/预览下载的响应校验、目标目录暂存、完成后替换及失败清理；对照当前最低 Android API。
- **问题依据/优先级**：复核下载中断是否可能破坏已有目标文件；未发现已证实缺陷。原文件和预览均先写入目标同目录的唯一隐藏 `.part` 临时文件，成功复制完成后才替换目标；预览额外限制声明长度和实际复制字节数。替换失败时现有目标保留，调用方 `finally` 清理暂存文件。
- **状态和依赖**：已检查，无需修改；Android API 最低版本为 26，与 `java.nio.file.Files.move` 的平台可用范围相符。
- **修改摘要**：无代码修改。
- **验证及结果**：核对 `LanShareClient.downloadToFile()`、`downloadPreview()`、`LanShareDownloadFiles` 及 `LanShareDownloadFilesTest` 的保留旧目标、替换成功和同目录暂存用例；本轮完整 `:core:lan-share:testDebugUnitTest` 通过，包含这些用例。未进行设备文件系统验证。
- **阻碍/恢复条件/下一步**：源码与单元覆盖未发现当前问题；不同 OEM 文件系统/存储故障仍需设备测试。继续审查尚未覆盖的 `:app` 或构建交付检查项；CI-073 已完成。

### CI-077 — `:app` Beta 调试日志写入屏障与导出完整性

- **检查范围**：导出动作、异步诊断队列、flush 顺序屏障、溢出计数、异常回退日志及 8 MiB 导出上限。
- **问题依据/优先级**：针对此前“导出日志缺少排障信息”的反馈复核；本轮三个假设均未证实为当前代码缺陷：导出前排队记录未落盘、队列溢出无提示、达到大小上限时误删最新记录。
- **状态和依赖**：已检查，无需修改；极慢/故障文件系统导致 2 秒 flush 超时时，导出会明确附加 `export_flush_timeout`，并非承诺此时仍能收齐全部待写记录。
- **修改摘要**：无代码修改。导出动作先记请求事件；`snapshot()` 通过单线程队列屏障等待之前入队的写入，再读取文件。队列溢出计数由屏障排出为诊断记录；截断按完整记录、文件新旧和最新记录优先，摘要报告省略数量；后端异常的同步兜底文件也纳入导出源。
- **验证及结果**：核对 `BetaDebugLogBackend.snapshot()`、`DiagnosticWriteQueue`、`LogRecordSelection` 与对应单测/架构契约。`:app:testBetaDebugUnitTest` 中 `DiagnosticWriteQueueTest`、`LogRecordSelectionTest` 与 `:architecture-tests:test --tests '*DebugLogContractTest'` 均通过；合并命令成功（76 项任务，2 项执行、74 项 up-to-date）。未用设备模拟慢速/故障存储，也未做真实分享面板端到端验证。
- **阻碍/恢复条件/下一步**：没有发现静默丢弃队列记录或截断最新记录的代码路径；慢盘超时仍可能按已声明策略省略待写记录。若用户再次提供日志缺失样本，应对照其时间戳与 `export_flush_timeout` / `queue_overflow` 标记定位；继续 `:app` 未覆盖系统边界。CI-073 已完成。

### CI-078 — Gradle 依赖版本集中管理与仓库边界

- **检查范围**：版本目录、根/模块插件声明、五个模块的外部依赖版本来源，以及 settings 中的仓库约束。
- **问题依据/优先级**：检查跨模块依赖版本漂移与模块自行添加仓库的风险；未发现已证实缺陷。外部库版本已集中在 `gradle/libs.versions.toml`，模块依赖使用 `libs.*`，未发现动态 `+` 或 `SNAPSHOT` 版本；Compose 无显式版本的库由 BOM 管理。仓库统一在 `settings.gradle.kts` 声明并启用 `FAIL_ON_PROJECT_REPOS`。
- **状态和依赖**：已检查，无需修改；没有为追求统一而改写现有插件 DSL。
- **修改摘要**：无产品或构建配置修改。
- **验证及结果**：静态搜索五个模块及根脚本的版本声明和动态版本；核对根插件 alias 与模块插件 ID 的版本供应关系；本轮全模块 Gradle 测试任务图解析成功。Beta 单元与架构测试实际执行 2 项任务并通过；其余本轮全模块门禁任务均标记 up-to-date，未在该命令中重新执行。
- **阻碍/恢复条件/下一步**：本地版本管理与仓库边界未发现问题；第三方 Action 的远端 runner 行为仍需经授权的 Actions 运行验证，不属于本次依赖目录审查。继续检查 `:app` 中未覆盖的本地可验证边界；CI-064 的测试边界选择及设备验收项仍单独追踪，CI-073 已完成。

### CI-079 — `:app` 结果收藏保存对话框的用户路径回归覆盖

- **检查范围**：从结果页打开收藏保存对话框、选择文件夹、输入名称、确认保存及成功/失败后的界面行为。
- **问题依据/优先级**：确认 P2 自动化覆盖缺口，不代表当前仍有崩溃。`ResultToolbarInteractionTest` 只验证“收藏”按钮触发回调；现有测试未发现实际驱动 `saveResultAsFavoriteCompose()` 的对话框、保存按钮及成功导航。该代码组合了 Compose 状态、嵌套目录编辑、Activity 生命周期任务和 Dialog 关闭/导航交接，过去曾报告过保存崩溃，因此仅有 ViewModel 数据层测试不能覆盖完整触发路径。
- **状态和依赖**：待确认。按 TDD 技能要求，新增测试前需确认测试接缝；2026-10-22 再次请用户确认以 Robolectric/Compose 驱动对话框、选择目录、输入名称、点击保存并验证成功回调/关闭的范围。复查用户提供的 2026-10-07 日志时，现有异常均跟在 `result_edit` 事件后，没有收藏保存事件对应的崩溃堆栈；因此不能将结果编辑的 GlassBackdrop 根因外推到收藏保存。
- **修改摘要**：本项无产品代码或测试修改；仅记录缺口和建议验证范围。
- **验证及结果**：静态搜索 `android/app/src/test` 未发现收藏保存对话框端到端交互测试；确认项目已有 Compose UI 测试、Robolectric 与 Android 资源测试配置，可评估候选方案，但尚未验证启动主 Activity 并完整触发该 Dialog 的可行性。
- **阻碍/恢复条件/下一步**：等待用户确认是否采用该 UI 测试接缝；确认后先验证它能到达真实调用链，再补回归用例。若未确认则不新增测试，继续检查独立的本地模块与系统边界。

### CI-080 — 仓库 Markdown 文档相对链接有效性

- **检查范围**：仓库 8 份 Markdown 文档中的相对文件/目录链接，重点检查结构整理后 README、架构、协议、贡献和结构文档之间的引用。
- **问题依据/优先级**：复核文件迁移后文档链接失效风险；未发现已证实问题。检查到的仓库内相对链接全部指向存在的文件或目录，没有 Markdown 引用式链接需要另行解析。
- **状态和依赖**：已检查，无需修改；仅覆盖本地相对链接，不验证 GitHub 页面、Release、Actions 等外部链接在线可用性。
- **修改摘要**：无文档内容修改；本进度记录新增本项审查结果。
- **验证及结果**：使用 PowerShell 扫描 8 份 `.md`/`.markdown` 文件的内联链接并逐一 `Test-Path`，结果 `BROKEN_LINKS=0`；另确认仓库没有引用式 Markdown 链接定义。
- **阻碍/恢复条件/下一步**：外部 GitHub 链接需在网络/仓库状态变化后另行检查；继续处理 `:app` 中不依赖设备或待确认产品决策的剩余边界。

### CI-081 — `:core:data` Room v1/v2 到当前版本的迁移覆盖

- **检查范围**：Room 数据库版本 1–6 的迁移链、schema 快照和实际迁移测试起始版本。
- **问题依据/优先级**：确认 P2 迁移覆盖缺口，不代表已证实数据损坏。生产数据库为 v6，注册了完整的 `1→2→3→4→5→6` 迁移；仓库仅保留 v3–v6 schema 快照，迁移集成测试仅从 v3、v4、v5 创建旧库。因此 v1/v2 安装升级时的索引创建及关联表重建没有真实 Room 链路回归覆盖，可能在旧数据升级场景暴露 schema/数据保持问题。补查 v5 收藏关系表只有 `(groupId, itemId)` 复合主键、没有顺序列；v6 按 `groupId, itemId` 确定性回填 `position`，当前读取明确按 `position, itemId` 排序。现有可达源码不能证明旧 UI 的展示顺序，因此不能声称迁移已保留旧 UI 顺序，也没有证据证明它造成了乱序。
- **状态和依赖**：待确认。已向用户提出测试接缝：通过可信 v1/v2 数据库 fixture 打开真实 Room 数据库并升级至 v6，检查条码、收藏关系/顺序、文件夹和外键；当前不写测试，等待接缝确认并寻找权威旧 schema，避免猜造 fixture。
- **修改摘要**：本项未修改产品代码或测试，仅记录待验证风险。
- **验证及结果**：静态比对 `BarcodeDatabase` 迁移注册、v5/v6 schema、迁移 SQL 与 `BarcodeDatabaseMigrationTest` 入口；当前 v5 fixture 验证 `itemId` 回填顺序稳定，但不能证明历史 UI 序列等价。使用短临时目录 `D:\Barcode_build\t` 强制重跑迁移测试类，12 项通过；强制重跑完整 `:core:data:testDebugUnitTest`，13 个测试套件、63 项测试、0 失败。默认 `%TEMP%` 下 SQLite 打不开与既有 CI-053 记录一致；本机短路径设置下通过，不作为产品缺陷。本轮只读复核当前非浅克隆的全部可达历史：最早提交为 `e0711b5`（2026-10-14 初始快照），该提交仅有 v3–v6 schema；结合本地验证 checkout 无 `1.json`/`2.json` 快照或非构建目录 `.db`/`.sqlite` fixture，仍没有权威 v1/v2 起始库。`git diff --check` 通过，未重复运行 Gradle。
- **阻碍/恢复条件/下一步**：仍需用户确认测试接缝，并取得/恢复权威 v1/v2 schema 或旧数据库样本；当前源码、完整可达 Git 历史及本地验证 checkout 均无旧版依据。验证目录中的 `.git` worktree 指针还指向已不存在的 `C:\Users\zhimi\Documents\GitHub\...` 元数据，不能当作可验证的历史提交来源。GitHub 当前 refs 仅有 `beta`/`main` 两分支及一个 `android-test-v9.9.9` tag/Release，该 tag 仍只有 v3–v6 schema。若无法取得旧版样本，保留为未验证迁移风险，不手工臆造数据结构。继续审查可独立验证的剩余模块边界。

### CI-082 — `:app` Manifest 权限与系统入口对应关系

- **检查范围**：运行时权限及安装/网络权限声明，核对对应 Activity、图库/文档选择器与应用内更新入口。
- **问题依据/优先级**：针对过度声明、权限请求与实际系统入口不一致的兼容性/隐私风险进行检查；未发现已证实问题。`CAMERA` 对应 OCR 与局域网拍照；`READ_EXTERNAL_STORAGE` 限定至 API 32 并只在旧版图库选择路径申请，文档选择器使用 URI 授权而不请求广泛存储权限；`INTERNET`/`ACCESS_NETWORK_STATE` 对应 LAN 与更新网络路径；`REQUEST_INSTALL_PACKAGES` 对应应用内 APK 安装入口；相机硬件声明为非必需。
- **状态和依赖**：已检查，无需修改；明文 LAN 网络策略仍单独待产品确认（CI-029），本项不改变其兼容性。
- **修改摘要**：无产品代码或 Manifest 修改；新增本进度记录。
- **验证及结果**：静态核对 Manifest 权限声明、`LanSharePickerPermissionPolicy`、相机权限入口及 APK 安装调用路径；现有 `LanSharePickerPermissionPolicyTest` 定向运行成功（`:app:testBetaDebugUnitTest`，1 项任务执行）。未做真机系统权限弹窗验证。
- **阻碍/恢复条件/下一步**：无需设备的权限映射未发现需修复项；系统实际授权提示仍需设备验证。继续检查其他独立的 `:app` 系统边界；CI-079 的测试接缝与 CI-081 的历史迁移样本及用户决策仍各自跟踪，CI-073 已完成。

### CI-083 — `:core:lan-share` HTTP 与 WebSocket 会话认证边界

- **检查范围**：公共配对页与配对 API、文件/预览/下载/上传路由、WebSocket 握手的授权判定，配对码生成与尝试限速。
- **问题依据/优先级**：针对匿名访问或弱会话凭据进行复核；未发现已证实的认证绕过。会话令牌由 `SecureRandom` 生成 32 字节随机值；私有 HTTP 路由和 `/ws` 握手均验证 Bearer/query 令牌并用 `MessageDigest.isEqual` 比较。配对码为 8 位安全随机数，按客户端地址每 60 秒最多允许 5 次错误尝试，并限制跟踪地址数。只有配对页和配对入口公开，配对成功响应标记 `no-store`；服务器未配置宽松 CORS 响应。
- **状态和依赖**：已检查，无需修改；认证不提供传输加密，局域网监听者仍可能截获 HTTP 中的配对码或令牌，继续由 CI-029 跟踪。
- **修改摘要**：无产品代码修改；新增本进度记录。
- **验证及结果**：静态追踪 `serve()`、`serveHttp()`、`isAuthorized()`、`pairBrowser()` 与令牌生成；现有 `LanShareServerOpenAccessTest` 定向运行成功（`:core:lan-share:testDebugUnitTest`，1 项任务执行），覆盖未配对私有 API 拒绝、合法令牌访问、WebSocket 拒绝/放行、配对码交换及来源限速。未做真实浏览器跨源运行测试或 Wi‑Fi 抓包验证。
- **阻碍/恢复条件/下一步**：源码与现有 loopback 测试未发现认证逻辑缺陷；对不可信网络的凭据保密仍需 CI-029 决定是否进行 HTTPS/WSS 协议改造。继续审查不依赖该决策的剩余模块。

### CI-084 — Beta/正式版 workflow 触发、权限与发布完整性门禁

- **检查范围**：Beta 与正式版 GitHub Actions 的触发方式、分支条件、Token/签名密钥暴露范围、版本恢复及 Release 发布前校验。
- **问题依据/优先级**：针对普通推送触发正式发布、错误分支发布、构建 job 获得不必要的 Release 写权限，以及未校验附件即公开发布的风险进行复核；未发现已证实问题。两个 workflow 都仅声明 `workflow_dispatch`；Beta job 限定 `beta`，正式版限定 `main`。发布 PAT 仅注入需要读写 Release 的步骤/job，构建使用单独的签名 Secrets；正式版发布前会从 Release 下载 APK 与校验文件进行字节比对和 SHA-256 检查，并确认 Release API 附件名称、大小和上传状态。
- **状态和依赖**：静态检查通过；旧的单 job Beta 流程及 Release 更新已有成功记录（CI-067）。当前 `origin/beta` 的双 job Beta 流程尚无远端执行验证，正式版远端流程也无本轮验证。未读取仓库 Secrets。
- **修改摘要**：本轮未改 workflow 或发布脚本；记录反映现有工作树与远端证据的差异。
- **验证及结果**：核对两个 workflow 的触发器、分支条件、权限与 Secrets 注入范围；正式版版本分配测试 15 项通过。只读检查的 Beta 成功运行完成 APK 构建、Release 更新及签名文件清理，Release 正文摘要与已上传 asset digest 一致；未独立下载 APK 重算摘要。未启动新 Actions、未读取 Secret 值。
- **阻碍/恢复条件/下一步**：需当前 `origin/beta` HEAD 的远端运行验证双 job 与 Action SHA 固定；正式版集成仍缺本轮远端验证。与 CI-067、CI-163 共用 Beta 验证。继续检查剩余不依赖这些动作的模块。

### CI-085 — `main` 正式版分支未强制执行 PR 检查

- **检查范围**：GitHub `main`/`beta` 分支保护、仓库 Rulesets、PR 检查 workflow 触发方式及其强制执行状态。
- **问题依据/优先级**：确认 P2 交付门禁缺口。2026-10-22 的 GitHub REST 查询显示 `main`、`beta` 均未启用 branch protection，rulesets 列表为空，因此当时没有 required status check 或 PR 保护；若设置未改变，直接推送 `main` 仍可绕过 PR 检查。PR workflow 的 Release 编译与 Lint 改动现已包含在 `origin/beta`（CI-117），但这不会自动启用分支保护，也不能证明 `main` 已包含该 workflow。
- **状态和依赖**：待确认；是否为 `main` 启用“必须通过 PR 与 `Verify architecture boundaries` 状态检查”会改变现有直推流程，需要用户决定。`beta` 也未保护，但本记录不建议擅自改变用户当前 Beta 推送习惯。
- **修改摘要**：未修改 GitHub 仓库保护设置；PR workflow 的检查范围改动单独记录为 CI-117。
- **验证及结果**：2026-10-22 只读查询仓库 branch protection：`main`、`beta` 均返回 `Branch not protected (404)`，rulesets 为空。该结果有日期，未在本次重新查询；当前 `origin/beta` 已包含 CI-117 的 workflow 修改，但尚无当前 revision 的远端 PR run 证据。
- **阻碍/恢复条件/下一步**：如用户决定强制保护 `main`，需明确 required check 名称、是否要求 PR 审核及是否限制管理员绕过，然后再修改 GitHub 设置；在决定前不做外部写入。继续其他不依赖此决定的本地审查。

### CI-117 — PR 门禁遗漏 Beta/Official Release 编译与 Lint

- **检查范围**：`.github/workflows/architecture-checks.yml` 的 PR Gradle 命令，以及根 `verifyLocal` 的依赖任务与 APK 组装边界。
- **问题依据/优先级**：确认 P2 检查覆盖缺口。原 PR 命令只运行架构、Domain/Data/LAN Share 和 Beta Debug 单测；根 `verifyLocal` 还包含 Beta/Official Release Kotlin 编译及两种 Release Lint。编译/Lint 故障此前只能在手动发布前发现，增加不必要的反馈延迟。
- **状态和依赖**：已验证，完成（本地）；架构契约、全量 `:architecture-tests:test` 与 `verifyLocal` 均通过。workflow 修改已推送至 `origin/beta`，当前 revision 的 GitHub PR workflow 托管端执行仍未验证。
- **修改摘要**：PR job 改为调用 `verifyLocal --no-configuration-cache -PenableAppUnitTests=true`，保留已有 job/check 名称与只读权限；新增架构契约确认 PR 调用完整门禁、门禁包含两种 Release 编译和 Lint 且没有 `assemble` 任务。未修改分支保护、触发方式或发布流程。
- **验证及结果**：契约测试先在旧 workflow 上按预期失败（缺少 `verifyLocal`），更新 YAML 后定向契约通过；强制重跑完整 `:architecture-tests:test` 成功。随后使用 `D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline verifyLocal` 成功，191 项任务（3 执行、2 缓存、186 up-to-date），覆盖 Beta 单测、架构检查、Beta/Official Release 编译与 Lint，不组装 APK；`git diff --check` 无空白错误，仅提示若干既有文件的 LF/CRLF 转换。该本地结果不代表 GitHub 托管 runner 已验证。
- **阻碍/恢复条件/下一步**：需要当前 revision 的 GitHub PR workflow run 才能验证 runner 环境、缓存和整套 Release 检查；PR 门禁是否强制仍由 CI-085 的分支保护决策决定，不因 workflow 已推送而视为关闭。

### CI-086 — 仓库签名密钥文件跟踪与忽略边界

- **检查范围**：当前 Git 索引及本地可达历史中的 `.jks`、`.keystore`、`keystore.properties` 文件路径，以及签名材料忽略规则。
- **问题依据/优先级**：未发现密钥文件被当前索引或本地可达提交跟踪。根 `.gitignore` 明确忽略 `*.jks`、`*.keystore`，抽样路径验证命中对应规则；未读取任何密钥内容或 GitHub Secrets。
- **状态和依赖**：已检查，无需修改。结论限于当前索引、本地可达 Git 历史和仓库忽略规则，不等价于审计 GitHub Secrets、其他 clone，或不可达/已删除的远端历史。
- **修改摘要**：无产品代码、配置或文档修改；补充本进度记录。
- **验证及结果**：`git ls-files` 未列出签名密钥文件路径；`git log --all --name-only` 未列出本地可达历史中的密钥文件路径；`git check-ignore -v --no-index probe/barcode-release.jks` 命中 `.gitignore:16`。仅检查文件名/路径，没有读取密钥或凭据值。
- **阻碍/恢复条件/下一步**：若需确认 GitHub Secret 的当前值或授权范围，需用户在 GitHub 设置界面自行核对；平台不会回显 Secret 值。继续 `:app` 中不依赖该外部确认的剩余审查。

### CI-087 — `:app` 局域网分享在后台时的存活策略

- **检查范围**：局域网 HTTP/WebSocket 服务启动/停止归属、Manifest 后台组件声明及 Android 进程生命周期保证。
- **问题依据/优先级**：待确认的 P2 产品行为线索，不是已证实缺陷。`LanShareManager.start()` 在应用进程内启动服务；`:app` 没有声明 Service/WorkManager 等后台执行组件，也未调用 `startForegroundService()`；`LanShareViewModel.onCleared()` 会关闭会话。Android 官方说明不可见的后台进程可能被系统回收，因此当前设计不承诺切到其他应用或锁屏后服务持续可用。参考：[Activity lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle)、[Foreground services](https://developer.android.com/develop/background-work/services/foreground-services)。
- **状态和依赖**：待确认；已向用户询问局域网分享是否必须在切换应用/锁屏后继续运行。答案决定是否需要评估常驻通知、前台服务类型和新 Android 后台限制。
- **修改摘要**：无代码或 Manifest 修改。
- **验证及结果**：静态核对 `LanShareManager`、`LanShareViewModel`、`:app` Manifest 和后台任务 API 搜索结果；确认服务跟随应用进程，无独立前台服务保活。未做后台/锁屏真机实验。
- **阻碍/恢复条件/下一步**：收到产品行为选择后再判断是否列为缺陷及设计修复；在此之前不引入前台服务、通知或权限。继续其他不依赖此选择的检查。

### CI-088 — 本地 AI 指引与 Python 测试产物的 Git 忽略规则

- **检查范围**：仓库共享 `.gitignore`、本机 `.git/info/exclude`、当前索引和工作区中的本地 AI 指引/会话文件及测试生成文件。
- **问题依据/优先级**：确认 P2 误上传风险。根 `AGENTS.md` 只由本机 `.git/info/exclude` 忽略，其他 clone 不会继承该规则；`android/tools/__pycache__/` 在当前 clone 中是未跟踪本地 Python 测试产物，仓库规则此前未屏蔽。未发现本地 AI 目录或日志文件已被 Git 跟踪。
- **状态和依赖**：已验证，完成；规则只针对本地 agent 指引/会话目录和 Python 缓存，不忽略项目测试源码。
- **修改摘要**：根 `.gitignore` 新增根级 `AGENTS.md`、`CLAUDE.md`、`GEMINI.md` 及常见本地 AI 会话目录忽略项，并忽略各层级 `__pycache__/`、`*.py[cod]` 和 `.pytest_cache/`。没有删除文件。
- **验证及结果**：`git check-ignore -v --no-index` 确认这些本地路径均命中新规则；当前 `AGENTS.md` 和 `android/tools/__pycache__/` 状态显示为 ignored；`android/tools/test_beta_release_version.py` 未被规则忽略；`.gitignore` 的 `git diff --check` 通过。
- **阻碍/恢复条件/下一步**：当前工作区中未发现其他本地 AI/测试数据候选；以后若新增其他工具专用状态目录，再按实际路径补充，避免无依据的宽泛忽略。

### CI-089 — `:core:data` DataStore 依赖泄漏到消费者编译接口

- **检查范围**：`:core:data` 的 Gradle 依赖作用域、`SettingsStore` 对外可见成员及 app/测试调用点。
- **问题依据/优先级**：确认 P2 模块接口泄漏。此前 DataStore Preferences 以 `api` 导出；`SettingsStore.get()` 和公开键常量的类型暴露 `Preferences.Key`。全仓调用核对显示这些成员只被 `:core:data` 自身实现、迁移和测试使用，app 只通过构造 `SettingsStore` 完成装配，并使用领域层 `SettingsRepository`，不需要直接依赖 DataStore 类型。
- **状态和依赖**：已验证，完成；保留 app 必需的 `SettingsStore` 构造和 `SettingsRepository` 接口，收窄的仅是仓库内未被消费者使用的实现细节。
- **修改摘要**：将 DataStore 依赖由 `api` 改为 `implementation`；将 Preferences 键常量及通用 `get()` 收窄为 `internal`。未改变持久化格式、设置行为或数据库结构。
- **验证及结果**：通过本地固定 JDK/SDK/Gradle 环境离线运行 `:core:data:testDebugUnitTest`、`:app:testBetaDebugUnitTest`、`:core:domain:test` 和 `:architecture-tests:test`。Data 测试 66 项、app Beta 测试 390 项均为 0 失败；Domain 测试因无源码变化为 `UP-TO-DATE`；架构测试随后用 `--rerun-tasks` 强制执行并成功。`compileBetaDebugKotlin` 成功，证明 app 消费者编译不再依赖 DataStore 编译类路径。`:core:data:testDebugUnitTest` 首次受长临时路径 SQLite 打不开影响；使用固定工具外的短临时目录 `D:\Barcode_build\t` 后，单项重跑与完整相关测试均成功。未打包 APK。
- **阻碍/恢复条件/下一步**：无本地验证阻碍；变更尚未推送，因此没有远端 CI 证据。继续选择无需设备、产品决策或远端写入即可审查的项目。

### CI-090 — Gradle `api` 依赖的消费者类型必要性复核

- **检查范围**：所有 Gradle 模块的 `api(...)` 声明及其模块对外类型/直接消费者。
- **问题依据/优先级**：复核 CI-089 收窄 DataStore 后剩余的两项 `api`。`:core:domain` 的公开契约显式使用 `kotlinx.coroutines.Job` 和 `StateFlow`；`:core:data` 的 `BarcodeDatabase` 继承 `RoomDatabase`，`BarcodeDao` 和数据库类型由 app 的依赖装配直接使用。两项均是消费者需要解析的类型，不是只供实现内部使用的依赖。
- **状态和依赖**：已检查，无需修改。
- **修改摘要**：无产品代码或构建配置修改；保留两项必要的 `api` 声明，避免无依据地收窄消费者编译接口。
- **验证及结果**：只读核对 Gradle 依赖作用域、Domain 对外签名、Room 数据库/DAO 类型及 app DI 调用点；CI-089 后的 app Beta 编译与单测已通过，说明 `:core:data` 改用 `implementation` 的 DataStore 不再是 app 编译依赖。未重复运行无需改变的 Domain 行为测试。
- **阻碍/恢复条件/下一步**：依赖接口审查无剩余本地变更项；持续改进仍有真机验证、产品策略选择、历史迁移样本/测试接缝和远端 workflow 验证待完成，保持目标活动。

### CI-091 — `:app` 持久化恢复包装器吞掉致命 JVM 错误

- **检查范围**：`runLibraryDataRecovery` 在持久化写失败后重新加载持久快照时的异常分类和传播行为。
- **问题依据/优先级**：确认 P2 稳定性缺陷。恢复器先单独重抛 `CancellationException`，但后续捕获 `Throwable`，会把 `AssertionError`、`OutOfMemoryError` 等 JVM `Error` 交给普通 `onFailure` 日志回调并当作恢复完成；这掩盖了进程级/程序级故障，并可能继续以损坏或陈旧 UI 状态运行。最小回归测试在修复前失败，直接证明了异常被吞掉。
- **状态和依赖**：已验证，完成；普通 `Exception` 仍由调用方记录并恢复，协程取消及非 `Exception` 的 `Throwable` 继续向上传播。
- **修改摘要**：将 `runLibraryDataRecovery` 的兜底捕获由 `Throwable` 收窄为 `Exception`；新增 `AssertionError` 回归测试，同时保留普通读取异常和取消行为的既有测试。
- **验证及结果**：用本地验证脚本 `D:\Barcode_build\run-barcode-android-gradle.ps1` 运行 `:app:testBetaDebugUnitTest --tests com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataRecoveryTest`，修复前测试按预期失败；修复后运行完整 `:app:testBetaDebugUnitTest`，`BUILD SUCCESSFUL`，全部 Beta 单元测试通过。未打包 APK、未部署设备。
- **阻碍/恢复条件/下一步**：该修复仅在本地源码和单元测试验证；尚无真机、远端 Actions 验证。继续审查 `:app` 其余未覆盖系统交接和持久化恢复边界；保留 CI-027/029/064/079/081/087 等设备条件或用户决策待办。

### CI-092 — 同一 workflow run 重试时构建 artifact 重名失败

- **检查范围**：正式版与 Beta workflow 的临时 artifact 上传步骤、失败恢复路径及对应静态架构契约。
- **问题依据/优先级**：确认 P2 发布恢复缺陷。两个 workflow 的 artifact 名称包含稳定的 `github.run_number`；正式版在同一个 job 中先上传 workflow artifact，之后还要上传并校验 Release 附件、最后才发布 Release。若后续步骤失败并重跑该 run，workflow 会再次上传同名 artifact。`actions/upload-artifact` 默认不覆盖已有同名 artifact，重跑会在上传阶段失败，阻断同版本恢复流程；Beta 全 workflow 重跑也有相同风险。Action 官方说明 `overwrite: true` 会先删除同名 artifact 再上传。
- **状态和依赖**：已修复，待远端验证；Beta 与正式版 workflow 的修复已包含在 `origin/beta`。本地契约测试覆盖了两份 workflow 的 artifact 名称和覆盖配置，但尚无同一远端 run 重传成功的证据。
- **修改摘要**：在正式版和 Beta 的 `actions/upload-artifact` 配置中启用 `overwrite: true`，并注明该设置用于同一 run 恢复；新增 `ReleaseWorkflowArtifactRetryContractTest` 检查两份 workflow 的 run 级名称和覆盖配置，将两个 YAML 文件登记为架构测试输入。
- **验证及结果**：契约测试在添加覆盖设置前按预期失败，修复后通过；本轮再次使用 `D:\Barcode_build\run-barcode-android-gradle.ps1 --offline :architecture-tests:test --rerun-tasks` 强制运行，提权允许 Gradle daemon 本地 IPC 后 `BUILD SUCCESSFUL`。未构建 APK、未读取 Secrets、未修改 Release。
- **阻碍/恢复条件/下一步**：远端重跑行为尚未验证；需在同一 workflow run 上传过 artifact 后重跑，确认 `overwrite: true` 可恢复。当前本机 GitHub CLI token 无效，无法启动/重跑远端 Actions；重新认证后再验证。继续检查不依赖远端的独立模块。

### CI-093 — 收藏备份分享准备把致命 JVM 错误包装成普通失败

- **检查范围**：`:app` 收藏备份分享临时 ZIP 的创建、写入异常分类、清理和错误返回边界。
- **问题依据/优先级**：确认 P2 稳定性缺陷。`prepareFavoriteBackupShareArtifact()` 先重抛 `CancellationException`，但随后捕获全部 `Throwable`，删除临时文件后将任意错误装入 `Result.failure`。因此 `AssertionError`、`OutOfMemoryError` 等并非普通可恢复 I/O/应用异常，会进入常规分享失败 Toast 路径而不再传播，可能掩盖进程级错误。
- **状态和依赖**：已验证，完成；保留取消传播和普通异常转 `Result.failure` 的既有行为，致命 `Error` 在尝试清理临时文件后重新抛出。
- **修改摘要**：在通用清理分支中，清理部分 ZIP 后仅接受 `Exception` 作为可恢复结果；非 `Exception` 的 `Throwable` 原样抛出。新增回归测试验证 `AssertionError` 身份传播且部分文件被删除。
- **验证及结果**：新测试在修复前按预期失败；修复后 `FavoriteBackupSharePreparationTest` 全部通过。随后包含本项及 CI-094 的完整 `-PenableAppUnitTests=true :app:testBetaDebugUnitTest` 报告 395 项、0 失败且 Gradle `BUILD SUCCESSFUL`。未打包 APK 或部署设备。
- **阻碍/恢复条件/下一步**：代码及本地单测已验证；文件系统异常和 Android 系统分享面板仍未在真机演练。继续审查其他可本地验证的 `:app` 恢复边界；CI-092 远端 workflow 重试仍待改动推送后实际验证。

### CI-094 — LAN 文件刷新把取消与致命错误转换为连接失败

- **检查范围**：`LanShareViewModel.refreshFiles()` 中并发读取服务器文件和本地消息的异常分类，以及刷新结果的恢复行为。
- **问题依据/优先级**：确认 P2 稳定性缺陷。原实现通过 `runCatching` 包裹挂起式 `list()`/`localMessages()` 调用；Kotlin `runCatching` 捕获任何 `Throwable`。父协程取消会在后续 `ensureActive()` 被发现，但独立传播的 `CancellationException` 和 `AssertionError`/`OutOfMemoryError` 会被当成 `Result.failure`，继而记成普通刷新失败并可发出“无法连接”事件，掩盖取消/致命错误语义。
- **状态和依赖**：已验证，完成；普通 `Exception` 继续作为可恢复刷新失败，`CancellationException` 及非 `Exception` 的 `Throwable` 原样向上传播。
- **修改摘要**：提取 `loadLanShareRefreshData()` 作为刷新查询边界；改为只捕获 `Exception`，并显式重抛协程取消。新增 3 项契约测试覆盖普通异常、取消和 `AssertionError`。
- **验证及结果**：致命错误用例在旧 `runCatching` 实现下按预期失败；修复后 `LanShareRefreshResultTest` 3/3 通过。完整 `-PenableAppUnitTests=true :app:testBetaDebugUnitTest` 成功，XML 报告 395 项测试、0 失败、0 错误。只运行本地测试，未打包 APK。
- **阻碍/恢复条件/下一步**：异常分类已由边界单测验证；LAN 网络恢复与取消的实际设备交互仍需真机验证。继续按模块检查其他本地可验证项；CI-092 的远端 artifact 重试仍待推送后验证。

### CI-095 — LAN 预览下载将取消与致命错误当成缩略图缺失

- **检查范围**：`LanShareViewModel.fetchPreviews()` 的预览下载结果封装、失败后的降级处理，以及 `LanShareClient.downloadPreview()` 的目标文件完整性边界。
- **问题依据/优先级**：确认 P2 稳定性缺陷。预览下载通过 `runCatching` 捕获所有 `Throwable`，调用点只记录普通失败然后继续刷新；因此独立的 `CancellationException` 和 JVM `Error` 会被压成“没有可显示缩略图”。普通网络失败降级为无预览是预期行为，取消与致命错误不应被当作正常缺图。另核实 `LanShareClient.downloadPreview()` 使用临时文件，完整响应后才替换目标，并在 `finally` 删除临时文件；本项没有发现其原子下载清理缺陷。
- **状态和依赖**：已验证，完成；普通 `Exception` 保持可降级结果，协程取消和非 `Exception` 的 `Throwable` 原样传播。
- **修改摘要**：提取 `downloadLanSharePreviewResult()` 作为实际调用的失败分类边界；显式重抛 `CancellationException`，仅将普通 `Exception` 封装为 `Result.failure`。新增 3 项契约测试，分别锁定普通异常降级、取消传播和 `AssertionError` 传播。
- **验证及结果**：契约测试在原 `runCatching` 实现下连续两次稳定复现取消与致命错误断言失败，普通异常降级用例通过；修复后 `LanSharePreviewDownloadResultTest` 3/3 通过。之后运行 `-PenableAppUnitTests=true :app:testBetaDebugUnitTest :architecture-tests:test`，`BUILD SUCCESSFUL`；Beta 单测报告 398 项、0 失败，架构契约通过（76 项任务，2 项执行、74 项 up-to-date）。仅运行本地测试，未打包 APK。
- **阻碍/恢复条件/下一步**：LAN 预览异常分类已单测验证；设备级网络中断和缩略图呈现仍未真机复验。继续检查其他 `:app` /系统交接边界；CI-092 远端 workflow 重试仍需授权推送后验证。

### CI-096 — 持久化写队列吞掉取消与致命错误

- **检查范围**：`PersistenceWriteQueue` 的写入串行、失败分类、排队后续快照行为与 `BarcodePersistenceCoordinator` 的后台失败观察器。
- **问题依据/优先级**：确认 P2 稳定性问题。队列用 `runCatching { write() }` 捕获所有 `Throwable`，把独立 `CancellationException` 和 `AssertionError` 等致命错误包装为普通 `Result.failure`；后台观察器又对 `Deferred.await()` 使用 `runCatching`，进一步静默吞掉队列级异常。直接回归测试在修复前证实取消和致命错误会被包装而不传播。若简单让后续写入 `await()` 上一项结果，单次取消还会沿队列传播、阻断后续快照。
- **状态和依赖**：已验证，完成；普通 `Exception` 仍是可恢复的 `Result.failure`，独立取消/致命错误不伪装为普通失败，后续快照按完成顺序继续；写入作用域整体取消时仍取消待处理任务。
- **修改摘要**：队列尾部从业务结果 `Deferred<Result<Unit>>` 改为独立 `CompletableDeferred<Unit>` 完成屏障，使串行等待与每次写入结果解耦；仅将普通 `Exception` 转为失败结果并重抛取消。后台观察器直接等待结果，让取消与致命错误保留协程异常语义。新增四项回归测试覆盖取消传播、取消后续写入继续、作用域整体取消释放 `awaitIdle()`、致命错误传播。
- **验证及结果**：新断言先在旧实现上复现失败；修复后 `PersistenceWriteQueueTest` 8/8 通过。完整 `-PenableAppUnitTests=true :app:testBetaDebugUnitTest :architecture-tests:test` 成功，Beta 单测 402 项、架构契约 59 项均为 0 失败/错误；`git diff --check` 通过。使用 `D:\Barcode_build\run-barcode-android-gradle.ps1`；未打包 APK、未安装设备。
- **阻碍/恢复条件/下一步**：本地代码与自动化验证完成；没有真机/远端验证。本持续目标仍需继续检查 `:app` 其他系统边界及构建/发布配置；CI-092 的远端 artifact 重试需在用户授权推送后验证。

### CI-097 — 收藏备份 Activity 桥接把致命错误当成可恢复失败

- **检查范围**：收藏备份导出、解析、导入的 Activity 桥接结果封装，以及 SAF 输入文件长度探测的降级边界。
- **问题依据/优先级**：确认 P2 错误分类问题。三个顶层 `runCatching` 均包住收藏备份操作；按 Kotlin 语义会把 `AssertionError`、`OutOfMemoryError` 等 `Error` 转为普通 `Result.failure` 并显示为普通导入/导出失败。长度探测的内层 `runCatching` 也会把 `Error` 当成“长度未知”继续读取。CI-061 已核实真实生命周期取消会在回到主线程时受取消检查保护；本项补足明确的异常分类边界，不改变普通失败提示或操作时序。
- **状态和依赖**：已验证，完成；普通 `Exception` 继续返回失败结果供现有 UI 反馈；协程取消和致命 `Error` 原样传播；普通长度探测异常仍按未知长度降级，64 MB 有界读取不变。
- **修改摘要**：新增 `captureRecoverableFavoriteBackupOperation()` 并用于导出、解析、导入；仅捕获普通 `Exception`，显式重抛取消。长度探测改为仅降级普通异常。新增三项分类测试，覆盖普通异常、取消和 `AssertionError`。
- **验证及结果**：新测试在异常分类 helper 尚未实现时因未定义引用而编译失败；实现并接入实际三个操作后，分类测试与原有分享 ZIP 清理测试共 6 项通过。完整 Beta 单测 405 项、架构契约 59 项均 0 失败/错误；`verifyLocal -PenableAppUnitTests=true` 成功，191 项任务（25 项执行、1 项缓存、165 项 up-to-date），包含 Beta/正式版 Release 编译与 Lint。`git diff --check` 通过。使用本地工具目录 `D:\Barcode_build`；未打包 APK、未安装设备。
- **阻碍/恢复条件/下一步**：本地代码及自动化验证完成；实际 SAF 提供器异常和 Android 生命周期行为仍待设备演练。继续检查 `:app` 余下可本地验证边界；远端 workflow 行为仍需获准推送后验证。

### CI-098 — 正式版与 Beta 发布分配及工作流恢复边界复核

- **检查范围**：正式版/Beta 版本分配器、手动触发条件、目标分支、并发组、签名密钥生命周期、发布附件校验与失败恢复步骤。
- **问题依据/优先级**：检查版本重复、跨运行覆盖、失败后错误递增、未验证附件即公开、Beta/正式版触发范围互相污染等风险；当前没有发现新的已证实缺陷。正式版有固定并发组且禁止运行中取消；分配标记绑定 workflow run 并从未完成 Release 恢复；APK 与 SHA-256 都下载比对并校验后才发布。Beta 仅允许 beta 分支手动运行，固定 Release 使用独立并发组。两者分别使用受限的 contents 读权限与发布 Secret，临时 JKS 以 `if: always()` 清理。
- **状态和依赖**：已检查，无需修改；远端 Actions 实际触发、GitHub Release API/Secret 行为仍未验证，不能由静态检查替代。
- **修改摘要**：无生产代码、脚本或 workflow 修改；核对官方版本状态机和对应单测，并确认重试 artifact 覆盖契约由架构测试保护。
- **验证及结果**：官方版本分配器 15 项、Beta 版本分配器 5 项测试均通过；完整 Beta 单测 405 项与架构契约 59 项通过，`verifyLocal` 已编译 Beta/正式版 Release 并完成 Lint。仅本地验证，未触发 workflow、读取 Secret、上传 artifact、创建/修改 Release 或打包 APK。
- **阻碍/恢复条件/下一步**：远端重传恢复需用户授权推送后选择可恢复的 workflow run；在此之前不触发或修改远端状态。继续处理尚可本地验证的项目，设备和产品决策项仍独立列为待办。

### CI-099 — Manifest Activity 入口、FileProvider 授权与备份范围复核

- **检查范围**：应用 Manifest 的导出组件、FileProvider 路径和 URI 授权标志、实际文件共享调用点，以及云备份/设备迁移排除规则。
- **问题依据/优先级**：未发现新的已证实缺陷。`MainActivity` 唯一导出入口仅响应系统 Launcher；FileProvider 为 `exported=false`，仅对相机输出、图片/备份分享及 APK 安装的具体 URI 临时授予访问，未发现 URI 前缀授权。缓存目录映射范围较宽，但这些流程当前在同一缓存根目录或其子目录创建文件；仅凭路径映射宽不能证明外部应用可读取未授权文件。备份规则明确排除 LAN 附件及诊断数据，并保留用户库数据的备份行为。
- **状态和依赖**：已检查，无需修改。
- **修改摘要**：无产品代码、Manifest 或备份规则修改；持续改进记录新增本项。没有擅自收窄 FileProvider 路径，以免改变相机、分享、更新安装等现有系统交接行为。
- **验证及结果**：核对 Manifest、三个 XML 资源及所有 `FileProvider.getUriForFile` 调用点；通过 `D:\Barcode_build\run-barcode-android-gradle.ps1 --offline :architecture-tests:test --tests '*LanShareUriGrantContractTest' --tests '*MainActivityResultContractTest' --rerun-tasks`。两个契约类共 5 项测试通过，0 失败、0 错误；该任务不打包 APK。
- **阻碍/恢复条件/下一步**：设备上的相机/分享/安装器 URI 消费行为仍未演练；Manifest 的明文网络范围及历史 schema 样本仍按既有 CI-029/CI-081 待确认事项处理。继续检查与本项独立的 `:app` 本地可验证边界；不推送、不构建 APK。

### CI-100 — SAF 遗留授权迁移吞掉 JVM 致命错误

- **检查范围**：Application 启动时读取旧版持久 URI 授权及逐条释放授权时的异常分类、重试门闩行为。
- **问题依据/优先级**：确认 P2 稳定性缺陷。读取 `persistedUriPermissions` 和逐条 `releasePersistableUriPermission` 均使用 `runCatching`，会把 `AssertionError`、`OutOfMemoryError` 等 `Error` 转成普通读取/释放失败；读取错误被记录后继续启动，释放错误则被计为可重试失败并继续处理其他 URI，掩盖了程序级故障。门闩逻辑本身正确：只在所有释放均成功时标记完成，普通单项异常会在下次启动重试。
- **状态和依赖**：已验证，完成；普通 `Exception` 仍进入原有可重试路径；协程取消与非 `Exception` 的 `Throwable` 不再被降级。
- **修改摘要**：新增 `captureRecoverableDocumentUriGrantOperation`，仅捕获普通 `Exception`、显式传播取消；读取授权与逐项释放共用该异常分类。增加普通异常、取消、读取致命错误、释放致命错误回归测试，保留现有“单项普通失败不阻塞后续 URI”的测试。
- **验证及结果**：新增释放致命错误测试在旧实现下按预期失败；修复后迁移测试类 5/5 通过。随后使用 `D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline :app:testBetaDebugUnitTest :architecture-tests:test`，完整 Beta 单测 409 项、架构契约 59 项均通过，0 失败、0 错误。`git diff --check` 对本项 Kotlin 文件通过；没有组装 APK。
- **阻碍/恢复条件/下一步**：代码与本地回归验证完成，真机启动时 Android `ContentResolver` 的实际授权释放仍待设备验证；不推送、不构建 APK。继续检查与本项独立的 app/模块边界。

### CI-101 — 外部 Activity 启动把致命错误当成可恢复故障

- **检查范围**：Compose 根动作的统一诊断包装和 `MainActivity.launchExternalActivity()` 系统 Activity 启动失败处理。
- **问题依据/优先级**：Compose `traceAction()` 虽捕获 `Throwable` 用于记录，但随后会原样重新抛出，不会吞掉错误；外部 Activity 启动却使用 `runCatching`，会把所有 `Throwable` 转成“无法打开系统选择器”、清理请求状态并返回 `false`，掩盖 `Error` 等程序级故障。归为 P2 错误分类问题。
- **状态和依赖**：已验证，完成；普通 Activity 启动异常仍记录、清理单次请求并给出原有提示；非 `Exception` 错误不再伪装成普通失败。
- **修改摘要**：将 `launchExternalActivity()` 从 `runCatching` 改为仅捕获 `Exception` 的 `try/catch`；新增 `MainActivityResultContractTest` 契约，确保异常边界与请求清理行为不回退。
- **验证及结果**：新增契约在旧实现下按预期失败；修复后完整 Beta 单测 409 项、架构契约 60 项全部通过，0 失败、0 错误；`git diff --check` 通过。采用 `D:\Barcode_build\run-barcode-android-gradle.ps1`；未组装 APK。
- **阻碍/恢复条件/下一步**：源码与本地自动化验证完成，设备上真实 ActivityNotFound/系统后台限制仍未演练。继续审查其他可独立本地验证区域；保持目标活动。

### CI-102 — 未授予安装权限返回后待安装流程静默停住

- **检查范围**：未知来源安装权限提示、跳转系统设置、Activity 恢复和待安装 APK 路径生命周期。
- **问题依据/优先级**：确认 P2 更新流程可恢复性缺陷。用户点击“去设置”后提示弹窗先关闭；若返回时仍未授予权限，`MainActivity.onResume()` 原先只在权限已授予时消费待安装路径，未授予时不再显示操作入口，路径虽保留却没有可见提示，更新流程静默停住。系统清理缓存文件后，旧路径也会残留并在后续恢复时反复失效。
- **状态和依赖**：已验证，已修复；未授权返回会重新显示提示，已授权仍自动继续安装，取消/外部关闭会清除路径，缓存 APK 不存在或为空时清除失效路径并提示重新下载。
- **修改摘要**：`MainActivity` 在恢复时重新检查权限并重开提示；增加 Activity 级弹窗去重，避免同一提示重复出现。设置启动失败时恢复提示；待安装文件失效时清理其 SavedStateHandle 状态。
- **验证及结果**：新增恢复契约在旧实现下按预期失败，修复后通过；完整 Beta 单测 409 项、架构契约 61 项均为 0 失败、0 错误。使用 `D:\Barcode_build\run-barcode-android-gradle.ps1`；`git diff --check` 通过；未组装 APK。
- **阻碍/恢复条件/下一步**：设置页拒绝授权、开启授权后返回、取消弹窗及缓存文件被系统清理等真实系统交互仍需设备演练；当前无连接设备。继续检查可独立验证的 app/数据边界。

### CI-103 — APK 身份校验失败后残留未信任的下载文件

- **检查范围**：更新下载完成事件、APK 包名/版本/签名校验、系统安装器交接前后的文件生命周期。
- **问题依据/优先级**：确认 P2 缓存卫生缺陷。`DownloadReady` 原先在校验失败时只显示失败提示，没有删除已完整下载但未通过身份校验的 APK；该文件从未交给系统安装器，继续留在缓存无用途。删除边界必须严格限于校验失败，校验成功的 APK 仍需保留，供 Android 安装器异步读取。
- **状态和依赖**：已验证，完成；普通校验异常、协程取消及 JVM `Error` 均触发拒绝文件清理并原样传播；清理失败作为 suppressed 异常附加，不覆盖原校验错误；成功校验不删文件。普通校验失败仍由既有 UI 显示失败提示。
- **修改摘要**：新增 `validateDownloadedApkForInstall()`，统一封装校验失败后的尽力清理；下载完成调用点改为经该 helper 校验，并显式传播取消。新增 4 项文件生命周期单测（成功保留、普通异常删除、取消删除并传播、Error 删除并传播），并扩充事件调用链架构契约。
- **验证及结果**：新增架构契约在旧调用链下按预期失败（1 项失败）；修复后 helper 测试 4/4 通过。完整 Beta 单测 413 项、架构契约 62 项均 0 失败/错误；`D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline verifyLocal` 成功，191 个任务（5 执行、186 up-to-date），包含 Beta/Official Release 编译与 Lint；没有组装 APK。`git diff --check` 退出码 0，仅报告其他既有文件的 LF/CRLF 转换提示。
- **阻碍/恢复条件/下一步**：Android PackageManager 对真实损坏/错误签名 APK 的解析及安装器交接仍需设备或平台集成环境验证；当前无设备。继续检查与此独立、可由本地测试验证的模块，不提交、不推送、不构建 APK。

### CI-104 — `:app` 旧文本分享入口的异常捕获线索（已排除）

- **检查范围**：`BarcodeUi.shareText()` 对系统 chooser 启动异常的处理及其生产调用点。
- **问题依据/优先级**：初看 `runCatching` 会把 `Error` 也转换为普通分享失败；但全仓检索表明该 `internal` 方法只有定义，没有生产调用方，当前用户路径不可达，不能据此认定为运行缺陷。
- **状态和依赖**：已排除；只有找到实际调用点或重新接入该方法时，才需要重新评估异常边界。
- **修改摘要**：没有保留产品代码或测试改动。临时契约初次在现有源码上失败后，继续追踪调用关系发现测试目标未被生产使用，因此撤回临时 helper 与测试，避免保留死代码门禁。
- **验证及结果**：通过 `rg` 检索 Android 主源码和测试中 `shareText` 的全部引用，仅找到方法定义及本轮临时测试引用；临时测试和 helper 已撤回。CI-103 的验证结果不受本项影响。
- **阻碍/恢复条件/下一步**：无。继续寻找有真实生产调用路径且能本地验证的 `:app` 问题；仍不处理需用户决定或远端/设备条件的事项。

### CI-105 — `:app` 条码编码吞掉 JVM 致命错误

- **检查范围**：DI 实际使用的 `ZxingBarcodeImageRenderer.encode()` 编码异常边界及图片缺失回退。
- **问题依据/优先级**：确认 P2 稳定性/诊断缺陷。该渲染器由应用依赖注入供结果页使用；旧 `runCatching` 会捕获所有 `Throwable`，把 `OutOfMemoryError`、`AssertionError` 等转成 `null`，随后由上层当作普通条码图片缺失处理，掩盖 JVM 致命错误。ZXing 的普通编码异常仍应保持日志记录并返回空图的既有回退。
- **状态和依赖**：已验证，完成；成功编码照常返回结果，普通 `Exception` 仍记录并回退，取消和致命 `Error` 不再转换成空图。
- **修改摘要**：新增 `captureRecoverableBarcodeEncoding()` 明确异常分类，并在实际 ZXing 编码入口使用；新增成功、普通异常回退、取消传播、`Error` 传播 4 项单测；增加渲染器调用点架构契约。
- **验证及结果**：调用点契约在旧 `runCatching` 下按预期失败。修复后定向单测 4/4、调用点契约通过；完整 Beta 单测 417 项、架构契约 63 项均 0 失败/错误。`D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline verifyLocal` 成功，191 个任务（15 执行、176 up-to-date），包含 Beta/Official Release 编译及 Lint，不组装 APK。`git diff --check` 退出码 0，仅有其他既有文件的 LF/CRLF 提示。
- **阻碍/恢复条件/下一步**：当前验证覆盖异常分类与调用点；设备内存压力下的真实 Bitmap 分配失败仍未实机演练。继续审查 `:app` 未覆盖的可达业务边界，外部验证项独立追踪。

### CI-106 — `:app` 相机/图库位图解码吞掉致命错误且未回收部分结果

- **检查范围**：相机/图库 URI 的尺寸探测、Bitmap 解码、EXIF 方向读取/变换与失败回退；追踪 `MainActivity` 识别入口及 CI-043 既有用户反馈行为。
- **问题依据/优先级**：确认 P2 错误分类和资源生命周期缺陷。实际调用的 `decodeRecognitionBitmap()` 外层 `runCatching` 会把 `OutOfMemoryError`、`AssertionError` 等变为 `null`，被上层当成普通无图；若 Bitmap 已解码、随后 EXIF/方向变换抛错，也没有统一释放已创建的 Bitmap。EXIF 元数据普通读取异常回退为默认方向是既有兼容行为，应继续保留。
- **状态和依赖**：已验证，完成；普通解码异常仍返回无图并写入不含 URI/内容的诊断；EXIF 普通异常仍使用默认方向；取消与致命错误传播；失败时回收部分解码结果；成功 Bitmap 所有权交给调用方。
- **修改摘要**：新增 `captureRecoverableRecognitionImageDecode()` 管理异常分类与失败清理；将解码操作接入 helper，并跟踪尚未移交的 Bitmap；EXIF 读取只将普通异常降级，显式传播取消。新增 4 项边界单测及解码调用点架构契约，保留 CI-043 的失败诊断和用户提示。
- **验证及结果**：解码调用点契约在旧 `runCatching` 下按预期失败；修复后 helper 测试 4/4、原 CI-043 反馈契约与新调用点契约通过。全量 Beta 单测 421 项、架构契约 64 项均 0 失败/错误；`D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline verifyLocal` 成功，191 个任务（15 执行、176 up-to-date），包含 Beta/Official Release 编译及 Lint，不组装 APK。`git diff --check` 退出码 0，仅报告其他文件既有的 LF/CRLF 提示。
- **阻碍/恢复条件/下一步**：不同 OEM 图片提供器、真实低内存条件和设备侧 Bitmap native heap 回收仍需真机验证；当前设备列表为空。继续检查可本地验证的 `:app` 剩余路径，不提交、不推送、不构建 APK。

### CI-107 — `:app` 局域网建房把致命错误伪装为普通启动失败

- **检查范围**：设置页进入局域网分享时的同步 `startHostSession()` 调用、失败回滚、提示与导航。
- **问题依据/优先级**：确认 P2 异常分类缺陷。用户可达入口原以 `runCatching` 捕获所有 `Throwable`，包括 `CancellationException` 和 JVM `Error`，然后一律显示普通建房失败提示。失败时关闭可能已部分启动的会话并回到设置页是必要回滚，必须在收窄异常范围时保留。
- **状态和依赖**：已验证，完成；普通异常仍执行回滚并显示原提示；取消和非 `Exception` 错误先执行回滚，再原样传播，不转成普通失败反馈。
- **修改摘要**：提取 `captureRecoverableLanShareHostStart()`，区分普通异常、取消与致命错误，并在各失败路径回滚；`enterLanShare()` 接入 helper，保留原来的网络错误提示、普通失败提示和回设置页行为。增加 4 项结果/异常边界单测及生产调用点架构契约。
- **验证及结果**：新调用点契约在旧 `runCatching` 代码上按预期失败；修复后定向单测 4/4、调用点契约通过。完整 Beta 单测 425 项、架构契约 65 项均 0 失败/错误；`D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline verifyLocal` 成功，191 个任务（15 执行、176 up-to-date），包含 Beta/Official Release 编译及 Lint，不组装 APK。`git diff --check` 退出码 0，仅报告其他文件既有的 LF/CRLF 提示。
- **阻碍/恢复条件/下一步**：真实 Wi-Fi/服务端口占用、权限撤销场景仍需设备验证；当前设备列表为空。继续检查 `:app` 其他可达路径；不提交、不推送、不构建 APK。

### CI-108 — `:app` 收藏备份分享启动吞掉取消与致命错误

- **检查范围**：收藏备份 ZIP 生成后启动 Android 系统 chooser、异常时清理临时备份并向用户反馈。
- **问题依据/优先级**：确认 P2 异常分类缺陷。该代码位于真实“分享收藏备份”按钮路径；原 `runCatching` 会把 `CancellationException` 与 JVM `Error` 也转换为“无法打开分享面板”，导致异常语义被掩盖。失败时清理未成功交接的暂存 ZIP 是必要行为，需保留。
- **状态和依赖**：已验证，完成；chooser 成功时保留 ZIP 供接收方读取；普通异常清理文件并保留既有日志/提示；取消和致命错误也执行清理后原样传播。
- **修改摘要**：新增 `launchFavoriteBackupChooser()` 封装 chooser 启动、失败清理与异常分类，并接入实际按钮回调。增加 4 项边界单测和生产调用点架构契约。
- **验证及结果**：新调用契约在旧 `runCatching` 代码上按预期失败；修复后 helper 测试 4/4、调用点契约通过。完整 Beta 单测 429 项、架构契约 66 项均 0 失败/错误；`D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline verifyLocal` 成功，191 个任务（15 执行、176 up-to-date），包含 Beta/Official Release 编译及 Lint，不组装 APK。`git diff --check` 退出码 0，仅报告其他文件既有的 LF/CRLF 提示。
- **阻碍/恢复条件/下一步**：跨应用分享目标真实读取 ZIP 的设备行为仍未演练；当前设备列表为空。继续审查 app 中有真实调用点且可本地验证的边界，不提交、不推送、不构建 APK。

### CI-109 — `:app` 其余宽异常捕获与辅助入口复核（无需修改）

- **检查范围**：当前 `:app` 主源码中剩余 `runCatching` / `catch(Throwable)` 调用、其生产调用关系、清理与诊断边界。
- **问题依据/优先级**：没有发现新的已证实 P1/P2 问题。结果图像/条码资源清理、LAN 下载回滚和操作诊断包装均在释放资源或记录错误后重新抛出；`DebugLog`/诊断写队列隔离的是非关键日志失败；SAF 目标删除是失效传输后的尽力清理并有诊断；附件显示名读取失败回退为通用名称。`BarcodeUi.shareText()` 与 `writeBitmapToUri()` 当前仅有定义、没有生产调用，不能算作用户可达故障。
- **状态和依赖**：已检查，无需修改；若上述未调用 helper 以后重新接入，需重新核对异常策略与调用契约。
- **修改摘要**：没有产品代码或测试修改；避免把日志隔离、回滚清理和通用显示名回退一概改成抛错，也未删除可能留作兼容的旧 helper。
- **验证及结果**：检索 `:app` 主源码中的剩余异常捕获及引用；复核对应调用上下文。最近完整 Beta 单测 429 项、架构契约 66 项均 0 失败/错误，`verifyLocal -PenableAppUnitTests=true --offline` 成功（191 项任务，15 执行、176 up-to-date），未组装 APK；全局 `git diff --check` 退出码 0，只有其他既有文件的换行风格提示。
- **阻碍/恢复条件/下一步**：`:app` 代码层的本地可验证高风险路径已基本检查；未解决事项集中于真实 Android PackageManager/SAF/低内存设备验证、历史上限/明文网络/后台运行等用户决策，以及远端 workflow 执行。继续按进度中的待确认与外部验证条件推进，不提交、不推送、不构建 APK。

### CI-110 — Beta 同一 workflow run 重试时重复递增 versionCode

- **检查范围**：Beta Release 已由 `action-gh-release` 更新、但后续 Release 元数据刷新失败时，同一个 GitHub Actions run 的重跑版本分配与工作流参数交接。
- **问题依据/优先级**：确认 P2 发布恢复一致性缺陷。Release 正文包含当前 `versionCode` 和 `workflowRun`，元数据刷新步骤位于发布动作之后；若刷新失败后重跑，原分配器只读取 `versionCode` 并无条件加一。最小内存复现：正文中的 `versionCode: 13` 与当前 run `12345` 被再次分配为 14；这不是不同手动发布的行为。
- **状态和依赖**：已验证，完成（本地）；不同手动 run 仍正常递增。远端 Actions 的发布后失败恢复尚未实跑，需在该改动获准推送并有可复用/可模拟的 Beta run 后验证。
- **修改摘要**：版本分配器新增可选 `run_id`；当 Release 正文恰有一个有效 `workflowRun` URL 且 ID 与当前 run 相同，沿用已发布的 versionCode；旧正文、不同 run 或不唯一的 run 标记仍按既有逻辑递增。Beta workflow 查询已有 Release 时传入 `GITHUB_RUN_ID`；新增 Python 回归测试及 workflow 接线架构契约。
- **验证及结果**：新 Python 用例在旧实现上因不支持 run ID 而失败；内存复现确认旧行为 13→14。新增 workflow 接线契约在旧 YAML 下失败，接线后通过。Beta 版本脚本测试 7/7 通过，架构契约 67/67 通过；固定本地工具执行 `verifyLocal -PenableAppUnitTests=true --offline` 成功，191 项任务（4 执行、187 up-to-date），Beta 单测 429 项全部通过，覆盖两种 Release 变体编译与 Lint；未组装 APK、未运行远端 Actions。
- **阻碍/恢复条件/下一步**：真实 GitHub Release 重试需先获得推送授权，当前不推送；继续核查尚未完成的发布工作流恢复和 `:app` 系统边界，不重复检查已验证功能。

### CI-111 — `:app` GlassBackdrop RenderNode 重入崩溃的回归保护

- **检查范围**：`recordGlassBackdrop()` 的 GraphicsLayer 录制生命周期、实际 Compose 调用位置及渲染异常回归。
- **问题依据/优先级**：用户 2026-10-07 提供的 `193714`、`195130` 两份日志均在 `result_edit` 事件后记录 `IllegalStateException`，堆栈进入 `GraphicsLayer.record()` / `RenderNode.beginRecording()`，与本项相同；`190650` 未记录该崩溃。当前实现已有 `AtomicBoolean` 重入保护，失败分支直接绘制子内容，避免对同一 RenderNode 再次开始录制；生产代码只有 `ComposeAppShell` 页面容器这一处录制入口。日志证明故障曾发生，但不能证明当前版本已在真机上不再触发。
- **状态和依赖**：代码回归保护已验证，真机待验证。相同 GraphicsLayer 的嵌套录制在 Robolectric 原生图形测试中可稳定触发；保留保护时安全，移除保护时抛出 `IllegalStateException`。真实设备 GPU/页面切换仍需设备验证。
- **修改摘要**：新增 Compose 原生绘制回归测试，嵌套使用同一 `GlassBackdropSource` 并显式捕获渲染图像，确保测试确实执行绘制。生产绘制逻辑未改动。
- **验证及结果**：新回归测试在临时关闭保护的变异版本下失败，恢复原子保护后通过（1/1）。2026-10-23 使用固定脚本在当前工作区强制重跑 `:app:testBetaDebugUnitTest --tests '*GlassBackdropRecordingTest' --rerun-tasks`，结果 1/1、0 失败/错误；既有完整验证为 `verifyLocal -PenableAppUnitTests=true --offline` 成功，191 项任务（4 执行、1 缓存、186 up-to-date），Beta 单测 430 项、架构契约 67 项、Domain 23 项、Data 66 项、LAN Share 66 项均 0 失败/错误；未组装 APK。尚无本轮真机验证证据。
- **阻碍/恢复条件/下一步**：需连接 Android 设备，在重复切换页面/长按菜单及应用前后台切换过程中观察是否再出现该 RenderNode 异常；在此之前不宣称真机端已彻底验证。继续处理与设备验证无关的待办。

### CI-112 — 更新器版本解析与正式版无上限 SemVer 规则不一致

- **检查范围**：`:core:domain` 的 `UpdateSecurity` 版本解析/比较、`:core:data` 的更新 Release tag 解析，以及正式版版本分配脚本和 APK 资产命名规则。
- **问题依据/优先级**：确认 P3 兼容性缺陷。正式发布器接受无位数上限的 MAJOR.MINOR.PATCH 数字段并拒绝前导零；Android 更新器此前将段限制为 9 位并转成 `Long`，Release tag 解析器也有独立的 9 位/`Long` 限制。达到该实现上限后，合法版本会被拒绝或比较错误，客户端无法正确发现更新。
- **状态和依赖**：已验证，完成（本地）；定向测试及全量 `verifyLocal` 均通过。Windows Robolectric SQLite 测试需采用 CI-053 记录的短 Java 临时路径；本轮通过一次性进程级覆盖完成验证，未改验证工具脚本。
- **修改摘要**：版本段保留为十进制字符串，通过长度再按字典序比较，消除固定整数范围；更新 tag 解析复用领域版本验证，仍保留 Beta 第四段 build 序号兼容和前导零拒绝。添加大于 9 位及大于 `Long` 范围的 Domain、Data 回归测试。
- **验证及结果**：新增回归测试先在旧边界实现下失败，修改后通过。`UpdateSecurityTest` 5/5、`UpdateApkAssetNameTest` 6/6、正式版版本分配脚本测试 15/15 通过；全量 Beta 单测 430 项、Domain 25 项、Data 68 项、LAN Share 66 项和架构契约 67 项均为 0 失败/错误。固定脚本的 `verifyLocal -PenableAppUnitTests=true --offline` 成功，191 项任务（6 执行、1 缓存、184 up-to-date），覆盖两种 Release 变体编译与 Lint；默认临时目录的 SQLite 问题通过进程级短路径覆盖解决。`git diff --check` 对本次四个源码/测试文件退出码 0，仅有换行转换提示；未组装 APK。
- **阻碍/恢复条件/下一步**：本地代码级问题和完整门禁均已验证；真实 Release 元数据、APK 安装器更新流程尚未真机/远端演练，因本目标未授权推送或发布而保留。继续检查未完成的发布恢复与 app 系统边界；不提交、不推送、不构建 APK。

### CI-113 — LAN host 启动期间 WebSocket 连接状态同步线索（已排除）

- **检查范围**：`:app` 的 `LanShareViewModel.startHostSession()` 初始快照读取与 `observeRealtimeState()` collector；核对 `:core:lan-share` 的 NanoHTTPD WebSocket 连接事件线程。
- **问题依据/优先级**：初步怀疑初始 getter 读取期间 browser 连接事件被 `isHost` 守卫丢弃。实际 `BrowserWebSocket.onOpen()` 在服务端工作线程触发，`LanShareManager` 将连接变化写入 `StateFlow`；主线程同步完成 session/isHost 状态更新后 collector 读取最新快照，因此该事件不会丢失。
- **状态和依赖**：已排除产品缺陷；不依赖产品代码变更。保留测试以验证后台实时事件的状态最终收敛。
- **修改摘要**：没有产品代码改动。新增 ViewModel 回归测试，模拟服务端工作线程在初始快照读取期间发出 `browserConnected=true`，检查 host UI 最终显示已连接。
- **验证及结果**：定向测试通过：`D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline :app:testBetaDebugUnitTest --tests '*LanShareViewModelStartupRealtimeTest'`。未构建 APK，未修改本地验证工具。测试早期失败来自主线程重入/Looper 调度方式，按生产后台线程模型及项目现有有界 Looper 泵送方式后通过。
- **阻碍/恢复条件/下一步**：若未来连接事件改为同步在 Main 线程发出，需重新评估 `isHost` 守卫与启动状态交接。继续检查构建/发布配置尚未本地验证的恢复场景；不提交、不推送、不构建 APK。

### CI-114 — 恢复已公开但附件不完整的正式版时先转草稿

- **检查范围**：正式版 Release 完整性判定、恢复请求保留发布可见性的逻辑，以及“仅在 APK 与校验文件上传并验证后发布”的发布规则。
- **问题依据/优先级**：用户已明确要求“只有在构建产物和校验文件都上传并验证成功后，才正式发布 Release”。旧逻辑将缺少 APK 或 SHA-256 的已发布 Release 判为 incomplete 并恢复，却保留 `draft=false`；这会在附件重新验证期间继续公开部分 Release，违反上述规则。GitHub REST 文档明确说明更新 Release 时 `draft=true` 会转为草稿，`draft=false` 发布 Release。参考：[Update a release](https://docs.github.com/en/rest/releases/releases#update-a-release)。
- **状态和依赖**：已修复，脚本级自动化验证通过；远端实际 Release API 往返仍未执行。
- **修改摘要**：所有新建或恢复中的 Release 在 reserve-request 阶段都保持草稿，包括曾经公开但现在附件不完整的 Release；恢复仍使用原有版本号和 versionCode。既有 publish-request 仍只在 workflow 下载并比对 APK 与 SHA-256 文件、验证上传状态后把 Release 标记为 complete 并重新公开。更新回归测试，验证“已发布 incomplete → 草稿恢复 → 校验后重新公开”完整状态契约。
- **验证及结果**：回归测试先按预期失败，暴露 `draft=false`；修复后正式版版本脚本 15/15、Beta 与正式版版本分配脚本合计 22/22 全部通过。使用本机 Python 运行 `python -m unittest discover -s tools -p 'test_*release_version.py' -v`；未修改本地 Gradle 验证工具，未调用 GitHub Release API，未触发 workflow，也未构建 APK。
- **阻碍/恢复条件/下一步**：代码与本地契约已验证；GitHub 托管端把已发布 Release 改为草稿、重新上传并验证后再公开的真实流程仍待授权推送后的 Actions 验证。继续处理可本地验证事项；设备侧验证仍无连接设备。

### CI-115 — 缺少 SHA-256 的新版本仍显示为可更新

- **检查范围**：`:core:data` 更新目录响应解析、`UpdateLookupResult.Available` 与 APK 下载器的摘要校验前置条件。
- **问题依据/优先级**：确认 P2 更新流程状态不一致。Release 有较新版本和 APK、但正文与 GitHub asset metadata 都没有有效 SHA-256 时，更新目录原先仍返回 `Available(..., expectedSha256 = null)`；下载器随后必然因缺少校验值拒绝该 APK，用户先看到“有更新”再在下载流程中失败。
- **状态和依赖**：已验证，完成（本地）；更新状态在下载前即与安全校验条件保持一致。
- **修改摘要**：将 Release JSON 解析抽为可直接测试的内部函数；新版本缺少 SHA-256 时返回明确的 `Failed`，有效摘要仍优先采用 Release 正文值，并保留 GitHub asset `digest` 回退。新增 3 项 Robolectric 回归测试，覆盖缺摘要拒绝、正文摘要和 asset digest。
- **验证及结果**：新回归测试先在旧行为下按预期有 1 项失败、另 2 项通过；修复后定向测试 3/3 通过。完整 `:core:data:testDebugUnitTest :core:domain:test` 成功；`D:\Barcode_build\run-barcode-android-gradle.ps1 verifyLocal -PenableAppUnitTests=true --offline` 成功，191 项任务（30 执行、1 缓存、160 up-to-date），包含 Beta 单测、架构测试、Beta/正式版编译与 Release lint，不组装 APK。`git diff --check` 退出码 0，仅显示已有文件换行风格提示；未改本地验证工具。
- **阻碍/恢复条件/下一步**：代码与本地验证完成；真实 GitHub Release 元数据仍需远端验证（浏览接口无法读取公开 API URL），且本目标当前未授权推送。CI-114 的恢复策略已依照既有发布规则修复，GitHub 托管端实际效果待 Actions 验证。继续处理可独立本地验证事项。

### CI-116 — 正式版并发组默认队列会替换等待中的手动发布

- **检查范围**：正式版 `workflow_dispatch`、workflow 级 `concurrency` 策略及版本递增契约。
- **问题依据/优先级**：确认 P2 发布可靠性缺陷。workflow 原有固定并发组和 `cancel-in-progress: false` 能防止两个发布同时运行，但未设置 `queue`，因此 GitHub Actions 使用默认 `queue: single`：运行中已有一个发布时，新的 pending run 会替换先前 pending run；连续手动触发可能导致某次正式发布不执行，违反每次手动启动都分配版本的预期。GitHub 官方文档说明 `queue: max` 可保留最多 100 个 pending run；处理顺序按进入等待状态的时间，dispatch 顺序不作严格保证。参考：[GitHub Actions 并发与队列](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency)、[Actions 队列限制](https://docs.github.com/en/actions/reference/limits)。
- **状态和依赖**：已验证，完成（本地配置契约）；GitHub 托管端实际排队行为仍待远端 workflow 验证。
- **修改摘要**：正式版 concurrency 组新增 `queue: max`，继续保留 `cancel-in-progress: false`；新增架构契约，固定并发组、保留 pending 队列及运行中发布不可取消三项要求。
- **验证及结果**：新契约在旧配置下按预期失败，加入 `queue: max` 后通过。`D:\Barcode_build\run-barcode-android-gradle.ps1 --offline :architecture-tests:test --tests '*OfficialReleaseWorkflowQueueContractTest'` 成功；随后 `verifyLocal -PenableAppUnitTests=true --offline` 成功，191 项任务（33 执行、158 up-to-date），包含 Beta 单测、架构测试、Beta/正式版编译与 Release lint，不组装 APK。未触发远端 workflow。
- **阻碍/恢复条件/下一步**：GitHub 并发队列最多接受 100 个 pending run，且不保证严格按 dispatch 顺序；远端 Actions 结果需在授权推送后核验。CI-114 的恢复流程也需在授权推送后验证托管端 API 行为；继续独立检查尚未完成的模块。

### CI-118 — Gradle Wrapper 发行包未校验摘要且启动器版本不一致

- **检查范围**：Wrapper properties、`gradlew`/`gradlew.bat` 启动器、`gradle-wrapper.jar` 版本与摘要；核对 Gradle 官方发行摘要和 GitHub `setup-gradle` 的 Wrapper JAR 验证。
- **问题依据/优先级**：确认 P2 构建供应链缺口及 P3 版本不一致。原配置只固定 `gradle-9.5.0-bin.zip` 的 HTTPS URL、没有 `distributionSha256Sum`，Wrapper 无法将新下载的发行包与官方摘要比对。仓库 JAR 实测摘要为 `498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17`，与 Gradle 官方 8.9 Wrapper JAR 一致，但 properties 指向 Gradle 9.5.0；这是旧版官方 JAR 而非未知/篡改 JAR。Gradle 官方说明 `distributionSha256Sum` 可阻止被篡改的发行包执行，并且只有首次下载尚未缓存的发行包时执行摘要比对。分发 ZIP 与 Wrapper JAR 是两项独立校验。
- **状态和依赖**：已验证，完成（本地配置契约与启动路径）；本地 Gradle 9.5.0 已缓存，未清理或改动缓存，因此本轮未强制重新下载并验证 ZIP 摘要拒绝路径。GitHub runner 新下载校验仍待推送授权。
- **修改摘要**：设置 Gradle 9.5.0 binary distribution 官方 SHA-256：`553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746`；使用固定本地 Wrapper 任务将 JAR、POSIX/Windows 启动脚本更新为 Gradle 9.5.0 生成版本，Properties 同步默认重试属性。新增架构契约验证发行 URL、发行包摘要和 Wrapper JAR 摘要；将 properties 和 JAR 声明为测试输入，配置变化会触发重新验证。未清理或改动依赖缓存。
- **验证及结果**：新增契约先在旧配置下因缺发行摘要失败，再在旧 JAR 版本时因 JAR 摘要不符失败；更新官方 Wrapper 后定向测试通过，实测 JAR SHA-256 为 `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`，与 Gradle 官方 9.5.0 值一致。首个 Wrapper 更新进程在任务成功后因启动脚本正在自我替换而返回异常；重新启动后定向测试和完整 `verifyLocal -PenableAppUnitTests=true --offline` 均通过，后者 191 项任务（4 执行、187 up-to-date），覆盖 Beta 单测、架构检查、Beta/Official Release 编译与 Lint，不组装 APK。`git diff --check` 退出码 0；Git 仅提示本次改动的 Wrapper properties 和 `gradlew` 存在 LF/CRLF 转换。
- **阻碍/恢复条件/下一步**：本地配置及 Wrapper 启动验证完成，但缓存命中的发行包未被强制重新下载；可在未来隔离的新环境首次运行时验证实际摘要拒绝行为。GitHub 托管 runner 同样待此次改动获推送授权后验证。继续审查其他未决模块，不提交、不推送、不发布。

### CI-119 — 明确 Beta 稳定 Release 的最新等待构建策略

- **检查范围**：Beta workflow 的固定并发组、`cancel-in-progress` 设置、稳定 Release 覆盖行为和版本分配语义。
- **问题依据/优先级**：原 workflow 依赖 GitHub 默认 `queue: single`，其“新 pending 替换旧 pending”行为没有在配置中显式声明；稳定 Beta Release 的目标是避免旧提交随后覆盖新版本。用户确认只保留最新等待构建，因此这是明确策略与防误改的维护性优化，不是已证实的功能缺陷。
- **状态和依赖**：已验证，完成（本地配置契约）；GitHub 托管端实际排队行为待获准推送后复验。
- **修改摘要**：Beta workflow 显式设置 `queue: single` 并用中文注释说明最新等待构建策略；新增架构契约固定分支并发组、单个 pending 槽和运行中任务不取消。
- **验证及结果**：新增契约在未声明 `queue: single` 时按预期失败；配置和契约完成后，定向测试通过。固定脚本 `D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline verifyLocal` 成功，191 项任务（4 执行、187 up-to-date），不组装 APK。普通权限下多次因 daemon localhost IPC 失败，按项目规则授权后使用同一固定脚本重试通过。2026-10-22 用户再次确认仅保留最新等待构建；本轮在当前工作树复跑 `D:\Barcode_build\run-barcode-android-gradle.ps1 --offline :architecture-tests:test --tests '*BetaReleaseWorkflowQueueContractTest' --rerun-tasks` 成功（3 项任务执行）。首次沙箱内重跑仍因客户端无法连接本机 daemon 失败，按授权沙箱外用相同固定脚本与参数重试通过。GitHub 官方文档确认单 pending 槽的新任务替换行为；`queue: max` 最多 100 个等待项且顺序未必与 dispatch 顺序一致。参考：[Control the concurrency of workflows and jobs](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency)、[Actions limits](https://docs.github.com/en/actions/reference/limits)。
- **阻碍/恢复条件/下一步**：托管端行为仍需获准推送后验证；继续与此独立的 `:app` 检查，不提交、不推送、不构建 APK。

### CI-120 — `:app` 高风险集合访问检查

- **检查范围**：主源码中的 `first()`、`single()` 与显式空值断言，核实调用前置条件和失败处理。
- **问题依据/优先级**：抽查到的 `first()` 均在非空分支或数量已校验的分支内；解析器的 `single()` 前先确认只有一个候选项；图片解码的 `checkNotNull` 位于捕获渲染异常并报告失败的路径。未确认可触发的崩溃缺陷。
- **状态和依赖**：已检查，无需修改；本项为静态调用路径核对。
- **修改摘要**：无产品代码改动。
- **验证及结果**：结合生成调用关系与既有 `:app` 测试审阅，未发现未保护的访问；没有为无证据的风险增加防御性分支。
- **阻碍/恢复条件/下一步**：若后续新增集合访问或现场日志指出特定异常，再按具体输入补回归测试。继续收藏状态一致性检查（CI-121）。

### CI-121 — 收藏组变更后当前列表顺序与数据库不一致

- **检查范围**：收藏组重命名/移动、文件夹重命名、收藏保存/编辑、收藏条码编辑/删除时 `savedAt` 更新与 Compose 树投影；对照 Room `ORDER BY savedAt DESC, id DESC`。
- **问题依据/优先级**：确认 P2 可见顺序不一致。各变更路径更新内存组的 `savedAt`，但没有重排 `LibraryStateStore.groups`；树投影保留输入顺序，因此当前页面仍显示旧顺序，重载后 Room 则按新时间排序。新增的真实重命名到树投影回归测试在旧实现下稳定失败。
- **状态和依赖**：已验证，完成（本地）；无外部依赖。
- **修改摘要**：在 `LibraryStateStore.Editor` 集中提供 `savedAt DESC, id DESC` 排序，并在所有更新收藏组时间戳的事务内排序，涵盖文件夹/收藏组重命名与移动、收藏新建/覆盖、条码编辑/删除。扩展测试覆盖重命名后 Compose 树顺序、条码编辑后的组顺序，以及删除造成的同时间戳 ID 次序。
- **验证及结果**：重命名回归在修复前按预期失败；修复后收藏排序和条目变更测试通过，并新增分页回归确认重命名把原最旧组移到列表顶部后，下一页游标仍指向剩余已加载项中的最旧组。三个定向测试筛选共 8 项通过。完整 `D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline verifyLocal` 成功，191 项任务（4 执行、187 up-to-date），包含架构测试、Beta 单测、Beta/Official Release 编译与 Lint，不组装 APK；`git diff --check` 退出码 0。
- **阻碍/恢复条件/下一步**：ADB 服务未能启动，本轮未做真机验证；更新之后通过真机查看收藏组重命名/条码编辑后的即时顺序可补充确认。继续检查 `:app` 其余高影响状态交接，不提交、不推送、不构建 APK。

### CI-122 — 系统权限回调状态交接

- **检查范围**：`CameraRequestCoordinator` 中待处理请求码/权限集合的 `SavedStateHandle` 保存、单次消费，以及拒绝或部分授予时的 fail-closed 处理；对照相机、图库及文件选择入口和现有回归测试。
- **问题依据/优先级**：未确认可复现缺陷。待处理请求信息在 Activity 重建后由 `SavedStateHandle` 恢复，回调消费后清除；缺少权限或部分授权不会继续执行受保护操作。现有 JVM 测试覆盖协调器状态及权限授予策略。
- **状态和依赖**：已检查，无需修改；真实 `ActivityResultRegistry` 在系统进程回收后恢复回调的设备行为仍未验证。
- **修改摘要**：无代码修改。
- **验证及结果**：静态追踪权限请求、回调分派和清理路径，并复核 `CameraRequestCoordinatorTest` 与 `PermissionGrantRegressionTest` 的相关覆盖；完整 Beta App 单测 435 项、0 失败/错误（含这两组测试），没有发现状态重复执行或权限拒绝后继续操作的可证实路径。
- **阻碍/恢复条件/下一步**：需 Android 设备/系统进程回收场景补验；连接设备后可通过发起相机或图库授权、系统回收 Activity 后恢复应用验证。继续检查导航路由状态交接（CI-123）。

### CI-123 — 从设置进入局域网分享覆盖原始返回路由

- **检查范围**：设置页返回目标的写入点、进入局域网分享的统一桥接入口，以及 `MainActivity` 处理局域网分享返回和设置页返回的路由链。
- **问题依据/优先级**：确认 P2 导航行为错误。用户从生成页进入设置后，`settingsReturnPage` 保存为 `Generate`；`enterLanShare()` 又把它覆盖为 `Settings`。从局域网分享返回设置页后，再执行设置页返回便导航到 `Settings` 自身，而不是原来的生成页。
- **状态和依赖**：已验证，完成（本地）；设备返回手势/系统 Back 真机交互仍待设备验证。
- **修改摘要**：移除 `enterLanShare()` 对设置返回目标的多余赋值，让设置页只在用户首次进入设置时确定返回页；新增导航状态回归测试和架构契约，防止 LAN 入口重新覆盖该目标。
- **验证及结果**：导航状态测试在显式模拟原覆盖时按预期失败；架构契约在修复前因入口包含覆盖赋值而失败。删除覆盖并还原真实路由状态后，两个定向测试通过。随后无筛选运行完整 App 单测 435 项和架构契约 72 项，均 0 失败/错误；完整 `D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline verifyLocal` 成功，191 项任务（14 执行、177 up-to-date），覆盖 Beta/Official Release 编译与 Lint，不组装 APK。`git diff --check` 退出码 0；Git 仅报告既有 LF/CRLF 转换提示。
- **阻碍/恢复条件/下一步**：本地验证无阻碍；ADB 服务仍未启动，未做设备 Back 手势验证。继续 `:app` 剩余状态交接和模块清单中的未完成项；不提交、不推送、不构建 APK。

### CI-124 — Activity 路由与结果页状态恢复边界

- **检查范围**：`MainActivity` 的 Bundle 保存/恢复条件、`AppNavigationViewModel` 的配置变更行为、主路由选中状态同步，以及结果页 `SavedStateHandle` 中稳定 ID/返回路由的恢复。
- **问题依据/优先级**：未确认可复现缺陷。配置变更时 ViewModel 保留当前 UI 状态；进程重建时 ViewModel 初始路由为 `Generate`，Activity 将 Bundle 中的页面与设置返回目标恢复到导航状态。结果页不把整份条码对象放入 Bundle，而按稳定 ID 重载，并保存来源路由；找不到记录时安全地拒绝构造不完整结果。
- **状态和依赖**：已检查，无需修改；Activity/系统进程恢复的真实设备时序仍需设备验证。
- **修改摘要**：无代码修改。
- **验证及结果**：静态追踪 `onSaveInstanceState`、`onCreate`、`NavigationStateCoordinator` 与 `SavedResultState` 的写入/读取路径；无筛选 App 单测 435 项及架构契约 72 项均通过，包含导航与结果描述符恢复的纯逻辑测试。
- **阻碍/恢复条件/下一步**：当前 ADB 服务未启动，无法执行系统进程回收演练。设备可用后，从历史/收藏进入结果页并触发系统回收，验证结果内容和返回目标。继续检查其他 `:app` 生命周期及系统结果交接，不提交、不推送、不构建 APK。

### CI-125 — 收藏列表分页失败未捕获

- **检查范围**：收藏列表滚动触发的 `FavoritesViewModel.loadMoreFavoriteGroups()`，经 `FavoritesQuerySession`、分页协调器到 Repository/Room 的错误传播，以及页面重试呈现。
- **问题依据/优先级**：确认 P1 稳定性问题。普通收藏分页请求在 `viewModelScope.launch` 中没有异常处理；Room/Repository 读取失败会作为未处理异常交给协程异常处理器，可能导致应用崩溃，且页面没有分页局部重试状态。新增实际 ViewModel/Robolectric 回归测试，在旧实现上观测到未捕获的 `UndeclaredThrowableException`（代理抛出的 Repository `IOException`）。
- **状态和依赖**：已验证，完成（本地）；取消仍按结构化并发传播。
- **修改摘要**：分页调用仅捕获普通 `Exception` 并保留 `CancellationException` 传播；日志仅记查询是否非空，不记录查询文本；增加分页失败状态、映射到 UI，并在列表末尾展示单独的“加载失败，点击重试”，成功后清除失败状态。
- **验证及结果**：新增回归验证异常不逃逸、诊断被记录、失败态可见、重试可再读且成功清除失败态；定向测试通过。无筛选 Beta App 单测 436 项、架构测试 72 项均 0 失败/错误。完整 `D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline verifyLocal` 成功，191 项任务（15 执行、176 up-to-date），覆盖架构/核心/App 单测、Beta/Official Release Kotlin 编译与双通道 Lint，不组装 APK。
- **阻碍/恢复条件/下一步**：代码与本地验证无阻碍；未运行设备界面验证。继续检查 `:app` 直接相邻的收藏搜索/分页状态切换及其他高影响模块；不提交、不推送、不构建 APK。

### CI-126 — 收藏搜索与持久化快照重载交接

- **检查范围**：收藏备份导入后的共享快照重载、`FavoritesQueryCoordinator.resetPaging()` 对活动搜索的清理，以及 `FavoritesContent` 重新发起搜索的条件。
- **问题依据/优先级**：确认 P2 可见状态错误。快照加载递增 `LibraryLoadMetadata.generation`；`FavoritesViewModel` 收到后调用 `resetPaging()`，清空 `activeSearch` 和搜索结果并发布空搜索状态。页面确实会重组，但搜索副作用只以查询和结构相等的收藏快照为键；若刷新前后收藏内容相同，effect 不重启，搜索结果会停留为空。
- **状态和依赖**：已验证，完成（本地）；不依赖设备或产品决策。
- **修改摘要**：通过 `FavoritesViewModel.snapshotMetadata` 将已有快照代次传给 `FavoritesContentState.searchRefreshGeneration`，并加入搜索 `LaunchedEffect` 的 key。相同收藏数据再次加载时也会重新发起当前查询；未改搜索协议、分页行为或持久化数据。
- **验证及结果**：新增实际 `FavoritesContent` Robolectric 回归测试。修复前连续 3 次得到同一红灯：预期第 2 次查询、实际仍只有 1 次；修复后定向测试通过。`FavoritesImportRefreshTest` 另断言实际导入刷新暴露递增代次；两项定向测试均通过。完整离线 `verifyLocal -PenableAppUnitTests=true --offline` 成功（191 项任务，17 执行、174 up-to-date），包含 Beta/Official Release 编译与 Lint、架构检查和模块测试。未组装 APK、未做真机验证。
- **阻碍/恢复条件/下一步**：无本项阻碍；继续审查 `:app` 独立路径。

### CI-127 — 旧版条码迁移吞掉协程取消和 JVM 致命错误

- **检查范围**：`LegacyBarcodeDataMigrator` 读取迁移门闩、读取旧条码偏好以及调用 Room 迁移事务时的异常分类和偏好清理时机。
- **问题依据/优先级**：确认 P2 稳定性/迁移可靠性问题。迁移器用 `runCatching` 包住可挂起的 Repository 迁移调用，会把 `CancellationException` 当作“迁移失败”返回；同一组 `runCatching` 还会吞掉 `AssertionError`、`OutOfMemoryError` 等 `Error`。取消时上层加载任务无法及时停止，程序级故障会被伪装成普通失败；旧偏好未删除虽保住重试源，但错误语义和生命周期边界被破坏。
- **状态和依赖**：已验证，完成（本地）；依赖 `:core:domain` 的 `BarcodeDataMigration` 与 Repository 迁移契约，不改变数据格式或迁移成功条件。
- **修改摘要**：迁移门闩/旧偏好读取及 Room 迁移只降级普通 `Exception`；显式重抛协程取消，JVM `Error` 自然传播。迁移未成功时仍不删除旧偏好，成功提交后才标记并清理旧键。
- **验证及结果**：新增两项 Robolectric 回归测试，取消与 `AssertionError` 在旧实现下都按预期失败，修复后分别验证异常原样传播且旧偏好保留。完整 `:core:data:testDebugUnitTest` 73 项、0 失败/错误；`D:\Barcode_build\run-barcode-android-gradle.ps1 verifyLocal -PenableAppUnitTests=true --offline` 成功，191 项任务（27 执行、2 缓存、162 up-to-date），覆盖全模块测试、架构契约、Beta/Official Release Kotlin 编译与 Lint；不组装 APK。2026-10-22 再针对当前工作区强制重跑该异常传播测试类，2 项通过、0 失败/错误。`git diff --check` 对产品代码通过。
- **阻碍/恢复条件/下一步**：无需设备或远端条件；未提交、未推送、未构建 APK。继续按模块清单处理尚未完成的 `:app` 与构建/发布远端验证事项。

### CI-128 — 旧版设置迁移吞掉致命错误并可能清理源设置

- **检查范围**：`LegacySettingsMigrator` 对 SharedPreferences 整数、浮点数、布尔值和字符串的旧值读取与迁移门闩/源键清理顺序。
- **问题依据/优先级**：确认 P2 数据迁移可靠性问题。四类读取均由 `runCatching` 捕获任意 `Throwable`；若读取中出现 JVM `Error`，迁移器会用默认值继续写入 DataStore、标记迁移完成并删除旧键，造成用户设置不可重试且丢失。新增 Robolectric 回归在旧实现下按预期失败：`AssertionError` 被吞掉，异常结果为 `null`。
- **状态和依赖**：已验证，完成（本地）；依赖当前 DataStore `SettingsStore` 和旧版 SharedPreferences 键，不改变格式或正常坏值回退行为。
- **修改摘要**：统一旧值读取异常边界：仅普通 `Exception` 继续记录并回退默认值；`CancellationException` 原样传播；JVM `Error` 不捕获，因此迁移不会错误标记完成或删除旧源设置。
- **验证及结果**：定向回归在修复前失败、修复后通过；完整 `:core:data:testDebugUnitTest` 74 项全部通过。完整 `D:\Barcode_build\run-barcode-android-gradle.ps1 verifyLocal -PenableAppUnitTests=true --offline` 成功，191 项任务（30 执行、1 缓存、160 up-to-date），覆盖五模块单测、架构契约、Beta/Official Release 编译与 Lint，不组装 APK。2026-10-22 再针对当前工作区强制重跑该异常传播测试类，2 项通过、0 失败/错误。生产文件 `git diff --check` 与新增测试尾随空白检查通过。
- **阻碍/恢复条件/下一步**：无设备或远端依赖；未提交、未推送、未构建 APK。继续检查 `:app` 尚未审阅的核心流程；远端 Actions 配置验证仍需推送授权。

### CI-129 — Android 自动备份包含收藏/历史数据（范围待确认）

- **检查范围**：Manifest 的 `allowBackup`、Android 11 及以下 `fullBackupContent`、Android 12+ `dataExtractionRules`，以及 Room 数据库、DataStore 文件的存储位置。
- **问题依据/优先级**：已确认当前隐私/换机行为，尚不能判断为缺陷。Manifest 启用自动备份；规则仅排除临时 LAN 数据和诊断日志。Room `barcode_generator.db` 存在 database 域，Preferences DataStore `barcode_settings` 在内部 files 域；系统默认备份这两类数据，因此收藏与历史可进入云备份及设备间迁移。现有规则注释明确表示要保留用户资料，应用也提供显式收藏备份，二者对用户意图的含义需要确认。
- **状态和依赖**：待确认；依赖用户决定希望的隐私/换机恢复范围。尚未改 Manifest 或备份规则。
- **修改摘要**：无；只核对 Manifest/XML 与 Room/DataStore 落盘声明，并查阅 Android 官方 Auto Backup 规则。
- **验证及结果**：静态确认两套 Android 版本规则均排除 LAN 临时文件与调试日志，但没有排除 database/files/shared preferences；官方文档说明默认备份包含数据库、内部文件与 SharedPreferences，Android 12+ 云备份和设备间迁移可分别配置。
- **阻碍/恢复条件/下一步**：等待用户选择“云备份和换机都包含”“只换机、不上云”或“都排除”。该决定不影响其他模块审查，先继续检查 `:app` 独立代码路径。

### CI-130 — 全局明文网络许可服务于动态 LAN HTTP

- **检查范围**：主 Manifest cleartext 标志、所有 app/core HTTP(S) 调用点、LAN 会话解析与目标子网校验、更新目录和 APK 下载协议规则。
- **问题依据/优先级**：确认 P2 安全范围偏宽，但未找到保持现有 LAN 传输体验的低风险收窄方式。唯一生产 HTTP 客户端是 LAN 分享客户端；会话只接受带端口的 IPv4 HTTP 地址，发起请求前还验证该 IPv4 属于当前 Wi-Fi 默认网关子网。更新目录固定 HTTPS，APK 下载要求 HTTPS 且每次跳转均拒绝降级。Android Manifest 的全局 `usesCleartextTraffic=true` 允许进程内其他 HTTP 调用，Android Network Security Config 可对固定 domain 设置规则，却不能表达任意动态家庭子网的 CIDR 范围（这是基于配置语法的判断）。
- **状态和依赖**：待确认安全取舍；保持明文 HTTP 是当前 LAN 分享链路的协议选择。若要彻底收窄，需要引入设备信任/本地 HTTPS 或明确限制支持的网络域，属于兼容性和产品行为变化，不在本轮擅改。
- **修改摘要**：无；已追踪全部生产 HTTP(S) 调用点与目标验证条件。
- **验证及结果**：静态检查确认更新链使用 HTTPS；LAN client 每次请求都执行路由器子网校验，域模型拒绝 DNS 主机名、路径和不合规端口。Network Security Config 官方文档以 domain 作为目的地规则，未提供 CIDR 白名单项。
- **阻碍/恢复条件/下一步**：等待产品确认接受此兼容性取舍，或决定未来迁移 LAN 分享到受信任的本地 TLS。当前任务不阻塞继续审查其他 App 功能。

### CI-131 — 旧版设置类型损坏的默认值兼容缺少回归保护

- **检查范围**：`LegacySettingsMigrator` 的 int、float、boolean、string 四类 SharedPreferences 读取与默认值回退，及迁移后的 `SettingsStore` 可见状态。
- **问题依据/优先级**：发现 P3 测试覆盖缺口，不是已证实的产品缺陷。CI-128 收窄异常捕获后，普通 `ClassCastException` 仍应像旧版一样回退默认值，但此前没有集成测试固定这一兼容行为；若后续误将普通错误改成中断迁移，会影响来自旧版的异常类型值。
- **状态和依赖**：已检查，无产品行为修复；依赖公开迁移入口 `SettingsMigration.migrateIfNeeded()`、真实 Robolectric SharedPreferences 与 DataStore，不 mock 内部实现。
- **修改摘要**：新增集成回归，把八个旧设置键写成错误的值类型，断言迁移结果及持久化样式等于领域默认值、迁移门闩完成且旧键已清除。
- **验证及结果**：定向 `LegacySettingsMigratorFailurePropagationTest` 成功（2 项）；完整 `D:\Barcode_build\run-barcode-android-gradle.ps1 --offline :core:data:testDebugUnitTest` 成功，75 项、0 失败/错误。测试使用 CI-053 要求的短 Java 临时路径；没有改生产代码或依赖。
- **阻碍/恢复条件/下一步**：无；不需要设备或远端服务。继续审查 `:app` 其他尚未确认的核心流程；CI-129 的备份范围仍等待用户选择。

### CI-132 — 结果图片分享与 SAF 保存的临时文件生命周期复核

- **检查范围**：`:app` 结果页合成图片的相册保存、系统 Sharesheet 分享、SAF 文件保存、Activity 重建后的待处理文件恢复、启动文件选择器失败时的回滚，以及条码图片 Bitmap 的缓存所有权。
- **问题依据/优先级**：未发现可证实的数据损坏或泄漏缺陷。SAF 导出使用唯一缓存文件；启动失败、用户取消、无返回 URI、来源文件失效、复制成功/失败和协程取消路径均会清理临时文件；待处理路径保存在实例状态中以支持 Activity 重建。Sharesheet 图片在选择器关闭后不立即删除是有意行为，避免接收应用稍后读取失败，文件位于可由系统回收的应用缓存目录。另有一个“导出释放回收可能破坏 LRU 缓存”的崩溃候选，经追踪排除：该路径调用 `ResultsViewModel.createBarcodeImage()`→`BarcodeImageRenderer.create()`，由 ZXing 直接创建独立位图；只有屏幕缓存路径调用 `loadOrCreate()` 并进入 LRU，因此导出后回收不会损坏缓存对象。
- **状态和依赖**：已检查，无需修改；静态审查与既有文件启动回归，无设备或外部应用依赖。
- **修改摘要**：无产品代码修改；没有基于缺乏证据的推测增加缓存清理时限，以免破坏异步分享接收方。
- **验证及结果**：`D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline :app:testBetaDebugUnitTest --tests '*PendingDocumentLaunchTest'` 成功，覆盖系统文件选择器启动成功时保留文件、启动失败时同时清理状态和文件；`--tests '*ResultExportBatchTest'` 再次运行也成功（完整 Beta 单测任务中 73 项均通过/为最新状态，本次各命令实际执行 1 项任务）。源码调用链确认导出用的位图由未缓存的 `create()` 创建，回收不会传染至屏幕缓存。
- **阻碍/恢复条件/下一步**：无；继续审查 `:app` 的其他独立核心流程。当前未连接设备，本项不声称验证了第三方 Sharesheet/SAF Provider 的系统行为。

### CI-133 — 持续改进记录中的 CI-073 过期状态引用

- **检查范围**：复核进度记录中对 CI-073 完成状态的引用是否一致。
- **问题依据/优先级**：记录一致性缺陷。CI-073 本身已注明修复完成，并有设置迁移和异常持久化回归测试及全量本地验证证据；但其后的六处下一步文字仍误称该项等待用户确认，可能导致重复工作或误判阻塞。
- **状态和依赖**：已验证，完成；仅涉及持续改进记录，不影响产品代码或构建行为。
- **修改摘要**：更正六处过期状态引用，保留 CI-064、CI-079、CI-081 各自真实待确认/未验证状态。
- **验证及结果**：重新搜索 CI-073 引用，确认仅保留已完成状态、有效的历史交叉引用和本项说明；检查文件尾随空白。
- **阻碍/恢复条件/下一步**：无；继续独立的 `:app` 模块检查，等待中的用户选择仍只阻塞各自事项。

### CI-134 — `:app` 可操作图标控件的无障碍名称

- **检查范围**：全量枚举主源码中的 `clickable`、`combinedClickable`、`selectable` 与 `IconButton`，并检查自定义设置 `toggleable`，核对图标单独操作时的可访问名称、控件角色，以及装饰图标是否避免重复朗读。
- **问题依据/优先级**：检查图标操作和自定义开关缺少屏幕阅读器名称的可访问性风险；静态核对未发现已证实缺陷。结果页玻璃圆按钮有显式 `contentDescription`；历史删除、LAN 二维码/附件、生成条码行操作有图标描述；Tab 使用页面描述及长按自定义无障碍操作；菜单操作有可见文字标签及按钮角色；收藏文件夹/文件与设置选项有可见文本。设置开关目标声明 `Role.Switch` 和由标题生成的描述，正文区域不承担开关切换；菜单内仅作视觉补充的图标已清除自身语义，避免与文字重复播报。
- **状态和依赖**：已检查，无需修改；TalkBack 实际焦点顺序与不同 Android/OEM 的朗读表现仍需设备验收。
- **修改摘要**：无产品代码修改。
- **验证及结果**：在 `android/app/src/main/java` 全量搜索所有 Compose 可操作修饰符及 `IconButton`，逐一检查 16 处点击入口及 `SettingsSwitchTarget` 的两个调用路径；确认可操作节点由内容描述或可见文本命名，并检查角色/自定义动作声明。定向 `:app:testBetaDebugUnitTest --tests '*SettingsSwitchTargetTest'` 的 4 项、`:architecture-tests:test --tests '*MenuRenderingContractTest'` 的 8 项均通过；前者覆盖开关描述/颜色策略，后者覆盖菜单行语义与绘制契约，不覆盖真实屏幕阅读器或触控命中测试。授权后执行 `adb devices -l` 成功，但列表为空。
- **阻碍/恢复条件/下一步**：当前没有已连接设备；设备连接后可按 TalkBack 导航关键页面确认焦点顺序和长按自定义动作。继续审查 `:app` 其他独立流程。

### CI-135 — LAN 下载提前断流时是否会替换目标文件

- **检查范围**：`:core:lan-share` 的 Android 客户端 `downloadToFile()`、`downloadPreview()`、同目录暂存文件和目标替换行为。
- **问题依据/优先级**：确认 P1 数据完整性缺陷。文件下载和图片预览下载都会先写 `.part` 再替换目标；loopback 返回 `200`、声明 32 字节却仅发出 9 字节时，旧实现可能把截断响应视作成功并覆盖已有目标。预览路径的旧实现已由回归测试复现。
- **状态和依赖**：已验证，完成；响应提供有效 `Content-Length` 时，两条下载路径均校验实际读取字节数，长度不匹配会在替换目标前失败；普通完整响应行为不变。
- **修改摘要**：`downloadToFile()` 和 `downloadPreview()` 分别记录复制字节数并与声明长度比较；长度不符时不替换目标，沿既有 `finally` 删除暂存文件。新增 loopback 回归覆盖两条路径的截断响应、旧目标保留及 `.part` 清理；未改变下载接口或服务端协议。
- **验证及结果**：截断预览回归在旧实现上失败，证明旧路径会接受不完整响应；修复后 `:core:lan-share:testDebugUnitTest --tests '*LanShareClientDownloadTest' --rerun-tasks` 通过。最新完整 `verifyLocal -PenableAppUnitTests=true --offline` 通过：191 项任务，4 执行、187 up-to-date，覆盖架构检查、Beta/Official Release 编译与 Lint、全部模块测试；未组装 APK。`git diff --check` 通过。
- **阻碍/恢复条件/下一步**：无源码验证阻碍。缺少 `Content-Length` 的响应无法通过声明长度比较判断预期字节数；项目当前下载和预览响应均声明该头。Android 真机网络栈的断流行为仍待设备验证。

### CI-136 — `:app` 启动数据加载失败提示协调

- **检查范围**：`MainActivity.onCreate` 首帧挂载、设置与库数据异步加载、错误汇总及生命周期取消传播。
- **问题依据/优先级**：检查启动时设置或收藏/历史读取失败可能被遗漏、重复提示或阻塞首帧的风险；当前静态调用链将设置读取置于首帧后、收藏/历史读取置于 IO dispatcher，分别记录普通异常并传播取消；协调器等两路结果后仅生成一次组合提示，未发现可证实缺陷。
- **状态和依赖**：已检查，无需修改；Activity 重建期间真实系统调度及 Compose 对话框展示仍需设备场景验证。
- **修改摘要**：无产品代码修改。
- **验证及结果**：定向运行 `D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline :app:testBetaDebugUnitTest --tests '*StartupFailureNoticeCoordinatorTest' --rerun-tasks` 成功，5 项测试、0 失败/错误；`git diff --check` 退出码 0（仅输出工作树中若干既有 LF/CRLF 转换提示）。测试覆盖协调器的先后顺序与并发单次提示，不覆盖真实 Activity 生命周期。
- **阻碍/恢复条件/下一步**：无代码级阻碍；设备连接后可补验系统旋转/重建场景。继续 `:app` 其他独立核心路径。

### CI-137 — `:app` 更新 APK 校验失败清理与安装错误反馈

- **检查范围**：下载 APK 的安装前校验、拒绝后的暂存文件清理、安装失败诊断与用户提示。
- **问题依据/优先级**：检查无效 APK 残留、清理异常覆盖原始校验错误、以及把可能包含 URI/本地路径的详细错误写入诊断日志等风险；现有实现校验失败时尝试删除 APK，清理错误作为 suppressed 信息附加而保留原异常；持久化仅记录通用更新失败摘要，详细原因仅用于当前用户提示，未发现可证实缺陷。另筛查 `:app/src/main/java` 未发现 Kotlin 非空断言 `!!`。
- **状态和依赖**：已检查，无需修改；真实 `PackageManager` 签名档案提取仍按 CI-064 待确认，未知来源设置与安装器进程恢复仍按 CI-027 待真机验证。
- **修改摘要**：无产品代码修改。
- **验证及结果**：使用 `D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline :app:testBetaDebugUnitTest --tests '*ApkInstallValidationTest' --tests '*ApkInstallFailureReportingTest' --rerun-tasks` 成功；5 项测试、0 失败/错误，覆盖有效文件保留、校验失败/取消/致命错误清理并传播，以及详细提示与通用持久化摘要隔离。此验证不执行 Android PackageManager 或系统安装器。
- **阻碍/恢复条件/下一步**：无本地验证阻碍；CI-027、CI-064 仍需各自的设备或已确认测试接缝，不影响继续审查 `:app` 其他路径。

### CI-138 — 更新 APK 的 FileProvider 安装交接与缓存生命周期

- **检查范围**：APK 下载完成后的 `FileProvider` URI 生成、系统安装 Intent 的读取授权、未知来源设置往返恢复，以及已完成安装包的缓存清理策略。
- **问题依据/优先级**：静态调用链未发现可证实的安装 URI 权限缺陷：Provider 未导出并仅允许 URI 临时授权；安装 Intent 使用 APK MIME、`FLAG_GRANT_READ_URI_PERMISSION` 和 ClipData；未知来源权限通过 `SavedStateHandle` 保存待安装路径，并在 `onResume` 授权后一次性消费。发现低优先级缓存生命周期候选：每次成功下载生成唯一的已校验 APK 文件，现有清理只删除遗留 `.apk.part` 文件，完成包会留在 cache，直到系统回收；但系统安装器可能异步读取 URI，当前没有足够证据安全确定删除时点，因此不在本项改动。
- **状态和依赖**：已检查，无需修改（安装授权交接）；APK 缓存清理是低优先级改进候选，现有证据不足以确定安全删除时机，不能在未证明前清理安装器可能仍在读取的文件。
- **修改摘要**：仅新增本持续改进记录；未改动下载、Provider 或安装行为。
- **验证及结果**：核对 `ComposeUpdateDialogs.installApkCompose`、`MainActivity.onResume`、`UpdateCoordinator`、Android Manifest 与 `res/xml/file_paths.xml`，确认 URI 授权和权限往返状态路径；既有 `UpdateCheckStateTest` 覆盖待安装路径重建后一次性消费，CI-137 定向测试覆盖 APK 校验及失败反馈。工作区 `git diff --check` 退出码 0，仅报告既有 CRLF/LF 转换提示；进度日志无尾随空白。以上均不替代设备上的系统 PackageInstaller 实测。
- **阻碍/恢复条件/下一步**：当前没有设备，未知来源设置与系统安装器恢复仍按 CI-027 待真机验证；若后续要实现已完成 APK 清理，先定义并验证安装器不再读取文件的生命周期边界。继续审查 `:app` 其他独立流程。

### CI-139 — 收藏备份 JSON 解析把致命错误伪装为格式错误

- **检查范围**：`:core:data` 的 `FavoritesTransferManager.restore(bytes)` ZIP JSON 解析及普通格式错误的用户可见回退。
- **问题依据/优先级**：确认 P3 错误分类缺陷。`parseFavoriteJson()` 对 `JSONObject(json)` 使用 `runCatching`，会把 `JSONException` 以外的 `OutOfMemoryError`、`AssertionError` 等也改写成“收藏文件不是有效 JSON”，丢失程序级故障语义。虽然单项 JSON 限 1 MB、总解压限 32 MB，内存压力下仍可能触发；当前没有证据表明会损坏已提交数据。
- **状态和依赖**：已验证，完成；普通 JSON 语法错误继续转换为原有“收藏文件不是有效 JSON”提示，其他运行时异常与 `Error` 不再误分类。
- **修改摘要**：新增内部 `parseFavoriteJsonOrError()`，只捕获 `JSONException` 并保留原用户提示；未增加公开解析器接口或改变备份格式。
- **验证及结果**：新增 3 项异常边界回归，首次运行时 OOM 与非 JSON 运行时异常两项失败、JSON 语法提示通过；改为仅捕获 `JSONException` 后定向 `:core:data:testDebugUnitTest --tests '*FavoriteJsonFailurePolicyTest'` 通过。随后完整 `verifyLocal -PenableAppUnitTests=true --offline` 通过：191 项任务，31 执行、1 缓存、159 up-to-date，覆盖架构测试、Beta/Official 编译与 Lint 及模块测试；未组装 APK。`git diff --check` 通过。
- **阻碍/恢复条件/下一步**：无本项阻碍；继续审查 `:core:data` 和其他模块的独立待办。错误边界单测不等价于真实设备内存耗尽演练。

### CI-140 — ML Kit OCR 将初始化及回调致命错误降级为空结果

- **检查范围**：`:core:data` 的 `MlKitOcrTextGateway.recognize()`、ML Kit 客户端创建、识别回调结果归一化、同步 `process()` 启动和 Bitmap/recognizer 清理。
- **问题依据/优先级**：确认 P2 错误分类与诊断缺陷。三个生产失败边界分别以 `catch (Throwable)` 返回空列表：客户端初始化、成功回调的文本转换、同步 `process()` 调用。它们会吞掉 `OutOfMemoryError`、`AssertionError` 等程序级错误；Presentation 随后把空列表视为“无识别文字”。图片预处理中的两处 `catch (Throwable)` 仅清理后原样重抛，不属于此缺陷。
- **状态和依赖**：已验证，完成；普通 ML Kit 异常继续回退为空结果，协程取消及 JVM `Error` 不再被降级；无公开接口变化。
- **修改摘要**：增加内部 OCR 异常分类策略，并用于客户端初始化、成功回调归一化和同步提交失败三处；新增 3 项策略回归测试。既有 bitmap / recognizer 清理顺序保持不变。
- **验证及结果**：新测试先红后绿：基线下致命错误和取消两项失败、普通异常回退通过；修复后 `:core:data:testDebugUnitTest --tests '*OcrFailurePolicyTest' --rerun-tasks` 通过，3 项、0 失败/错误。完整固定本地 `verifyLocal -PenableAppUnitTests=true --offline` 通过：191 项任务，33 执行、1 缓存、157 up-to-date，覆盖架构检查、Beta/Official Release 编译与 Lint、各模块单测；未组装 APK。
- **阻碍/恢复条件/下一步**：无源码验证阻碍；设备 OCR/相机行为尚未实测。继续处理 `:core:data` 其他独立事项；不把策略单测等同于 ML Kit SDK 实际回调或 Bitmap 所有权的设备验证。

### CI-141 — ZXing 条码图片识别吞掉内存和程序级错误

- **检查范围**：`:core:data` `ZxingBarcodeDecodeGateway.decode(ImagePayload)` 与 `CameraOcrViewModel` 的错误处理和 Activity 图片资源回收。
- **问题依据/优先级**：确认 P2 错误分类缺陷。Gateway 用 `runCatching` 覆盖 Bitmap 解码、全图像素数组分配和 ZXing 识别；因此 `OutOfMemoryError` 等会被当成普通“未识别到条码”返回 `null`。上层 `CameraOcrViewModel` 已明确只把普通 `Exception` 转为可恢复空结果并记录，Bitmap 生命周期由 Activity `finally` 回收；被 Gateway 吞掉的 `Error` 无法到达该边界。
- **状态和依赖**：已验证，完成；普通解码异常维持 `null` 未识别结果，协程取消原样传播，`Error` 不被降级；不新增公开接口。
- **修改摘要**：将 ZXing 解码异常边界抽成内部 `decodeImageOrNull()`，只将普通 `Exception` 转换为 `null`，显式重抛 `CancellationException`，不捕获 `Error`。
- **验证及结果**：新增 3 项直接针对生产异常边界的回归。首次运行时，致命错误传播和协程取消两项均失败、普通异常行为通过，证明旧策略会吞掉两者；修复后定向 `:core:data:testDebugUnitTest --tests '*ZxingDecodeFailurePolicyTest'` 通过。完整 `verifyLocal -PenableAppUnitTests=true --offline` 通过：191 项任务，33 执行、1 缓存、157 up-to-date，覆盖架构、Beta/Official 编译与 Lint、模块测试；未组装 APK。`git diff --check` 通过。
- **阻碍/恢复条件/下一步**：无本项阻碍；继续处理 `:core:data` 中其余独立待办。此测试验证错误分类边界，不声称真实设备 OOM 或图像解码性能已做设备复现。

### CI-142 — app 主源码同步等待调用筛查

- **检查范围**：扫描 `:app` 与 `:core` Kotlin 主源码中的 `runBlocking`、`Thread.sleep`、`join()` 和 `get()`，并追踪生产命中点的调用路径，寻找可能阻塞 UI 的同步等待。
- **问题依据/优先级**：本范围没有发现已证实的 UI 线程阻塞缺陷。`:app` 主源码没有 `runBlocking` 或 `Thread.sleep`；唯一生产 `runBlocking(Dispatchers.IO)` 位于 `LanShareWebImagePreviewCache` 的 TIFF 网页预览分支，由 `LanShareServer` 的 `/api/preview/` 同步服务请求调用，不在 Compose/Activity UI 路径。`ComposePageRoute` 的 `Job.join()` 是协程挂起等待；搜索到的 `AtomicLong.get()` 也不是阻塞调用。TIFF 预览请求线程在大型图像上的实际耗时未测量，不据此声称网络请求无延迟。
- **状态和依赖**：已检查，无需修改；本项是静态范围筛查，不替代设备端帧时或并发请求性能测量。
- **修改摘要**：无产品代码修改；仅补充审查记录。
- **验证及结果**：运行 `rg -n -g '*.kt' 'runBlocking|Thread\.sleep|\.join\(\)|\.get\(\)' android/app/src/main android/core`，随后逐一核对生产命中点、`LanShareServer` 预览路由和 TIFF 解码调用链；未运行测试或 APK。
- **阻碍/恢复条件/下一步**：无代码级阻碍；若出现网页大图预览卡顿报告，再用受控大 TIFF 与并发请求做耗时测量。继续审查 `:app` 其他独立核心路径。

### CI-143 — NanoHTTPD 请求体临时文件公告适用性核查

- **检查范围**：仓库声明的 NanoHTTPD 构件、LAN 服务的 HTTP 请求体入口、NanoHTTPD 2.3.1 实际 `HTTPSession` 字节码，以及 Android 对 `java.io.tmpdir` 的默认设置和应用沙箱边界。
- **问题依据/优先级**：CVE-2022-21230 描述 NanoHTTPD 在显式解析大于 1024 字节的请求体时，将数据写入权限过宽的 `RandomAccessFile`。当前项目只引入 `org.nanohttpd:nanohttpd` 与 `nanohttpd-websocket`，未引入另一个 XSS 公告所针对的 `nanohttpd-nanolets`。LAN 上传由 `LanShareServer` 直接读取 `session.inputStream`；全仓主代码未调用 `parseBody()`，而 NanoHTTPD 2.3.1 的 `HTTPSession.execute()` 字节码直接进入服务处理器，不会自动调用 `parseBody()`。因此公告所述的请求体临时文件写入路径在当前上传实现中没有被触发。Android `ActivityThread` 默认把 `java.io.tmpdir` 设为应用 `cacheDir`，并由按 UID 隔离的应用沙箱保护；这为未来显式使用解析器提供额外边界，但不能替代代码路径审查。
- **状态和依赖**：已排除，并注明依据；本结论限于当前 Android LAN 服务和当前 NanoHTTPD 用法，不代表该依赖在其他平台/调用方式下无漏洞，也不代表完成了全依赖库漏洞扫描。
- **修改摘要**：无产品代码/依赖修改；仅补充持续改进记录。没有仅因版本号旧而升级依赖或改变上传实现。
- **验证及结果**：核对 `android/gradle/libs.versions.toml` 与 `:core:lan-share` 声明；通过项目当前缓存的 NanoHTTPD 2.3.1 JAR 使用固定 JDK `javap -c -p` 检查 `HTTPSession.execute()`、`DefaultTempFileManager` 和 `DefaultTempFile`；`execute()` 调用服务处理器而非 `parseBody()`，临时文件管理器仅在被显式请求创建临时文件时调用 `File.createTempFile`。源码搜索确认 LAN 服务从 `session.inputStream` 读取请求体且没有 `parseBody()`/`getParms()` 调用；AOSP `ActivityThread` 与 `System` 源码确认临时目录默认指向应用缓存目录，Android 官方安全文档确认应用 UID 沙箱隔离。未运行代码测试或 APK，因为没有生产代码变化，且静态字节码/调用点已足以判定此特定路径。
- **阻碍/恢复条件/下一步**：`:core:lan-share` 仍由独立的 CI-135 下载断流测试接缝确认事项保持“检查中”；继续检查其他直接依赖与供应链风险，不提交、不推送、不构建或发布。

### CI-144 — WebSocket 入站帧在聊天长度校验前无实用大小上限

- **检查范围**：NanoWSD 2.3.1 `WebSocketFrame.read()` 的帧长解析、payload 分配、分片重组，以及 `LanShareServer.BrowserWebSocket.onMessage()` 的聊天文本限长位置。
- **问题依据/优先级**：确认 P2 可用性/内存耗尽风险。库层对单帧长度仅拒绝超过 `Integer.MAX_VALUE` 的声明值，随后按声明长度直接分配 `byte[]`；分片消息也先聚合长度再分配数组，仍只受整数上限约束。`onMessage()` 在整帧读入、UTF-8 解码、必要的 JSON 解析之后才对 `text` 检查 64 KB，因此该业务限制不能保护前面的内存分配。持有有效配对凭据的 LAN 浏览器端可构造远大于应用消息上限的帧，可能使进程内存耗尽；正常网页 UI 本身只发送小消息，未证明匿名互联网用户可直接触发。
- **状态和依赖**：已确认，待修复；安全边界应在帧 payload 分配和分片累积之前执行。现有 NanoWSD API 没有可配置入站最大帧大小的接缝，需在保持 HTTP 服务不变的前提下，选择维护一个受限帧解析实现或迁移到支持可配置帧上限的 WebSocket 实现；这会改变网络层实现，先待用户确认方案。
- **修改摘要**：无产品代码修改；仅补充持续改进记录。保留现有 64 KB 业务消息规则，不把它误记为网络帧内存防护。
- **验证及结果**：从项目当前缓存的 `org.nanohttpd:nanohttpd-websocket:2.3.1` JAR 使用固定 JDK `javap -c -p` 检查 `WebSocketFrame.readPayloadInfo()`、`readPayload()` 与分片帧构造器：长度检查上限是整数范围，`new byte[payloadLength]` 和分片总 payload 数组均在调用 `onMessage()` 前创建。静态追踪 `LanShareServer` 确认 64 KB 检查位于回调阶段；现有服务端 loopback 测试覆盖消息身份、正常收发和认证，但没有超限帧的 pre-allocation 回归。未尝试分配巨型 payload，也未运行测试或 APK，以避免通过真实 OOM 来证明静态可见的危险分配路径。
- **阻碍/恢复条件/下一步**：等待用户确认帧解析修复路线；确认后先建立安全的 loopback/解析器回归，证明超限单帧与分片总量在大数组分配前被拒绝，再实施和验证。该网络层方案未确认前不扩大改动；协议文档已增加限制发生时机的说明（CI-152），继续审查其他独立项目路径。

### CI-145 — Room v1/v2 迁移事项与 CI-081 重复（已合并）

- **检查范围**：将本轮发现的版本 1/2 迁移测试缺口与既有 CI-081 对照，检查进度是否存在重复待办。
- **问题依据/优先级**：确认两项完全同源：生产迁移链支持 v1/v2，但缺少权威旧版 schema/数据库及对应迁移回归。CI-081 已包含更完整的历史测试结果、旧版 schema 查找记录与用户待确认的测试边界；单独保留 CI-145 会造成重复推进和状态不一致。
- **状态和依赖**：已排除，并注明依据；唯一有效的待办与恢复条件以 CI-081 为准，仍待用户确认并取得权威 fixture。
- **修改摘要**：将 `:core:data` 模块清单指向 CI-081；本条仅记录去重，不新增产品代码、测试或第二个迁移任务。
- **验证及结果**：逐段比较 CI-081/CI-145 的检查范围、证据、阻碍和恢复条件，确认完全重复；全进度只保留 CI-081 为活动事项。
- **阻碍/恢复条件/下一步**：无独立阻碍；若用户确认并提供旧版样本，直接按 CI-081 继续，不再启动 CI-145 平行工作。

### CI-146 — 收藏 ZIP 导入资源上限复核

- **检查范围**：收藏备份 SAF 输入读取、ZIP 条目与解压预算，以及 JSON 文件逐条解析前后的资源限制。
- **问题依据/优先级**：未发现可证实的无界读取或 ZIP 解压风险。SAF 输入读取在追加到内存前实施 64 MiB 上限；ZIP 恢复限制 2,048 个条目、单个收藏 JSON 1 MiB、总解压量 32 MiB，并把不导入的文件/目录也计入预算，避免通过跳过路径绕过限制。
- **状态和依赖**：已检查，无需修改；这些限制表达了当前备份格式的既有兼容上限，不调整产品行为。
- **修改摘要**：无产品代码修改；仅记录现状核对和测试证据。
- **验证及结果**：固定 Gradle 脚本强制重跑 `:core:data:testDebugUnitTest --tests '*FavoritesTransferManagerTest'` 成功，18 项、0 失败/错误；`-PenableAppUnitTests=true --offline :app:testBetaDebugUnitTest --tests '*FavoriteBackupInputTest' --rerun-tasks` 成功，2 项、0 失败/错误。未组装 APK；期间一次未带 App 单测启用参数的命令因任务未注册而退出，随后按项目配置正确重跑通过。
- **阻碍/恢复条件/下一步**：无；继续检查其他独立模块。导入 JSON 对象的额外内存占用未做低内存设备测量，不据此宣称任意设备都不会因合法的大备份触发系统内存回收。

### CI-147 — LAN HTTP 上传容量预留与暂存提交

- **检查范围**：HTTP 上传请求帧解析、文件/房间大小上限、并发数及磁盘空间预留、暂存文件清理和最终提交顺序。
- **问题依据/优先级**：未发现无界上传或半文件被当作完成文件暴露的可证实缺陷。上传路由在读文件体前校验长度与 10 GiB 单文件上限；在锁内检查并预留 100 GiB 房间配额、系统可用空间和并发槽位，再于锁外流式写入同目录 `.part` 暂存文件；核对请求长度和落盘长度后才原子改名。失败或取消路径由 `finally` 删除暂存文件并释放容量预留。
- **状态和依赖**：已检查，无需修改；依赖当前 LAN 服务认证及 `LanShareLimits` 配额。
- **修改摘要**：无产品代码修改，仅补充审查记录。
- **验证及结果**：代码静态检查核对认证先于上传分发、请求长度检查和暂存/提交顺序。完整验证曾因取消上传测试用相同的 5 秒服务端 socket 读取超时和 5 秒清理断言窗口而失败；将测试观察窗扩至 10 秒并在失败消息列出残留文件后，定向取消测试连续 8 次通过，最终完整 `verifyLocal -PenableAppUnitTests=true --offline` 通过：191 项任务，6 执行、185 up-to-date。测试没有模拟 Android 设备断电或内核级磁盘故障。
- **阻碍/恢复条件/下一步**：无已复现的产品清理缺陷；取消清理回归保留 10 秒等待以避免与服务端读取超时竞争。继续检查其他独立事项；WebSocket 超大帧（CI-144）仍待确认网络层方案，下载断流（CI-135）已完成。

### CI-148 — LAN 浏览器配对与会话鉴权复核

- **检查范围**：配对码熵与生成、未认证 `/api/pair` 的失败限速、会话 token 生成/比较、配对成功响应缓存策略及失败请求的日志边界。
- **问题依据/优先级**：未发现可证实的弱 token 或无界暴力尝试缺陷。配对码为 `SecureRandom` 生成的 8 位数字；每个远端地址每 60 秒最多 5 次失败尝试，跟踪地址上限 256，避免限速表被任意增长。会话凭据由 32 个随机字节编码，服务端通过 `MessageDigest.isEqual` 比较；成功响应带 `Cache-Control: no-store`，未授权响应也不缓存。鉴权先于受保护路由分发，失败日志不写入请求查询串/凭据。
- **状态和依赖**：已检查，无需修改；LAN 局域网 HTTP 本身不加密仍是已单独记录的平台边界（CI-130），本项只核对当前配对与会话认证。
- **修改摘要**：无产品代码修改，仅补充审查记录。
- **验证及结果**：固定脚本强制重跑 `:core:lan-share:testDebugUnitTest --tests '*LanShareServerOpenAccessTest' --rerun-tasks` 成功，10 项、0 失败/错误；回归覆盖未配对访问拒绝、正确配对返回 token，以及同一客户端连续 5 次失败后第 6 次收到 429。静态检查确认 token 生成与常量时间比较调用点。
- **阻碍/恢复条件/下一步**：无；继续检查其他独立路径。该测试使用 loopback HTTP，不验证真实 Wi-Fi 下的抓包风险或 Android 网络隔离行为。

### CI-149 — LAN 诊断日志的凭据与用户数据暴露复核

- **检查范围**：`:core:lan-share` 所有持久日志调用、LAN 服务启动/上传/请求失败信息，以及 Beta 日志异常格式化和导出脱敏链。
- **问题依据/优先级**：未发现可证实的会话 token、配对码或上传文件名进入持久日志的调用。服务启动日志仅写基础 IP/端口；HTTP 请求错误只记录查询串之前的路径；上传诊断记录字节数/耗时而非文件名。Beta 日志在异常类型和调用栈之外省略异常消息，导出前对 URL/URI、路径和旧日志中的上传文件名做脱敏；正式版使用空日志实现。
- **状态和依赖**：已检查，无需修改；依赖当前 Beta 日志格式化/导出后端，以及 LAN 层日志调用保持无敏感参数。
- **修改摘要**：无产品代码修改，仅补充审查记录。
- **验证及结果**：固定脚本强制重跑 `:app:testBetaDebugUnitTest --tests '*DiagnosticThrowableFormatterTest' --rerun-tasks` 成功，4 项、0 失败/错误；静态枚举 `LanShareServer`、`LanShareManager`、`LanShareClient` 的全部 `logger.record` 调用，核实会话 token 仅进入配对响应和浏览器会话，不进入日志消息。
- **阻碍/恢复条件/下一步**：无；继续检查其他独立路径。该范围不审计 Android 系统/第三方 NanoHTTPD 自身可能产生的 Logcat，也不验证设备备份提取出的应用私有日志。

### CI-150 — README 局域网分享安全说明与实现不一致

- **检查范围**：README 面向用户的 LAN 安全说明，与 `PROTOCOL.md`、`LanShareServer` 配对码/会话令牌实现及受保护路由核对；同时检查主要 Markdown 文档的本地文件链接。
- **问题依据/优先级**：确认 P2 用户安全认知错误。README 原文声称局域网分享“没有身份认证或密码保护、能访问地址的设备均可上传下载”，但服务实际要求 8 位主机配对码兑换会话令牌，并在文件接口、上传、预览、下载和 WebSocket 路由校验有效令牌。另一方面 HTTP/WebSocket 确实未加密，令牌与内容仍可能被同网监听者截获，不能把配对认证描述成传输加密。
- **状态和依赖**：已修复并经静态核验；不改变协议或行为。
- **修改摘要**：README 改为准确说明文件接口有配对码和会话令牌保护，同时明确 HTTP/WebSocket 明文可能导致令牌及文件内容被监听，并保留“仅在可信 Wi-Fi 使用”的提示。本地 Markdown 链接核查无失效目标。
- **验证及结果**：对 README、PROJECT_STRUCTURE、CONTRIBUTING、ARCHITECTURE、PROTOCOL 与 PERFORMANCE_BASELINE 的相对 Markdown 文件链接做路径存在性检查，未发现断链；逐项核对 `LanShareServer` 的 `/api/pair`、令牌生成/比较和受保护路由，与 `PROTOCOL.md` 安全模型一致。另核对测试版 workflow 的 Release 正文确实包含版本、提交 SHA 与 APK SHA-256，正式版 workflow 上传并校验 APK 和 `.sha256`，与 README 发布说明一致。未运行测试或构建，因为仅修改文档。
- **阻碍/恢复条件/下一步**：无；继续检查剩余独立模块和未完成项。当前 README 的协议说明仅覆盖用户可见安全边界，不取代完整网络威胁模型。

### CI-151 — 项目结构与架构导览和 Gradle 工程不一致复核

- **检查范围**：`PROJECT_STRUCTURE.md` 的仓库树、模块职责和源码目录，与 `settings.gradle.kts`、模块 Gradle 依赖、Android source set 及实际目录核对；同步检查 `ARCHITECTURE.md` 的模块依赖方向声明。
- **问题依据/优先级**：未发现可证实的目录或模块说明漂移。工程注册五个模块；`:app` 依赖三个 Core 模块，`:core:data` 和 `:core:lan-share` 各依赖 `:core:domain`，Domain 没有项目模块依赖；Beta/Official 与单元/仪器测试 source set、`architecture-tests` 和 `tools` 目录均存在。文档没有把本机 Gradle 验证工具目录误写成仓库组成。
- **状态和依赖**：已检查，无需修改；本次只核验项目结构与架构边界的文档准确性，不表示所有源文件均已逐一审查。
- **修改摘要**：无产品或项目说明修改；仅记录本次核对结果。
- **验证及结果**：静态核对 `android/settings.gradle.kts`、`:app`/`:core:*` 模块依赖声明及 `android/app/src/{beta,official,test,androidTest}`、`android/architecture-tests`、`android/tools` 目录；与 `PROJECT_STRUCTURE.md`、`ARCHITECTURE.md` 对照一致。没有代码变化，未运行测试或构建。
- **阻碍/恢复条件/下一步**：无；继续审查其余文档内容与独立模块待办。

### CI-152 — LAN 协议文档区分聊天大小限制与 WebSocket 帧资源上限

- **检查范围**：`PROTOCOL.md` 对 WebSocket 聊天 64 KiB UTF-8 限制的说明，与 NanoWSD 完整帧读取及 `LanShareServer.onMessage()` 校验时机核对。
- **问题依据/优先级**：确认 P2 维护者认知风险，关联已确认的 CI-144。原文只说超限聊天内容会被拒绝，未指出限制在完整帧解析之后执行，容易被误读为网络帧层已有内存保护。
- **状态和依赖**：文档说明已修复；底层 WebSocket 帧资源风险仍按 CI-144 待用户确认修复路线，文档更正不代表风险已解决。
- **修改摘要**：在协议文档明确 64 KiB 仅是完整帧解析后的聊天业务限制，不是帧大小或内存用量上限，并指向 CI-144；没有改变协议或运行行为。
- **验证及结果**：静态核对 NanoWSD 2.3.1 帧读取/分片分配发生在 `onMessage()` 前，及聊天 UTF-8 长度检查位于回调中；主要 Markdown 文档本地链接检查通过，`git diff --check -- README.md android/core/lan-share/PROTOCOL.md` 通过，未跟踪进度文件无尾随空白。未运行测试或构建，因为仅修改文档。
- **阻碍/恢复条件/下一步**：底层修复方案仍待确认；继续推进无关的文档准确性检查和独立模块审查。

### CI-153 — Room stale-ID 导入回归的专用测试库无法打开

- **检查范围**：`BarcodeDatabaseMigrationTest.favoriteImportRebasesStaleDraftIdsAgainstDatabaseInsideCommitTransaction` 的 Robolectric SQLite 文件初始化、与 `commitFavoriteImport` 同一事务调用链的差分对照。
- **问题依据/优先级**：确认 P2 测试可靠性问题，不是已证实的产品数据库缺陷。使用专用 `barcode-stale-import-test` 数据库名时，单测和整类执行都在 SQLite 打开/journal mode 阶段稳定抛 `SQLITE_CANTOPEN`；相同 `commitFavoriteImport` 事务上的默认测试库回滚测试通过，失败不进入测试断言。
- **状态和依赖**：已验证，完成；仅调整测试数据库隔离方式，生产数据和 Room 行为未改变。
- **修改摘要**：将 stale-ID 回归改用该测试类既有的 `TEST_DATABASE`；`@Before/@After` 已删除并清理此库，仍保持每项测试隔离。移除额外数据库名常量。
- **验证及结果**：更改前，整类 12 项中 1 项失败；同一项单独运行仍失败，默认库事务回滚对照通过。更改后该单项通过，随后 `D:\Barcode_build\run-barcode-android-gradle.ps1 --offline :core:data:testDebugUnitTest --tests '*BarcodeDatabaseMigrationTest' --rerun-tasks` 成功，12 项、0 失败/错误。不组装 APK。
- **阻碍/恢复条件/下一步**：无；继续处理 `:core:data` 其余独立风险。Room v1/v2 迁移夹具缺口仍按 CI-081 待确认，不受本项测试库修正影响。

### CI-154 — 条码图片缓存写入吞掉致命错误

- **检查范围**：`:core:data` `LocalBarcodeFileStore.writeImage()` 对临时 PNG 写入和 `Bitmap.compress()` 的异常处理，以及该缓存数据是否属于可再生成数据。
- **问题依据/优先级**：确认 P3 错误分类/诊断缺陷。`runCatching` 会捕获所有 `Throwable`；缓存压缩遇到 `OutOfMemoryError` 或其他 `Error` 时会被转换为 `false`，随后删除临时文件并静默返回，掩盖 JVM/内存故障。缓存仅可由 Room 中的数据重新生成，未发现该路径会丢失用户条码持久数据。
- **状态和依赖**：已验证，完成；缓存写入只将普通 `Exception` 降级为缓存失败，`Error` 原样传播；未改公开 API。
- **修改摘要**：新增内部 `writeBitmapCacheFile()` 作为压缩/临时文件边界；压缩返回失败或抛异常/错误时清理部分文件，同时保留成功压缩后的临时文件供后续原子提交。
- **验证及结果**：新增 3 项 `BitmapCacheFileWriterTest`，分别验证普通异常返回失败并清理、`OutOfMemoryError` 原样传播并清理、成功后保留临时文件；定向 `:core:data:testDebugUnitTest --tests '*BitmapCacheFileWriterTest'` 通过。完整 `verifyLocal -PenableAppUnitTests=true --offline` 通过：191 项任务，33 执行、1 缓存、157 up-to-date，覆盖架构检查、Beta/Official Release 编译与 Lint、各模块测试；未组装 APK。`git diff --check` 通过。
- **阻碍/恢复条件/下一步**：无本项阻碍；继续检查 `:core:data` 其他未解决事项。真实设备 Bitmap/GPU 行为不属于此文件 I/O 异常边界测试范围。

### CI-155 — 依赖更新与制品完整性自动化缺口

- **检查范围**：版本目录与 Gradle 模块依赖声明、仓库内 Dependabot/Renovate 配置、Gradle dependency locking/verification 配置及 GitHub workflow 文件清单。
- **问题依据/优先级**：确认 P3 维护和供应链可追溯性改进机会，不代表已发现漏洞或依赖被篡改。外部依赖集中在 `libs.versions.toml` 且当前没有动态版本；但仓库没有 Dependabot/Renovate 更新配置、Gradle lockfile 或 `verification-metadata.xml`。因此仓库本身不保证依赖更新 PR 的定期产生，也不校验解析到的 Maven/插件制品摘要。GitHub 侧依赖图、漏洞告警和安全更新开关属于仓库外设置，本地证据无法判断其当前状态。
- **状态和依赖**：待确认；自动更新 PR 会影响 GitHub 工作流，目标分支须与 Beta 测试、正式版 `main` 的发布流程匹配。尚未添加机器人配置或生成校验元数据。
- **修改摘要**：无产品、Gradle 或 GitHub 配置修改；本项仅记录仓库内可观察到的自动化缺口及其边界。
- **验证及结果**：核对 `android/gradle/libs.versions.toml`、五个模块的 `build.gradle.kts`、`android/settings.gradle.kts` 与 `.github` 文件清单；确认版本集中声明、`google()`/`mavenCentral()` 仓库集中管理，无锁文件、verification metadata 或依赖更新机器人配置。对 NanoHTTPD、ZXing 与 TiffRenderer 做了有限的公开公告查询，但结果不足以代替解析后的完整依赖树扫描或证明依赖无已知漏洞；未读取仓库外 GitHub 安全设置。GitHub 的公共公告入口为 [Advisory Database](https://github.com/advisories)。
- **阻碍/恢复条件/下一步**：需确认 Dependabot 更新 PR 应发往 `beta`（验证后再晋升到 `main`）、`main`、两个分支，还是维持人工更新；随后再评估 Gradle 制品校验元数据的生成与 CI 维护成本。此项不阻塞其他代码审查。

### CI-156 — 浏览器大文件上传是否整文件缓冲及取消清理

- **检查范围**：浏览器上传 `File` 的请求体传递、进度记录、取消/失败/成功交接，以及服务端回执和实时事件竞态。
- **问题依据/优先级**：未发现浏览器端把整个文件复制到 JavaScript 内存的代码路径。上传直接以 `XMLHttpRequest.send(file)` 发送原始 `File`，没有 `FileReader`、`arrayBuffer()` 或 `Blob` 副本；单文件大小先与共享 10 GiB 上限比较。当前实现每次选择一个文件，不存在本页面隐式并发提交整个多选集合的路径。上传记录只保存名称、大小、时间与进度；取消时移除 UI 记录并 `abort()`，失败/取消在 `finally` 回收活动请求和取消标记；成功 ID 以 HTTP 回执为准，WebSocket 同步事件与回执按文件 ID 去重。
- **状态和依赖**：已检查，无需修改；这只覆盖浏览器源码中的请求/状态路径，不证明各浏览器内部网络栈的缓存策略，也不替代 iOS Safari 真实设备上传/取消测试。
- **修改摘要**：无产品代码修改；新增本审查记录。没有把 XHR 进度事件误称为传输链路吞吐基准。
- **验证及结果**：固定本地工具 `D:\Barcode_build\run-barcode-android-gradle.ps1 --offline :core:lan-share:testDebugUnitTest --tests '*LanShareWebScriptTest' --rerun-tasks` 成功，`LanShareWebScriptTest` 16 项、0 失败/错误，17 项 Gradle 任务执行。沙箱内初次执行因本机 Gradle daemon localhost IPC 被限制而失败；按项目流程对同一只读验证命令获批提升后重试通过。该测试主要断言生成脚本契约，不是完整浏览器运行时测试；未组装 APK、未访问远端或设备。
- **阻碍/恢复条件/下一步**：需连接 iOS Safari 真机才能验证浏览器文件选择器、长时间大文件上传与中途取消；本地源码审查无需等待设备。继续推进其他未被该验证条件阻塞的事项；CI-135/CI-144 与 CI-154 各自的测试边界确认保持独立。

### CI-157 — 结果预览释放是否会回收条码缓存位图

- **检查范围**：`:core:data` 位图 LRU 返回对象、`:app` ZXing 渲染器缓存所有权、结果页预览合成以及 Compose 离场释放路径。
- **问题依据/优先级**：针对“结果页退出后再次进入时缓存命中已回收 Bitmap，导致绘制失败”的潜在 P2 崩溃线索做调用链核对；未发现当前实现存在该所有权冲突。缓存保存/返回原始条码 Bitmap；`prepareResultRows()` 将原图传给 `composeResultRowImage()`，后者新建独立结果行 Bitmap 并绘制原图；`DisposableEffect` 只回收这些新建结果行，未回收缓存返回的原图。Code 128 的 trim/静区变换仅回收转换前的中间 Bitmap，最终缓存对象继续有效。
- **状态和依赖**：已检查，无需修改；结论基于当前生成与预览调用链，若后续改为直接将缓存 Bitmap 纳入结果行所有权，须重新审查并更新所有权契约。
- **修改摘要**：无产品代码修改；仅记录一次跨 `:core:data`/`:app` 的位图所有权复核。
- **验证及结果**：静态追踪 `ZxingBarcodeImageRenderer.loadOrCreate()`、`LocalBarcodeFileStore.readImage/writeImage()`、`prepareResultRows()`、`composeResultRowImage()` 和 `DisposableEffect`；确认结果行使用新建 Bitmap。未运行测试，未构建 APK；现有 `BarcodeRenderPerformanceBaselineTest` 使用内存缓存替身，不单独证明生产 LRU 的对象释放时序。
- **阻碍/恢复条件/下一步**：无阻碍；继续处理其他未依赖用户决策的检查项。生产缓存异常分类与回归测试边界仍由 CI-154 单独追踪。

### CI-158 — 持续改进批次 Beta 推送与远端构建验证

- **检查范围**：本地 `verifyLocal`、变更集敏感文件检查、`beta` 分支推送、Beta workflow_dispatch、签名 APK 构建与稳定 Beta Release 资产。
- **问题依据/优先级**：将本轮累计的可靠性、回归测试及发布工作流改动交付到 Beta，确保本地与远端验证使用相同提交；不涉及正式版发布。
- **状态和依赖**：已验证，完成；提交 `bea226bb4af1eff2fcf7631844aaf1085ed926f4` 已推送至 `beta`，工作区在交付前干净。
- **修改摘要**：提交 164 个文件，包含可靠性修复、回归测试、发布/构建约束和进度记录；未纳入签名密钥、构建产物或本地验证工具。
- **验证及结果**：本地 `verifyLocal` 成功（191 项 Gradle 任务，4 执行、3 缓存、184 up-to-date）；`DiagnosticThrowableFormatterTest` 定向复测成功；`git diff --cached --check` 通过。远端 Beta Actions run `39809198560` 对同一 SHA 完整成功，签名 Release APK 与稳定 Beta Release 资产发布成功：versionName `9.9.9`、versionCode `5`、文件 `BarcodeGeneratorBeta9.9.9.apk`、SHA-256 `12ca907ed62be57e5b87000ff98ce6f18fb7c4aa5a19e112af963c2050a6afaf`。
- **阻碍/恢复条件/下一步**：无本次交付阻碍；后续 CI-139/140/141/154 均已完成并通过本地验证。CI-144 的 WebSocket 帧大小方案、CI-155 的依赖更新目标分支及若干设备验证项仍按各自记录等待恢复条件。

### CI-159 — LAN HTTP 错误响应被完整读入内存

- **检查范围**：`:core:lan-share` 客户端普通 HTTP 请求与 App 文件上传的非 2xx 错误响应读取和异常信息构造。
- **问题依据/优先级**：确认 P2 资源耗尽风险。普通请求与上传分支原先都对对端错误流调用 `readText()`；loopback 返回 500 并携带 64 KiB 错误正文时，普通请求产生 65,558 字符异常消息。局域网 HTTP 响应未加密，客户端不应假设错误正文总是短且可信。
- **状态和依赖**：已验证，完成；详情最多读取 4 KiB 正文和 1 字节探测位，超长时附截断提示；普通请求与上传错误处理共用同一有界读取实现。
- **修改摘要**：新增 `readLanShareErrorBody()`，固定缓冲上限并保留错误正文前缀；替换普通请求与上传路径的无界 `readText()`。未更改 HTTP 状态处理、成功响应、协议或服务端行为。
- **验证及结果**：loopback 500 响应回归验证异常消息受限；计数流回归验证对 64 KiB 输入最多读取 4 KiB + 1 字节、保留错误前缀并显示截断标记；静态确认上传错误分支调用相同 helper。完整离线 `verifyLocal -PenableAppUnitTests=true --offline` 成功：191 项任务，34 执行、157 up-to-date，覆盖架构检查、Beta/Official Release 编译与 Lint、模块测试；`git diff --check` 通过。未组装 APK、未做真机网络验证。
- **阻碍/恢复条件/下一步**：无本项验证阻碍；继续审查 `:core:lan-share` 的独立路径。WebSocket 帧上限风险仍按 CI-144 等待网络层方案确认，不由本修复覆盖。

### CI-160 — LAN 文件清单的大规模响应峰值内存待评估

- **检查范围**：`:core:lan-share` 的 100 GiB 房间字节上限、`fileRecordsSnapshot()`、服务端 `GET /api/files`/WebSocket `snapshot` JSON 构造和 `LanShareClient.list()` 响应解析。
- **问题依据/优先级**：发现 P2 可扩展性候选，尚未证实为用户可见缺陷。服务端只限制累计文件字节，不限制记录数或清单消息大小；HTTP/WS 快照、`JSONObject`/`JSONArray` 和 JSON 字符串会形成多份中间表示，Android 客户端再以 `readText()`、`JSONArray` 和领域列表重复物化。单字节文件理论上使字节上限无法提供有意义的条数上限。
- **状态和依赖**：待确认；取决于实际最大文件数、目标设备可用内存及期望清单延迟。不能在缺少数据的情况下直接截断清单或引入分页，因为这会改变现有“返回全部文件”的行为。
- **修改摘要**：无代码改动；静态核对 `LanShareLimits.MAX_ROOM_BYTES`、`fileRecordsSnapshot()`、`serveHttp()` 的清单路由和客户端 `list()`。
- **验证及结果**：确认服务端容量上限为字节而非记录数，清单路由序列化全部快照，客户端完整读入响应后解析；未发现数量/正文上限。没有性能回归测试或代表性清单基准，故没有把结构性分配事实夸大为已复现的 OOM/卡顿，也未运行测试。
- **阻碍/恢复条件/下一步**：需要代表性/最大文件数和低端目标设备基准，才能决定流式序列化/解析是否足够，或需要端到端分页；这不阻止继续检查其他 LAN 路径。

### CI-161 — 网页图片预览缓存失效与资源回收复核

- **检查范围**：`:core:lan-share` 的 `LanShareWebImagePreviewCache` 缓存键、并发生成、图片/临时文件回收，以及 `LanShareServer` 上传发布和预览响应调用链。
- **问题依据/优先级**：未发现可复现的 P2 数据错误。缓存键由规范化路径、大小和修改时间组成；可能的同路径、同大小、同修改时间覆盖在理论上会复用旧预览，但服务端上传目标带 `System.nanoTime()` 唯一前缀并明确拒绝覆盖现有文件，正常上传无法产生该情况。预览输出限制最长边 4096、磁盘缓存 128 MiB；JPEG 写入采用临时文件后发布，Bitmap 与临时文件在 `finally` 回收。
- **状态和依赖**：已检查，无需修改；结论限于服务端正常上传生命周期。若未来加入同路径覆盖/原地编辑，需要调整缓存失效契约。
- **修改摘要**：无产品代码修改；记录对预览缓存所有权和源文件更新路径的复核。
- **验证及结果**：静态追踪 `cacheKey()`、`getOrCreate()`、`makeRoom()`、`receiveUpload()` 的唯一目标名及 `/api/preview/` 路由；未发现正常写入可覆盖已缓存源文件的路径。未运行测试或构建，因为未发现可复现异常；现有测试覆盖预览解码器，不专门测试缓存失效竞态。
- **阻碍/恢复条件/下一步**：无阻碍；继续检查其他 LAN 服务端/客户端边界。CI-160 清单峰值仍待实际规模和设备基准。

### CI-162 — WebSocket 出站队列是否有界并隔离慢连接

- **检查范围**：`:core:lan-share` 的 `WebSocketWriteQueue` 队列容量、单连接写入超时、溢出处理和并发回归测试。
- **问题依据/优先级**：未发现可证实的慢客户端队列膨胀缺陷。每个浏览器连接有独立写入队列，默认容量 32；单条写入有 5 秒 watchdog，队列满时关闭该连接；其他 peer 使用不同 writer，不被阻塞。
- **状态和依赖**：已检查，无需修改；依赖连接创建时每个 socket 都创建独立队列且关闭回调关闭其 TCP transport。
- **修改摘要**：无产品代码修改；补充一次出站资源边界复核记录。
- **验证及结果**：静态追踪 `BrowserWebSocket` 的队列创建及 abort/transport.close 回调；现有 `WebSocketWriteQueueTest` 覆盖顺序、慢 peer 超时不阻塞另一 peer、队列溢出只 abort 一次、写失败原因保留。最近完整 `verifyLocal -PenableAppUnitTests=true --offline` 成功并执行 LAN 模块单元测试；本项未增加代码，不单独重跑。
- **阻碍/恢复条件/下一步**：无阻碍；继续检查其他 LAN 路径。该队列检查不覆盖 CI-144 的入站帧分配风险。

### CI-163 — Beta 发布工作流使用已弃用的 Node.js 20 Artifact Action

- **检查范围**：Beta Release 发布 job 下载当前 workflow run 构建的 APK artifact 的 Action 版本及运行时兼容性。
- **问题依据/优先级**：远端 run `39879019894` 实际标记 `actions/download-artifact@v6.0.0` 将被强制运行于 Node.js 24。GitHub 已于 2026-09-23 移除 Node.js 20 runner 支持；继续固定在旧 runtime 的 action 会保留弃用告警并存在后续兼容风险。官方 `v8.0.2` 的 action metadata 使用 `node24`，且仍支持当前同 run artifact 下载路径。
- **状态和依赖**：已推送，待远端验证；只涉及发布 job 使用的第一方 Action，不改变 APK、artifact 命名或 Release 流程。实际 Node 24 hosted-runner 执行依赖下一次 Beta workflow run。
- **修改摘要**：将 Beta Release 的 `actions/download-artifact` 从 v6.0.0 升级并固定到官方 v8.0.2 commit SHA `9000827ccba6bdab643e8b6fd33ac0654aef8333`；正式版流程没有该下载步骤，不修改。
- **验证及结果**：已从官方 release/tag 与 `action.yml` 核对 v8.0.2 commit 和 `runs.using: node24`；本地完整离线 `verifyLocal -PenableAppUnitTests=true --offline` 成功（191 项任务，4 执行、187 up-to-date），包括架构契约测试；`git diff --check` 通过。尚未在远端实际运行新 Action。
- **阻碍/恢复条件/下一步**：使用当前 `origin/beta` HEAD 的下一次 Beta workflow run 确认远端下载步骤通过；与 CI-067 共用验证。无需为 Action pin 单独生成 APK，发布流程仍为手动触发。

### CI-164 — 持续改进记录保留 CI-126 的过期阻塞引用

- **检查范围**：持续改进记录中 CI-126 的状态及后续 CI-127、CI-128、CI-131 的恢复条件说明。
- **问题依据/优先级**：确认 P3 文档一致性问题。CI-126 已记录为“已验证，完成”，且提交 `03e37db` 包含收藏搜索重载修复与定向回归测试；但三个后续条目仍称 CI-126 待确认、待 UI 复验或缺确定性复现，容易误导后续推进并重复排查。
- **状态和依赖**：已修复并完成静态核验；不依赖产品代码、设备或外部服务。
- **修改摘要**：移除 CI-127、CI-128、CI-131 后续说明中的过期 CI-126 阻塞描述，保留各条目仍然有效的其他下一步。
- **验证及结果**：对照 CI-126 完成记录、提交 `03e37db` 的文件清单与回归测试；搜索全文确认不存在将 CI-126 描述为待确认、待复验或待复现的残留引用；`git diff --check` 通过。未运行产品测试或构建。
- **阻碍/恢复条件/下一步**：无；继续审查不依赖设备、旧版数据库样本、用户决策或远端认证的 `:app` 路径。

### CI-165 — 持续改进记录未反映已推送的 workflow 改动

- **检查范围**：CI-067、CI-084、CI-085、CI-117、CI-163 的状态/恢复条件与 `origin/beta` 当前提交及 workflow 文件。
- **问题依据/优先级**：确认 P3 进度准确性问题。CI-067/084 将已推送的 Beta 双 job 与 SHA 固定继续写成工作树未推送；CI-085/117 将已推送的 PR workflow 写成未推送；CI-163 仍把“推送工作流修复”列为下一步。当前 `HEAD` 与 `origin/beta` 均为 `f98aec2`，相关提交均可从该分支到达；Beta workflow 已包含独立 `build`/`publish` job 和固定到 Node 24 的 `actions/download-artifact@v8.0.2`，PR workflow 已包含 `verifyLocal` 门禁。
- **状态和依赖**：本地记录已修正；远端 hosted-runner 对当前流程的验证仍待实际 Beta workflow run。
- **修改摘要**：更正 CI-067、084、085、117、163 中已推送 workflow 改动的状态，明确分支保护未配置与 workflow 托管端执行验证是不同事项；Beta 双 job 和 Node 24 Action 共用下一次远端 Beta run 验证。
- **验证及结果**：用 `git rev-parse` 核实 `HEAD` 与 `origin/beta` 同为 `f98aec2`；用 `merge-base --is-ancestor` 核实相关 workflow 提交已进入远端分支；静态检查 Beta workflow 的 job 依赖、Action SHA 与 PR workflow 的 `verifyLocal` 命令；`git diff --check` 通过。只读 GitHub Actions API 查询被当前 GitHub connector 拒绝为不支持的 endpoint，未重试或改用其他工具。
- **阻碍/恢复条件/下一步**：只有当前 `origin/beta` HEAD 的实际 GitHub Actions run 能验证托管端行为；本地静态检查不能替代该验证。继续审查其他可本地推进的高影响路径。

### CI-166 — `:app` 设置和相机识别 ViewModel 的取消边界复核

- **检查范围**：`SettingsViewModel` 的设置加载/异步写入，以及 `CameraOcrViewModel` 的 OCR/条码识别错误分类和 Bitmap 清理。
- **问题依据/优先级**：针对异步取消被误记为普通失败、UI 收到伪失败提示或识别 Bitmap 未释放的 P2 稳定性风险复核；未发现新的可证实问题。设置加载显式记录并重抛 `CancellationException`；写入完成回调将取消与失败分开，取消不会触发 `saveFailed` 或失败通知。OCR 和条码识别均显式重抛取消，仅将普通 `Exception` 降级为既有用户反馈/空结果；OCR Bitmap 在 `finally` 与任务完成回调中检查后释放，避免泄漏或重复回收。
- **状态和依赖**：已检查，无需修改；依赖当前 `SettingsRepository` 的 Job 写入契约及 `CameraOcrFacade` 网关调用。
- **修改摘要**：无产品代码修改；补充一次设置和相机识别异步边界复核。
- **验证及结果**：使用固定本地环境运行 `:app:testBetaDebugUnitTest --tests '*SettingsViewModelTest' --tests '*CameraOcrViewModelTest' --rerun-tasks` 成功，两个测试类各 5 项、共 10 项，0 失败/错误；73 项 Gradle actionable tasks 执行。未组装 APK。普通沙箱 daemon IPC 连接失败后，按项目权限流程用相同参数授权重试成功。
- **阻碍/恢复条件/下一步**：无本项阻碍；继续检查其他可本地验证的核心路径。结果页编辑的历史崩溃日志已与 CI-111 的 GlassBackdrop 回归保护对应；收藏保存仍缺少其自身崩溃堆栈，完整对话框回归测试接缝仍待确认（CI-079）。

### CI-167 — LAN 图片预览解码失败的降级边界复核

- **检查范围**：`:core:lan-share` raster/TIFF 预览解码、浏览器预览缓存路由，以及 `:app` 预览解码器适配边界。
- **问题依据/优先级**：扫描发现 raster 解码的 `runCatching` 会把包括 `OutOfMemoryError` 在内的 `Throwable` 转为 `null`，表面上类似吞掉致命错误；但当前调用是可选预览，不承载原始文件读取/写入。服务端在预览结果为 `null` 时返回明确的 406 下载原图提示；TIFF 解码器注释明确说明解码失败回退到常规附件 UI，并显式处理 `OutOfMemoryError`。未找到由该降级引起的用户可见错误或数据损失证据。
- **状态和依赖**：已检查，无需修改；结论限于可选预览解码，不适用于持久化、上传或原始文件下载路径。
- **修改摘要**：无产品代码修改；记录预览 `null` 结果的既有回退契约。
- **验证及结果**：静态追踪 `LanShareWebImagePreviewCache.getOrCreate()` 到 `/api/preview/` 的 406 下载提示，核对 TIFF 解码器的失败契约、App 适配器的可空返回类型及 `LanShareViewModel` 缓存成功解码结果的方式。未运行测试，因为未修改代码且没有需复现的产品故障；未构建 APK。
- **阻碍/恢复条件/下一步**：无；若将来有日志证明预览失败导致进程持续处于内存异常状态，需以该实际场景重新评估 OOM 降级策略。继续检查其他独立的 LAN/数据路径。

### CI-168 — LAN 浏览器文件名到 DOM 与下载响应头的注入边界

- **检查范围**：浏览器文件列表/预览/聊天内容的动态 DOM 写入、文件 ID 的 URL 使用，以及服务端上传文件名进入 `Content-Disposition` 的路径。
- **问题依据/优先级**：核查潜在的已配对浏览器持久型 XSS 和 CR/LF 响应头注入；未发现可证实的漏洞。网页脚本未使用 `innerHTML`、`outerHTML`、`insertAdjacentHTML` 或 `document.write`；文件名、聊天内容和错误提示使用 `textContent`，文件 ID 进入 URL 前通过 `encodeURIComponent`。服务端上传文件名用 `safeFileName()` 替换路径分隔符、引号及 CR/LF，并限制为 100 字符，再作为共享目录文件名和下载显示名。
- **状态和依赖**：已检查，无需修改；结论适用于当前内置网页资源和服务端上传入口。
- **修改摘要**：无产品代码修改；记录对浏览器不可信内容输出和下载头路径的静态审查结果。
- **验证及结果**：静态枚举 `LanShareWebScript` 的动态内容写入点、`LanShareServer` 的 `/dl`/`/api/preview` 路由及 `safeFileName` 调用；确认网页模板为静态组装，不插入请求值。现有测试未专门模拟恶意文件名在真实浏览器中的 DOM/HTTP 头表现，因此未声称完成浏览器端到端安全测试；没有发现需要添加产品修复的证据，未运行测试或构建。
- **阻碍/恢复条件/下一步**：无已确认漏洞；若今后改用 HTML 字符串模板或增加用户值动态拼接，需重新审核并优先在浏览器 DOM/loopback HTTP 边界补回归覆盖。继续审查其他独立 LAN 路径。

### CI-169 — LAN 下载路由将客户端声明的 SVG 类型内联返回

- **检查范围**：`:core:lan-share` 上传 `Content-Type` 的保留规则、服务端 `/dl/{id}` 响应的 `Content-Type`/`Content-Disposition` 与浏览器文件列表下载行为。
- **问题依据/优先级**：确认 P2 安全边界问题。上传侧 `uploadedMimeType()` 接受任意格式正确的客户端 MIME（包括 `image/svg+xml`），服务端在当前运行期间保留该值；`/dl` 以 `startsWith("image/")` 决定 `Content-Disposition: inline`。因此一个上传的 SVG 会被同源 HTTP 下载端点作为可内联文档提供。SVG 直接作为独立文档打开时与 `<img>` 图片上下文的脚本限制不同；这里已确认的是危险响应策略，尚未用真实浏览器验证脚本执行链。`/api/preview` 是独立的受限预览接口，网页直接下载控件也声明了 `download`，但不能保证所有直接访问 `/dl` 的场景都遵循该 UI 路径。
- **状态和依赖**：已确认，待修复；计划让 `/dl` 语义固定为附件下载、附加 `X-Content-Type-Options: nosniff`。拟在现有 `LanShareServerUploadTest` 的上传→真实 loopback `/dl` 边界验证响应。按测试 seam 约定，已向用户请求确认该回归边界，尚未收到答复，未写测试或产品代码。
- **修改摘要**：无；保留原始上传 MIME 供文件列表/预览判断，不在确认测试边界前改动下载行为。
- **验证及结果**：静态追踪 `uploadedMimeType()`、`uploadedMimeTypes`、`fileMimeType()`、`/dl` 与浏览器 `fileUrl()` 调用；确认 `/api/preview` 走单独转换/拒绝路径。无当前实现红灯回归测试，未声称已复现浏览器脚本执行；未运行测试或 APK。参考 MDN 对 `Content-Disposition` inline/attachment 及独立 SVG 脚本上下文的说明。
- **阻碍/恢复条件/下一步**：等待用户确认 loopback `/upload`→`/dl` 回归测试 seam；确认后先运行红测，再作最小服务端响应修复并验证。独立模块审查继续推进。

### CI-170 — Android API 26 最低版本与直接调用的 Java API 兼容性

- **检查范围**：Android 模块 `minSdk` 与生产代码直接使用的 `java.util.Base64`、`java.nio.file`、`java.time` API。
- **问题依据/优先级**：针对低版本设备调用平台缺失 API 导致崩溃的 P2 兼容性风险核查；`:app`、`:core:data`、`:core:lan-share` 均声明 `minSdk = 26`。扫描到的 `Base64`、NIO 文件 API 和 `LocalDate` 均从 Android API 26 开始可用；无需额外 core-library desugaring，也未发现这些直接调用低于模块最低版本。
- **状态和依赖**：已检查，无需修改；结论只覆盖上述直接平台 API 兼容性，不代替所有设备版本的运行验证。
- **修改摘要**：无产品代码修改；记录最低 SDK 与所查平台 API 的一致性。
- **验证及结果**：读取三个 Android 模块 Gradle 配置并枚举生产源码调用；运行 `D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline :app:lintBetaRelease :app:lintOfficialRelease`，`BUILD SUCCESSFUL`，94 项 actionable tasks（2 执行、92 up-to-date），未组装 APK。
- **阻碍/恢复条件/下一步**：无本项阻碍；继续按模块检查其他独立路径。

### CI-171 — 历史列表以毫秒时间戳作为生成批次身份

- **检查范围**：`:app` 历史页 `HistoryScreen` 分组键、领域生成用例时间戳赋值、Room 历史顺序及最终条码 ID 分配。
- **问题依据/优先级**：确认一个低概率、仅影响展示分组的 P3 边界。每次 `GenerateBarcodesUseCase.execute()` 将一次 `now` 值赋给本次所有生成条码；历史页使用 `groupBy { createdAt }` 作为批次边界。因此两个独立生成操作若得到相同毫秒时间戳，会合并显示为同一历史卡片。Room 查询已有 `createdAt DESC, id DESC` 稳定排序，持久化条码和 ID 不丢失；风险是卡片批次边界不再等同于用户操作边界。
- **状态和依赖**：待确认；是否要求每次生成操作始终对应独立历史卡片，当前产品语义未写明。可靠区分需要显式批次身份（会涉及数据库迁移及旧记录兼容），未用人为时间戳偏移掩盖真实时间，也未修改行为。
- **修改摘要**：无产品代码修改；记录时间戳分组和操作批次身份并非同一概念。
- **验证及结果**：静态追踪 `GenerateBarcodesUseCase.execute(now)`、`HistoryScreen.groupBy(createdAt)`、`BarcodeDao.loadStartupItems()` 排序及 `insertGeneratedItemsWithAllocatedIds()`；确认所有条码仍以独立 ID 保存。未添加测试、运行测试或构建；没有证据显示条码内容丢失或数据库排序不稳定。
- **阻碍/恢复条件/下一步**：需确认“同一毫秒的两次生成是否必须拆成两张历史卡片”；若需要，再设计向后兼容的批次 ID 迁移及回归 seam。继续检查与该产品决定无关的其他模块。

### CI-172 — 收藏搜索与常规分页并发状态复核

- **检查范围**：收藏页规范化查询状态、搜索取消/刷新、搜索分页互斥、常规分页代次校验、收藏树 UI 的查询身份与占位状态。
- **问题依据/优先级**：针对快速改写搜索词、同时触发分页或收藏变更时，旧查询结果覆盖新查询、旧常规分页污染变更后快照的 P2 风险复核；未发现新的可证实缺陷。已有保护包括：常规分页提交前比较 `regularGeneration`；搜索首屏、分页、重置和刷新共用 `searchMutex` 并校验 `activeSearch`；搜索状态携带规范化 query，界面在 query 未匹配或仍 busy 时不呈现旧结果；导入重载代次纳入搜索 effect key（CI-126）。
- **状态和依赖**：已检查，无需修改；结论限于当前状态协调与已有单元/Robolectric 覆盖，不等同于设备上的压力测试。
- **修改摘要**：无产品代码修改；记录对收藏常规列表、搜索分页和查询切换并发边界的复核。
- **验证及结果**：运行 `D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline :app:testBetaDebugUnitTest --tests '*FavoritesQueryCoordinatorTest' --tests '*FavoritesSearchSnapshotRefreshTest'`，`BUILD SUCCESSFUL`；10 项查询协调器测试与 1 项同快照刷新测试均通过，0 失败/错误，73 项 Gradle actionable tasks（1 执行、72 up-to-date）。首次沙箱运行在测试开始前因 Gradle daemon 回环通信失败；获批后以相同命令重试通过。未组装 APK。
- **阻碍/恢复条件/下一步**：无本项阻碍；继续审查可独立推进的收藏数据和其他核心路径。CI-169 的 loopback 下载响应测试与 CI-171 的历史批次产品语义仍待用户确认，不阻塞本项。

### CI-173 — 收藏备份往返的条码顺序与重复内容复核

- **检查范围**：`:core:data` 收藏 ZIP 导出/导入顺序、同内容条码身份分配、Room 收藏关联位置约束与导入提交事务。
- **问题依据/优先级**：针对备份往返可能合并重复条码内容、重排收藏文件内条码或留下部分导入数据的风险复核；未发现新的可证实缺陷。导出按 `position, itemId` 读取关联并逐项写入文本/格式；导入为每个文本单独分配 item ID 并生成连续 position；Room 对 `(groupId, position)` 建唯一索引，导入表与关联在单一事务内提交。相同条码正文不会参与收藏文件身份判重。
- **状态和依赖**：已检查，无需修改；结论限于当前 ZIP 与 Room 导入/导出实现和覆盖到的测试，不替代真实用户备份文件的设备恢复演练。
- **修改摘要**：无产品代码修改；记录备份数据顺序、重复内容和事务边界复核。
- **验证及结果**：运行 `D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline :core:data:testDebugUnitTest --tests '*FavoritesTransferManagerTest' --tests '*BarcodeDatabaseMigrationTest'`，`BUILD SUCCESSFUL`；收藏传输 18 项、数据库/导入迁移 12 项，共 30 项，0 失败/错误，34 项 Gradle actionable tasks（1 执行、33 up-to-date）。首次沙箱运行在测试开始前因 Gradle daemon 回环通信失败；获批后以相同命令重试通过。未组装 APK。
- **阻碍/恢复条件/下一步**：无本项阻碍；继续检查 `:core:data` 中其他独立持久化路径。旧版权威数据库 fixture 仍按 CI-081 等待恢复条件；未影响本次现行备份传输验证。

### CI-174 — 设置异步初始化期间的用户修改被加载结果覆盖

- **检查范围**：首屏 Compose 外壳先显示与 Activity 后台加载设置的时序、`SettingsViewModel` 的预加载交互、设置写入队列与旧设置迁移提交顺序。
- **问题依据/优先级**：确认 P2 设置丢失竞态。`MainActivity` 挂载交互界面后才异步加载设置；设置页可在加载结束前操作。原 `initialize()` 无条件把读取值覆盖到 ViewModel，用户刚修改的值会回退；预先排队的写入还可能早于旧设置迁移提交，令迁移覆盖这些修改。
- **状态和依赖**：已验证，完成（本地）；范围限于设置 ViewModel 首次加载期间的设置与 OCR 纠错修改。
- **修改摘要**：初始化时按字段合并加载期间实际改动的样式项，保留其他已存值；保留早期 OCR 选择，并在初始化完成后重新排入持久化队列，确保旧设置迁移之后仍以用户修改为准。
- **验证及结果**：新增确定性挂起加载回归测试；修复前定向测试失败，失败点为最终主题值被 `initialize()` 回滚；修复后该测试通过。完整 `SettingsViewModelTest` 类 6 项通过、0 失败/错误。使用固定脚本运行 `:app:testBetaDebugUnitTest --tests '*SettingsViewModelTest'`，`BUILD SUCCESSFUL`（73 项 Gradle actionable tasks，4 执行、69 up-to-date）；未组装 APK、未做设备验证。
- **阻碍/恢复条件/下一步**：无本项阻碍；继续检查独立的核心数据与网络路径。CI-169 的 loopback 下载响应回归边界仍待用户确认，CI-171 的历史批次产品语义仍待确认，均不阻塞后续独立审查。

### CI-175 — 条码渲染缓存键输入完整性复核

- **检查范围**：ZXing 实际使用的格式、条码文本、像素宽高、明暗配色与 `barcodeRenderKey` 输入；与非像素标签设置和结果行间距区分。
- **问题依据/优先级**：未发现缓存错误复用的产品缺陷。键含有条码文本、原始格式、渲染算法版本、最终像素宽高和明暗模式；结果页标签和条码间距由外层合成处理，不应让编码位图缓存失效。显示格式、文字大小等标签设置不会改变条码矩阵。
- **状态和依赖**：已检查，无需产品修改；结论限于当前 ZXing 编码位图键与结果页合成职责。
- **修改摘要**：补充测试，明确覆盖条码文本、格式、Code 128 宽度和高度变化都会使缓存键变化；不改变缓存或渲染行为。
- **验证及结果**：运行固定脚本 `D:\Barcode_build\run-barcode-android-gradle.ps1 -PenableAppUnitTests=true --offline :app:testBetaDebugUnitTest --tests '*BarcodeRenderKeyTest'`，`BUILD SUCCESSFUL`；该测试类 3 项通过，0 失败/错误，73 项 Gradle actionable tasks（4 执行、69 up-to-date）。未组装 APK。
- **阻碍/恢复条件/下一步**：无本项阻碍；继续检查其他高影响独立路径。当前没有证据要求变更缓存键格式或使标签设置触发条码矩阵重算。
