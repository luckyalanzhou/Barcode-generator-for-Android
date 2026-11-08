# 全页面性能优化目标与收藏页专项证据

## 意图

系统性降低应用所有页面的可感知卡顿和帧延迟，以同一台 120 Hz 真机上的 Perfetto 实际呈现帧及页面交互证据驱动修复。目标覆盖生成、历史、收藏、设置、结果、局域网分享六个 `NavigationRoute` 页面，以及它们共用的页面外壳、导航和视觉效果。先前工作的证据集中在收藏与底栏导航；它们只作为已调查的专项，不代表其他页面已经测量或通过验收。真机测试目前按用户要求暂停，暂停期间只做代码与已有证据梳理，不连接或操作手机，也不宣称设备验收完成。

## 目标与范围

- 页面覆盖：生成（输入、格式选择、批量生成）、历史（进入、列表滚动、编辑/删除/打开）、收藏（进入、搜索、展开/折叠、滚动、菜单操作）、设置（进入、滚动、样式切换）、结果（生成/历史进入、图片列表滚动、预览/导出操作）、局域网分享（进入、消息/文件列表滚动与已有交互）。此外检查共用外壳的页面切换、底栏手势、背景/玻璃效果是否放大各页成本。
- 保持现有业务语义和页面交互；只修复可复现且有证据支持的性能问题，不做无关重构或视觉调整。
- 对已调查的收藏专项，保留已有行投影缓存，并维持行几何数据通过非 Snapshot 当前坐标引用更新；是否继续保留由当前代码与后续证据确认，不以扩大缓存为默认策略。
- 暂不常驻整个收藏页，不改变应用刷新率策略；不修改 Beta/正式版。
- 真机验证暂停期间不尝试安装、启动、解锁或交互设备；设备专属结论留待用户恢复测试后取得。

### 页面与证据状态

| 页面/共享层 | 当前覆盖 | 后续需要分别观察的用户路径 | 证据状态 |
| --- | --- | --- | --- |
| 生成 | 有现存 Compose 实现与调试日志 | 冷/暖进入、输入编辑、格式弹窗、批量生成 | 未见本地专属 FrameTimeline 样本 |
| 历史 | 有列表与行操作 | 进入、长列表滚动、编辑/删除/打开记录 | 未见本地专属 FrameTimeline 样本 |
| 收藏 | 已做坐标状态和投影缓存调查 | 进入、搜索、展开/折叠、滚动、切出返回 | 有多份 Perfetto trace；滚动目标尚未通过 |
| 设置 | 有设置列表、滑块和样式更新 | 进入、滚动、滑块/样式切换、检查更新 | 未见本地专属 FrameTimeline 样本 |
| 结果 | 有条码图像行与图片绘制逻辑 | 从生成/历史进入、列表滚动、预览/导出 | 未见本地专属 FrameTimeline 样本 |
| 局域网分享 | 有消息/文件列表和会话状态 | 进入、会话更新、列表滚动及文件交互 | 未见本地专属 FrameTimeline 样本；网络/设备行为需单独验证 |
| 共用外壳与导航 | 覆盖过底栏拖动和部分切页 | 六页点按/拖动切换、页面切入首帧、玻璃背景 | 有部分导航 trace；尚未全路由覆盖 |

“未见专属样本”表示当前性能记录没有可用于该页面的有效数据，不等于页面已知有问题或已通过。

### 真机暂停期间的源码核查（2026-11-04）

- 六个页面目前没有新增可用的帧证据；收藏与共用外壳继续沿用下方已经记录的 Perfetto 结果。生成、历史、设置、结果和局域网分享仍不能据静态代码检查判定卡顿或通过门槛。
- 结果页 `ResultsContent` 的工具栏尺寸由 `onSizeChanged` 写入 Compose `toolbarSize`，同一状态随后在组合阶段读取并换算为列表顶部 padding。它构成页面进入时可能触发一次额外组合/测量的源码候选；没有结果页首帧 trace，暂不修改或归因交互卡顿。
- 设置页两个下拉锚点尺寸由 `onGloballyPositioned` 写入 Compose 状态，并在组合阶段读取以定位菜单。更新被尺寸相等性抑制，候选影响限于锚点测量变化时的组合；未发现证据表明它参与持续滚动卡顿，暂不改动。
- 收藏行坐标回调已改写普通坐标引用，收藏行投影缓存仍由应用壳持有。离线定向 JVM 任务 `:app:testBetaDebugUnitTest` 的收藏树、动画策略和无障碍测试通过；这只能证明对应源码可编译并通过这些单元测试，不能替代设备帧验收。
- 下一步需要同一 profileable、`debuggable=false` Debug 构建逐页取得实际呈现帧；先验证上述候选是否出现在受影响操作的长帧路径，再决定是否修复。真机暂停时不操作设备。

## 方案选择

全页面工作逐页分层推进：先复核每页的调用链和已有 trace，按共享外壳、页面状态/重组、测量布局、绘制/GPU、帧队列定位证据；再对单一瓶颈做最小修复和本地可行验证。若某个修复影响共用外壳，应记录受影响的页面并在恢复设备测试后逐页复测。不得把收藏页结论套用到其它路由。

收藏页既有方案记录：行锚点几何改为普通坐标引用，布局回调只更新引用，长按时读取并构造菜单锚点；整页常驻组合曾试验但滚动证据较差，已撤回。保留这些历史决策作为本专项背景，不预先规定其他页面采用同类技术。

## 验收

- 每个路由页面和共用导航/外壳都有单独的路径清单、基线或明确的“未采集”状态；所有性能结论必须能对应到页面、操作、构建身份和有效 trace，禁止以单页数据代表全应用。
- 真机测试恢复后，使用相同 profileable 非 debuggable Debug 包、同一设备与刷新模式，对各页面分别预热并重复关键操作；记录页面进入到首个可见内容呈现、持续交互期间的 `On-time Present` 比例、`App Deadline Missed` 数量及需要时的队列延迟。网络/相机等系统行为与纯 UI 帧测量分开记录。
- 对每个已识别且本轮承诺修复的页面交互，验收目标为交互帧至少 95% 按时呈现、应用期限超时帧为 0；若页面不适用持续帧测试，则以可重复的进入/操作首帧延迟与功能回归条件验收。汇总时逐页报告，不允许用总体平均掩盖单页失败。
- 每个修复保持既有行为；仅当新有效证据显示改善且其它受影响页面没有回归，才保留实现。未达到门槛的页面明确列为未通过，不以构建成功替代设备验收。
- 真机测试暂停期间，只能报告代码审查、已有 trace 的重分析及本地构建/静态检查；不得把它们表述为全页面设备验收。
- 不安装、替换或改动 Beta/正式版；不提交或推送。

## 实施边界

按模块分层、逐页迭代。修复范围由有效性能证据决定；不扩展为无关重构。当前先使用代码、现有 trace 和本地可用检查推进，真机测试未获恢复前不操作设备；涉及设备行为的验收保持待办。

## 收藏页专项推进方案（历史阶段记录）

按用户确认的方案分阶段推进：先验收底栏拖动期间不导航，再分别评估点按/滑动进入收藏的首帧与收藏列表纵向滚动。不要把两类开销混在同一轮改动或指标中。

### 阶段一：底栏拖动只在松手时导航

- 拖动期间仅更新液态玻璃指示器、候选 Tab 和现有触感反馈。
- 手势完成且最终 Tab 与当前页不同时，提交一次导航。
- 手势取消或返回原 Tab 时，不导航；普通点按保持立即切页。
- 真机验证必须从无障碍节点确认的底栏实际命中区域发起，覆盖历史滑过收藏到设置、拖动取消/折返、点按收藏。
- 历史滑过收藏到设置时，一次手势只在松手提交一次导航；拖动期间收藏页不得进入组合或首次测量。代码调用链和 Perfetto 事件需共同支持此结论。
- 验收后再决定是否继续处理阶段二的首帧和滚动问题。

### 阶段二：收藏页首帧与位置恢复

- 将滚动位置恢复与行展开/折叠动画资格拆分，避免一个“已恢复”状态同时控制定位和动画。
- 仅在收藏页面实际成为目标页时恢复位置；优先使用 `LazyListState` 保存的位置，只有没有有效保存状态时才调用备用位置恢复。
- 验证点按进入、滑动进入、中部滚动后离开再返回、搜索后清空查询等路径。
- 用相同真机、数据和路径做至少 5 轮对照；分别记录收藏首帧和列表滚动帧。仅当 Perfetto trace 明确含应用实际呈现帧时，才据其判定 120 Hz 帧目标。

### 当前执行记录（2026-11-03）

- 阶段一已实现：拖动时只更新指示器、候选 Tab 和触感，成功松手时最多提交一次导航；折返和取消不导航，点按继续即时导航。
- 阶段二已实现：收藏树与搜索结果使用独立 `LazyListState`；收藏树位置恢复与行变化动画资格分离。保留现有行投影缓存，未保留整个收藏页组合。
- 搜索清空回归通过：普通列表滚到 `HD145X` 后搜索 `Optoma` 并清空，首个可见条目与偏移仍为 `HD145X`（`[271,644][1177,728]`）。此前共用一个 `LazyListState` 会在清空时丢失普通列表位置，因此改为独立状态。
- 设备为 Xiaomi 2201122C、Android 15，显示模式 120.00001 Hz。`BarcodeGenerator-Debug.apk` 仅覆盖安装到 `com.luckyalanzhou.barcodegenerator.debug`。`assembleBetaDebug` 成功；没有安装或替换 Beta/正式版。
- 底栏验证：历史页经收藏拖到设置，5 次往返完成后回到历史；单独折返与取消均保持历史页；点按收藏可即时进入。拖动过程中没有松手提交，代码路径只有释放时回调导航。
- 三份新 Perfetto trace 均包含真实 Debug 应用帧：
  - `barcode-favorites-final-entry.perfetto-trace`：历史与设置间跨过收藏 5 次；947 帧，按时 543（57.3%），含 `App Deadline Missed` 19 帧，最大应用帧 50.09 ms。
  - `barcode-favorites-final-taps.perfetto-trace`：点按进入/离开收藏 5 轮；574 帧，按时 284（49.5%），期限超时 10 帧，最大应用帧 54.50 ms。
  - `barcode-favorites-final-scroll.perfetto-trace`：展开的 40 条 Optoma 收藏中慢滚 5 次、短 fling 2 次；在手指移动窗口内有 753 帧，按时 309（41.0%），期限超时 15 帧、Buffer Stuffing 429 帧，最大应用帧 20.18 ms。
- 滚动 trace 的 Debug 主线程 `AndroidOwner:measureAndLayout` 591 次、平均 0.627 ms、最大 7.692 ms；`Recomposer:recompose` 最大 0.794 ms，`draw-VRI[MainActivity]` 最大 7.130 ms。当前证据不支持继续扩大 Compose 缓存或常驻页面；呈现延迟主要被 Perfetto 标为 Late Present / Buffer Stuffing，但还没有找到设备显示合成链路的更深层原因。
- **验收未通过**：实际呈现的按时帧未达到 95%，并且三份采样均出现应用期限超时。继续目标时应围绕 Late Present / SurfaceFlinger 与设备刷新策略做证据分析；除非新 trace 指出应用侧瓶颈，不再扩大 UI 改动。该统计仅用于当前 profileable Debug 构建与这些操作样本，不代表 Beta/正式版或所有使用场景。
- trace 放在本机忽略目录 `.local-validation/device-validation-2026-11-03/`。Trace Processor 报告少量 `trace_sorter_negative_timestamp_dropped`；FrameTimeline 仍提供有效应用实际呈现帧，本记录的帧统计只来自该应用的 `android_frames`。

### 按分步方案继续执行（2026-11-03 晚间）

- 修复一次先前行布局试验留下的 `FavoritesContent.kt` 大括号错误后，`assembleBetaDebug` 构建通过。安装到 Xiaomi 2201122C 的仍是 `com.luckyalanzhou.barcodegenerator.debug`，并确认 `run-as` 返回 `package not debuggable`；屏幕 render frame rate 为 120.00001 Hz。
- 真机底栏拖动复核：从历史 Tab 拖过收藏并松手后进入设置。普通点按仍即时切页。首轮五次拖动采样 trace 已保存；其实际 FrameTimeline 尚未解析，因此该轮只证明路径可操作，不报告新的帧验收数字。
- 真机列表复核：展开既有 Optoma 文件夹后，列表可纵向滚动。由中部锚点 `EL5500W`（`[271,670][1177,754]`）切到生成页再回收藏，锚点和偏移完全一致。搜索 `Optoma` 后清除，普通收藏树也恢复到同一锚点。
- 本轮 Perfetto 文件保存在本地忽略目录：`barcode-favorites-plan-iteration1.perfetto-trace`（五次历史到设置拖动）和 `barcode-favorites-plan-taps1.perfetto-trace`（五轮生成/收藏点按）。当前机器 PATH 中没有 Trace Processor shell；尝试从官方地址获取官方 wrapper 时，Windows `curl` 报 `SEC_E_NO_CREDENTIALS`，没有下载文件。trace 已采集但还未得到按时比例、期限超时数、首帧中位数/最慢值，不能据此宣称达到 95%/0 帧门槛。
- 最初拖动 trace 经本机解析只有 SystemUI/SurfaceFlinger 帧、Debug 应用 0 帧，作为无效样本丢弃。随后核对采集 helper、20 秒配置和 trace 文件路径，用 stdin 方式确认 Trace Processor 输入与保存文件匹配；本机缓存已有 `trace_processor_shell`，无需下载或外传数据。
- 重录的五次 History→Settings 拖动 trace `barcode-favorites-plan-drag2.perfetto-trace`：689 个 Debug 应用实际帧，673 个 `on_time_finish=1`（97.7%），11 个带 `App Deadline Missed`，最大帧 44.636 ms。应用帧集中在前 6 秒交互窗口；`AndroidOwner:measureAndLayout` 23 次，平均 6.646 ms、最大 26.671 ms；`Recomposer:recompose` 556 次，最大 9.201 ms。FrameTimeline 确认这是有效应用帧，但此汇总不能单独说明中间收藏页是否组合；代码调用链仍显示拖动阶段没有导航回调，且最终松手落在设置。trace 有 2 个 `trace_sorter_negative_timestamp_dropped`。
- 复核点按 trace `barcode-favorites-plan-taps1.perfetto-trace`：只有 43 个应用实际帧，16 个 `on_time_finish=1`、3 个应用期限超时；其中存在 10 个负时间戳丢弃。该样本没有足够的逐次进入/首帧相关帧用于可靠计算首帧中位数和最慢值。
- 按 Perfetto 官方 FrameTimeline 定义，`present_type` 用于判断帧早到、按时或迟到；`on_time_finish` 只表示应用是否在预计期限内完成工作。后续“按时呈现比例”统一按 `present_type = 'On-time Present'` 计算，不能把 `on_time_finish=1` 当作屏幕按时显示。
- 因此滚动 trace `barcode-favorites-plan-scroll1.perfetto-trace` 的按时呈现为 535/638（83.9%），其中 43 帧虽 `on_time_finish=0` 但仍按时呈现；102 帧为 `Buffer Stuffing / Late Present`，1 帧为 `App Deadline Missed / Late Present`。`traversal` 638 次、平均 0.941 ms、最大 3.022 ms，`Recomposer:recompose` 12 次且最大 0.048 ms；最大应用帧 16.707 ms。trace 有 16 个负时间戳丢弃。
- 拖动 trace `barcode-favorites-plan-drag2.perfetto-trace` 重新按呈现结果统计为 255/689（37.0%）按时呈现，422 帧为 `Buffer Stuffing / Late Present`，另有 11 个含应用期限超时的帧；此前的 673/689（97.7%）只是 `on_time_finish=1`，不代表画面按时呈现。此 trace 含 2 个负时间戳丢弃。
- 点按 trace `barcode-favorites-plan-taps1.perfetto-trace` 只有 43 个应用帧，其中 38 帧按时呈现（88.4%），且有 3 个包含应用期限超时的帧；10 个负时间戳丢弃。样本太少，不能计算可靠的进入首帧中位数/最慢值。
- 滚动单帧复核：唯一的 App Deadline Missed 与一次 `ACTION_MOVE` 处理、`Choreographer#doFrame` 约 9.2 ms、`RenderThread DrawFrames` 约 3.7 ms 同时出现；该时段前一帧等待 GPU 完成约 7.4 ms，并记录到 SurfaceFlinger 的 60→120 Hz render-rate 转换。时间相关不证明刷新率转换是根因；目前证据指向该帧 UI/渲染工作接近或超过 120 Hz 预算并伴随缓冲排队，尚不能归因单一环节。
- 当前性能验收仍未通过：滚动按时呈现 83.9%（低于 95%），并有 1 个应用期限超时；拖动和点按样本也分别包含明显晚呈现/期限超时。先完成阶段一的导航提交时机验收；阶段二首帧中位/最慢值仍需有效逐次采样。整页组合常驻方案继续不启用；没有触碰 Beta/正式版或收藏内容。

### 按用户提供的两阶段优化方案推进（2026-11-03）

- **阶段一代码及交互复核通过**：`ComposeBottomTabBar` 的拖动过程只更新 motion、候选 Tab 与触感；仅当拖动成功结束且落点不同于当前 Tab 时调用一次导航回调。取消/折返释放到当前 Tab 时不导航；点按路径未改变，仍即时导航。此前真机已完成 5 次历史滑过收藏到设置，以及折返、取消和点按检查。当前调用链没有拖动中切换目标路由的入口，因此不会因途经收藏而请求收藏页组合/测量。现有通用 Perfetto FrameTimeline 不含可识别 Compose 页面组合的事件，不能声称 trace 直接计数证明组合次数为 0；此处以导航调用链为确定性证据。
- **阶段二不通过性能收益判断，暂不扩大**：暖机且折叠状态的点按样本中，原行动画挂载方式的 `controlled-entry3` 五次收藏进入首帧“点击到 SurfaceFlinger 呈现结束”中位数 55.62 ms、最大 65.42 ms；条件挂载 `animateItem` 的 `controlled-entry6` 中位数 61.80 ms、最大 65.97 ms。两轮操作间隔不同，不能据此断言回归，但未显示改善。条件挂载试验已撤回，只保留原来按动画 spec 控制的 `animateItem` 行为。展开状态样本 `controlled-entry5` 不与折叠样本混比。
- 首帧细项显示上述十次进入的应用首响应帧都标为 `App Deadline Missed`，对应的 SurfaceFlinger 显示帧标为 `On-time Present`；这说明应用 FrameTimeline jank 标签不能代替显示帧呈现状态。点击至 SurfaceFlinger 呈现的中位/最慢值是目前可比的用户可见延迟指标，但需在相同采样节奏下复测后才能确认优化效果。
- 另一次条件挂载滚动 trace 的 `process.name` 为空，是采集配置未把进程元数据关联进 trace；按 `layer_name` 过滤 Debug 应用 Surface 可成功查询，已在现有 trace 上验证修订查询。该 trace 覆盖整段采集且未可靠截取活跃滚动窗口，不能作为滚动交互验收数据；不再用进程名空结果或整段闲置帧比例下结论，也不重录 trace 直到现有查询/窗口定义问题解决。
- **当前推进位置**：阶段一逻辑与真机交互检查完成；阶段二的列表滚动仍按先前有效 trace 记录为 535/638（83.9%）按时呈现、1 帧 App Deadline Missed，未达到 95%/0 门槛。首帧试验未证明条件动画挂载有收益。下一轮只针对可重复的实际进入/滚动窗口建立可比证据，再决定是否实施阶段二代码改动；不改刷新率策略，不常驻整个收藏页，不修改 Beta/正式版。
- 工程 Goal 服务当前只提供新建 Goal、读取 Goal 和更新状态的接口，没有修改现存活动 Goal objective 的接口。本设计文档是本轮可更新的推进目标正文；不伪报已改写 Goal 服务中的 objective。

### 当前活动 Goal：120 Hz 复测与常驻组合评估（2026-11-03 深夜）

- 当前安装包身份核对：APK 包名 `com.luckyalanzhou.barcodegenerator.debug`，清单含 `profileable android:shell="true"`；设备上 `run-as` 返回 `package not debuggable`，系统 `peak_refresh_rate=120`。未操作 Beta/正式版。收藏行几何仍只写入 `FavoriteRowCoordinates` 普通对象，Shell 级 `FavoritesRowsProjectionCache` 保留。
- 普通销毁/重建版本首帧对照：`controlled-entry3` 五次进入收藏的点击至 SurfaceFlinger 呈现中位 55.62 ms、最慢 65.42 ms；`controlled-entry6` 五次进入中位 61.80 ms、最慢 65.97 ms。两个基线节奏不一致，作为现象证据而非严格 A/B。
- 临时“单份收藏组合跨主 Tab 常驻”版本：完整 trace 匹配 10 次应用 ACTION_UP。五次进入收藏的点击至对应 SurfaceFlinger 呈现分别为 47.69、36.91、32.48、32.13、33.33 ms，中位 33.33 ms、最慢 47.69 ms；五个对应显示帧均为 SurfaceFlinger `On-time Present`，尽管应用首响应帧标了 `App Deadline Missed`。相较基线显示出更快首帧，但操作节奏/帧环境未完全一致，需谨慎看待。
- 初步内存对照：同一手机冷启动后约 8 秒，常驻原型 PSS 165.16 MB、普通版本 163.57 MB，差约 1.60 MB；导航若干次并经过 GC 后两者约 147 MB。单点 PSS 受 GC、加载时序影响，不能精确量化长期峰值，但常驻新增内存在低个位数 MB 范围。
- **滚动结果否决当前常驻原型**：当前临时版 8 段实际触摸滚动区间共有 775 个应用帧，其中 178 个 `On-time Present`、580 个 `Buffer Stuffing / Late Present`、17 个 `App Deadline Missed / Late Present`；每段慢滚大约 1 秒，另有两段约 0.3 秒 fling。有效基线 trace 的 6 段约 0.5 秒手势共 360 帧，304 个按时呈现（84.4%）、55 个 Buffer Stuffing、1 个 Deadline Missed；其中后 5 段全部按时呈现。两组滑动次数/节奏不同，不能当作严格配对回归，但方向与此前整页常驻测试（Buffer Stuffing 80→164）一致，当前方案的滚动风险高于首帧收益。
- 因此已撤销实验性常驻组合代码，恢复主 Tab 单页直接替换；保留行坐标非 Snapshot 更新、行投影缓存和底栏松手提交导航。临时包仅安装在 Debug 包名，已重建并覆盖安装无常驻实验的 Debug APK；最终核对 `run-as` 仍提示包不可调试、刷新率为 120 Hz，并在最后一次真机验证后锁屏（`mWakefulness=Dozing`）。
- 剩余目标是降低普通版本真实收藏滚动时的晚呈现/Buffer Stuffing；现有有效基线交互区间按时呈现率 84.4%，低于 95% 目标。Perfetto 显示 `measureAndLayout` 平均约 0.94 ms、最大 3.02 ms，`Recomposer:recompose` 最大约 0.048 ms；因此坐标状态写回不是当前主要滚动耗时来源。Buffer Stuffing 按官方 FrameTimeline 定义表示应用在前帧呈现前继续提交新帧、帧队列积压，晚呈现会增加触摸到画面的延迟。后续需继续定位渲染/显示队列层，不以扩大 Compose 缓存作为默认方案。

### 复核旧条件动画 trace（2026-11-04）

- 对此前 `animateItem` 条件挂载实验的两份 trace 改用应用层名和 `generator.debug` 线程事件复算，解决其 `process.name` 为空导致的旧查询空结果。两份 trace 都包含 7 段约 1 秒慢滑和 2 段 fling 的有效输入/应用帧窗口：`conditional-scroll` 为 896 帧，158 按时、721 Buffer Stuffing、17 Deadline Missed；`conditional-scroll-direct` 为 892 帧，184 按时、695 Buffer Stuffing、15 Deadline Missed。两组均有 18–21 个负时间戳丢弃，且与普通版基线不是严格同动作节奏；不能作为精确 A/B，但没有证据支持条件挂载能改善滚动，因此保持已恢复的动画修饰符。
- 深查普通版滚动基线的首段 Buffer Stuffing：App 实际帧约每 8.3 ms 到达，App 帧持续约 8–10 ms；对应 SurfaceFlinger 帧在目标显示时间内呈现，但从 App 帧开始到显示帧结束约 29–31 ms。主线程测量/重组较轻，下一步应验证页面整层 GlassBackdrop GraphicsLayer 是否在每次 LazyColumn 滚动绘制时重新录制整页；此处仍是待验证假设，未修改实现。当前设备在再次复测前处于锁屏，需要先由用户完成设备解锁。

### 目标范围扩展（2026-11-04）

- 用户将性能目标从收藏页扩展到应用所有页面。本文件开头的六页范围和逐页验收规则是当前目标；上文“收藏页专项阶段”及旧活动 Goal 文本是历史记录，不能限制当前范围。
- 当前六个路由为 `Generate`、`History`、`Favorites`、`Settings`、`Results`、`LanShare`。共享页面外壳、路由切换和底栏交互也纳入调查。先前证据主要覆盖收藏滚动/切入与部分底栏导航，其余页面均须独立采集或保持“未采集”。
- 真机测试部分按用户指示暂停。暂停期间只做源代码和已有 trace 的审查/分析及本地可行工作，不连接或操作手机。后续是否有性能收益以及逐页设备验收，均待恢复真机测试后判断。
- Goal 服务中的活动 objective 仍是此前的收藏页文字；该接口仅提供读取与状态更新，无法修改现有 objective。此处记录的是用户已明确扩展的实际任务范围，不声称 Goal 服务里的 objective 已更新。

#### 源码审查线索（手机测试暂停期间）

| 线索 | 代码证据 | 适用页面 | 当前判断与下一步 |
| --- | --- | --- | --- |
| 共享玻璃背景绘制成本 | `GlassBackdrop.kt` 的 `recordGlassBackdrop` 在 `drawWithContent` 中每次绘制都执行 `source.layer.record { drawContent() }`；`ComposeAppShell.kt` 将其应用于 `chromeVisible && !opaqueGlass` 的主体列 | 生成、历史、收藏、设置（依 `chromeVisible` 和视觉效果策略启用） | 收藏有效滚动 trace 的主线程测量/重组较轻，而 RenderThread/GPU 等待占用明显，构成跨页验证优先级最高的假设。尚无其他页面 trace，也未证明整页录制是根因；恢复手机测试后需先比较滚动前景变更与静态页面滚动的录制/呈现成本，再决定实现。 |
| 上传进度导致时间线投影重建 | `LanShareViewModel` 按进度百分比更新 `uploadingFiles`；`LanShareContent` 以 `files`、`messages`、`uploadingFiles` 为键重建并排序整条时间线 | 局域网分享，尤其大量历史消息且上传进行时 | 源码确认一次百分比变化会让列表投影失效，但实际消息规模、更新频率与耗时未测；仅列为待采样候选，不作为已确认卡顿或立即优化依据。 |
| 批量准备结果图片 | `ResultsContent` 通过 `prepareResultRows` 准备整批图片，完成后才创建结果列表 | 结果 | 当前行为与用户选择的“整批就绪后显示”一致。已有代码表明批次规模可能影响等待时间，但没有逐批数据或结果页 trace；不通过改变显示策略来规避，恢复设备测试后按实际批次测首项/整批完成耗时。 |

以上是对当前实现及已有收藏 trace 的静态归纳，不是新发现的已证实 Bug，也没有据此修改应用代码。

#### 当前工作区复核（2026-11-04）

- 当前工作区仍保留既有未提交改动；本轮只复核，没有覆盖或回退它们。收藏文件夹行与收藏分组行的 `onGloballyPositioned` 回调均写入普通 `FavoriteRowCoordinates` 对象；`LayoutCoordinates` 只在打开长按菜单时读取并转换为屏幕矩形，布局回调未写入 Compose Snapshot 状态。此项源码要求当前满足。
- `ComposeAppShell` 通过 `remember` 持有 `FavoritesRowsProjectionCache`，主 Tab 内容离开/重建不会丢失该缓存。缓存命中条件为 items/groups/folders 列表引用、查询词和折叠目录集合全部匹配；当前仍保留该缓存。
- 上述核对只能证明现有代码结构满足目标设计，不能证明当前工作区构建包的真机行为或当前设备安装身份。真机验证仍按用户要求暂停；恢复后需重新检查包身份、Profileable/Debuggable 属性与 120 Hz 状态，再测收藏切入及滚动。
- 本轮使用专用 `.local-validation/gradle-user-home` 和本机 `D:\Android\Sdk` 运行 `assembleBetaDebug --no-configuration-cache -PenableAppUnitTests=true`，构建成功（Gradle 报告 127 个任务中 2 个执行、5 个缓存、120 个最新）。没有运行测试或安装 APK。对生成的 `BarcodeGenerator-Debug.apk` 执行 AAPT/ApkAnalyzer 检查，包名为 `com.luckyalanzhou.barcodegenerator.debug`，含 `profileable android:shell="true"` 且 `debuggable=false`；这确认候选包配置，不证明手机安装状态或 120 Hz 实测。

#### 既有收藏滚动 trace 独立复算（2026-11-04）

- 用缓存 Perfetto Trace Processor v58.3 重跑 `.local-validation/device-validation-2026-11-03/barcode-favorites-plan-scroll1.perfetto-trace`。包名匹配的只有一个应用主 Activity layer；638 个帧 slice 对应 638 个不同 `surface_frame_token`，所以本 trace 的原按时/迟呈现计数没有被同 token 多 Surface 重复放大。其他 trace 仍应逐份检查，因为 [Perfetto FrameTimeline](https://perfetto.dev/docs/data-sources/frametimeline) 说明同一帧 token 可能对应应用的多个 Surface。
- 六段按 `generator.debug` ACTION_DOWN/ACTION_UP 截取的实际输入窗口合计 360 帧：304 个 On-time Present、55 个 Buffer Stuffing/Late Present、1 个 App Deadline Missed/Late Present。第一段为 1/55/1，其余五段 303 帧均按时呈现。SurfaceFlinger 对应的 360 个 display frame token 均为 On-time Present；首段应用帧开始到对应显示帧结束约 29–31 ms。
- 首段 `Choreographer#doFrame` 平均 2.932 ms、最大 9.225 ms；GPU completion wait 平均 4.632 ms、最大 7.416 ms；RenderThread DrawFrame 平均 2.993 ms、最大 5.389 ms。其余五段 `doFrame` 平均 2.091–2.722 ms，GPU wait 平均 4.275–4.689 ms，RenderThread DrawFrame 平均 3.047–4.478 ms。数据显示渲染/GPU/帧队列是值得继续检查的分支，不证明 `GlassBackdrop` 或 GPU 是根因。
- Trace Processor 发现 16 个 `trace_sorter_negative_timestamp_dropped`，FrameTimeline parser errors 和 unpaired-end errors 均为 0。内置 v2 `android_cpu`/`android_mem`/`android_gpu`/`android_lmk` 指标在此缓存二进制中返回 “No valid SQL to run”；系统热状态/限频等数据尚不能从这次复算确认。当前操作窗口仍是历史真机数据，不是本轮设备测试。
- 对首段 500.146 ms 症状窗口进一步查线程状态与帧依赖：唯一 App Deadline Missed 帧 token `85092625` 的预计预算 11.667 ms、实际 15.550 ms（晚 5.388 ms）；其 `Choreographer#doFrame` 为 9.225 ms，其中一次 `ACTION_MOVE` 分发 6.004 ms（`deliverInputEvent` 5.149 ms、`AndroidOwner:onTouch` 1.826 ms）。RenderThread 对应 `DrawFrames` 3.692 ms；GPU fence 70 等待 3.271 ms，在 App 帧结束前约 74 μs 结束；GPU-completion waiter 被 `kworker/u17:3` 唤醒。配对 SurfaceFlinger 帧仍按时呈现。这条依赖链只定位到局部慢帧，不能解释同一段 55 个 Buffer Stuffing 帧或确认其根因。
- 首段与下一段健康对照（均约 500 ms）的主线程 Running/Runnable/R+ 分别为 164.138/9.593/14.406 ms 与 163.386/8.017/15.731 ms，未显示明显增加的主线程 CPU 饥饿。观测到的 CPU 频率在首段不低于对照，但覆盖不完整且无热数据，不能排除限频；trace 未启用 GPU 频率、GPU slice、LMK/thermal 数据源，缺项不能解释为系统无此类问题。`ftrace_setup_errors=36`；CPU ftrace dropped-event 及 traced-buffer dropped-sequence 计数为 0。
- 根因状态维持 **未定位**：目前是 ACTION_MOVE、主线程帧处理、RenderThread 与 GPU fence 完成的局部相关链；共享 GlassBackdrop 和 GPU 饱和仍是未验证分支。已在本机忽略目录新增通用配置 `profileable-debug-20s-gpu.pftrace-config`：保留原输入、调度、CPU 频率、idle 和 FrameTimeline，并请求 `power/gpu_frequency` 与每 250 ms CPU 频率采样。`capture-profileable-debug-trace.ps1` 接受场景标签，可覆盖六个路由页或共享导航交互；脚本生成唯一的远端/本地 trace 名称，核对指定 Debug 包并在采集后自动拉回专用目录。配置和脚本尚未在目标设备验证；脚本只做 PowerShell 静态语法解析，未执行 ADB。真机恢复后先检查各源是否成功启用及 trace health，再按页面及操作记录有效窗口。设备不支持的源要明确记为缺失，不能将缺失解读为没有 GPU/热限制。

#### 坐标改动前后旧 trace 的可比性复核（2026-11-04）

- `barcode-favorites-after-coordinate.perfetto-trace` 虽有 405 个应用层帧和 16 个输入区间，但复用滚动窗口 SQL 后，只有 2 个区间各匹配 1 帧，其余区间均为 0；trace 有 6 个负时间戳丢弃。它无法把大多数 ACTION_DOWN/UP 与应用帧配对，因此不能作为坐标改动前后对照，也不能解释为滚动期间没有帧。
- 更早的 `scroll-after-split` 同样在 12 个输入区间中未匹配到应用帧，不能用于帧率结论。`scroll-after-split-2` 可匹配 12 个窗口共 720 帧（272 On-time、437 Buffer Stuffing、11 Deadline Missed），但操作与收藏页面代码阶段不是受控配对；其 40 个负时间戳丢弃也降低可信度。保留为探索性症状数据，不用作修复收益的 A/B 证据。
- 结论：当前证据能确认坐标回调不再写 Compose Snapshot 状态，但没有一组干净、可比的“改动前/后滚动 trace”证明这一改动改善了呈现帧。真机测试恢复后，需在同一构建/数据/手势协议下重新建立有效基线与目标版本记录。

#### 全页面采集入口（2026-11-04）

- `NavigationRoute` 的页面为 Generate、History、Favorites、Settings、Results、LanShare；共享导航另记 `tab-navigation`。通用采集器可用 `generate-entry`、`generate-input`、`history-entry`、`history-scroll`、`favorites-entry`、`favorites-scroll`、`settings-entry`、`settings-change`、`results-entry`、`results-scroll`、`lanshare-entry`、`lanshare-activity`、`tab-navigation` 等标签分别保存 trace。
- 恢复真机测试后，在专用目录运行 `.\capture-profileable-debug-trace.ps1 -Scenario favorites-scroll`；每次运行采集 20 秒并自动拉回一份带场景名和毫秒时间戳的 trace。该脚本目前只完成语法解析，未运行 ADB，因此设备连接、GPU ftrace 事件支持、trace health 和实际 120 Hz 状态仍待真机验证。
- 新增 `.local-validation/device-validation-2026-11-03/profileable-debug-frame-health.sql`，可对任一新 trace 输出逐 Surface 的原始帧行/唯一 token 数、触摸窗口帧覆盖状态、按时/迟呈现分类及关键 trace health 计数。已对历史 `plan-scroll1` 和 `after-coordinate` 离线运行：前者六个窗口均有帧覆盖、638 帧 token 无重复；后者大多数窗口没有帧落在按压/拖动区间。短点按的空窗口需结合下文的抬手后首帧分析，不单独判为无效 trace。

#### 点按首帧离线分析（2026-11-04）

- 点按的 `ACTION_DOWN` 到 `ACTION_UP` 通常只有数毫秒，区间内没有完整呈现帧是正常现象，不能把 `NO_FRAME_DURING_INPUT` 当作 trace 无效。该状态只表示没有帧落在手指按住期间；滚动帧覆盖率仍按完整按压/拖动区间计算。
- 新增本机忽略文件 `.local-validation/device-validation-2026-11-03/profileable-debug-entry-latency.sql`：对每次 `ACTION_UP`，在 500 ms 内且下一次 `ACTION_DOWN` 之前寻找首个主 Activity 应用帧，并按 `display_frame_token` 配对 SurfaceFlinger 帧。它测量首帧时间代理，不保证该帧已包含预期内容变化；页面转场或更新内容是否真正可见仍需人工确认或后续加交互标记。
- 用历史 `barcode-favorites-controlled-entry3-20s.perfetto-trace` 离线验证：全 trace 的主 Activity layer 为 691 行、691 个唯一 Surface token；11 次点按均在抬手后 2.359–8.854 ms 出现首个应用帧，均能配对显示帧，首个显示帧结束为 36.649–65.422 ms。11 个应用帧均标记为 `Late Present` / `App Deadline Missed`，其中 10 个对应 SurfaceFlinger 帧为 `On-time Present`，1 个为 `Late Present`。该记录是收藏页历史真机 trace，不代表本轮设备测试，也不足以证明目标页面内容已在首帧可见。
- 用同一 trace 验证 `profileable-debug-frame-health.sql`：11 个短点按区间均为 `NO_FRAME_DURING_INPUT`，符合按压区间太短的特征；完整 trace 有 4 个负时间戳丢弃、36 个 ftrace setup errors，FrameTimeline parser/unpaired-end 和 traced-buffer data loss 计数为 0。时间戳异常及数据源缺失仍需在逐份 trace 判读中披露。
- 入口 SQL、通用帧健康 SQL 和通用采集脚本都只保存在 `.local-validation/` 并被根 `.gitignore` 忽略。SQL 在上述已有本地 trace 上成功运行，脚本只做 PowerShell 语法解析；没有执行 ADB、连接手机或采集新数据。六个页面的采样与真实首个可见内容确认仍待用户恢复手机测试后逐页完成。

#### 当前源码复核（2026-11-04，设备测试暂停期间）

- 收藏行布局回调当前只将 `LayoutCoordinates` 写入普通 `FavoriteRowCoordinates` 引用；坐标在长按打开菜单时才转换为屏幕矩形，行卸载时清空引用。对应实现见 `ComposeFavoriteFolderRow.kt`、`ComposeFavoriteGroupRow.kt` 与 `FavoriteRowCoordinates.kt`。这确认该路径没有把滚动位置更新写入 Compose Snapshot state；是否改善实际滚动帧仍需相同设备上的前后 trace 验证。
- `FavoritesRowsProjectionCache` 由 `ComposeAppShell` 在主 Tab 外持有，页面切换重建 Favorites 组合时仍可查最近投影；命中条件是 items/groups/folders 的引用身份、查询文本及折叠目录集合。它是单条最近项缓存，不扩大为整页常驻。
- `ComposePageRoute` 当前明确分发 Generate、History、Favorites、Results、Settings 和 LanShare 六页，采集计划按这些路由分别记录，并单独记录共享底栏导航。当前本地旧 trace 仍只足以复核收藏页；其余路由的性能状态保持“未采集”，本段代码审查不视为通过。
- 本段只依据当前源文件检查实现和调用关系，没有改应用代码、运行测试、连接设备或操作 ADB。坐标改动前后缺少一组可比的有效滚动 trace，收藏页 95% 按时呈现 / 0 个应用期限超时的设备验收仍未通过；其他页面尚无专属基线。设备测试恢复后先按页面建立有效基线，再据证据决定是否修改。

#### 历史收藏点按 trace 补充复算（2026-11-04）

- 用抬手后首帧 SQL 对历史 `barcode-favorites-controlled-entry4-20s`、`entry5-25s`、`entry6-25s` 离线复算，分别得到 11、10、10 次点按；31/31 次均在 `ACTION_UP` 后找到主 Activity 帧，并按 display token 配到 SurfaceFlinger。首个应用帧距抬手 2.466–13.939 ms；首个匹配显示帧结束距抬手 40.398–84.386 ms。
- 这 31 个首个应用帧全部标记 `Late Present` / `App Deadline Missed`，31 个配对显示帧均为 `On-time Present`。这是进一步检查“应用提交与屏幕呈现延迟差异”的线索；trace 没有帧像素内容标记，不能断言该帧已经显示了目标页面内容，也不能据此把问题归因到 Compose 行坐标或单个渲染组件。
- 三份 trace 各有 36 个 `ftrace_setup_errors`，负时间戳丢弃分别为 11、10、5；FrameTimeline parser errors、unpaired-end 及所查 traced-buffer data-loss 计数均为 0。每份 trace 的应用 layer 唯一 surface token 数与帧行数相等（660/660、617/617、626/626），但负时间戳与 ftrace setup 问题仍降低系统侧因果分析的完整性。
- 这三份记录是 2026-11-03 收藏页历史样本，不构成当前源码的前后对照或本轮 120 Hz 复测。结合已知版本/操作协议不足，暂不据它们改变当前实现；后续真机恢复时需保留相同场景，采集健康 trace 并核对真实页面内容首帧。

#### 本地 Debug 复测候选包身份复核（2026-11-04）

- 加入 Trace section 前的候选产物 SHA-256 为 `01A5731D74DA6B4CC2D9E52270A0E7746E32EDC4FF615B55EA23DE086202A4D7`。其余旧点按与滚动 trace 均早于该包，没有与当前源码对应的 Perfetto 记录。
- 加入 Trace section 后的当前候选产物为 `android/app/build/outputs/apk/beta/debug/BarcodeGenerator-Debug.apk`，大小 60,685,903 bytes，最后写入时间 2026-11-04 00:15:05（本地时间），SHA-256 `3C0326F48D59E24E9D385642209B954295A35AEDD875B5D00ED0FCF626F3469C`。对 APK 本身复核：application ID `com.luckyalanzhou.barcodegenerator.debug`、`debuggable=false`、清单含 `<profileable android:shell="true" />`，应用标签为 `Barcode Generator Debug` / `条码生成器 Debug`。通过 `apkanalyzer dex code` 检查已打包 `GlassBackdrop.recordContent`、`replayContent`、`sampleContent`、`shaderSetup` 标记及 Trace begin/end/isEnabled 调用。这些是离线包检查，不证明已安装或标记已出现在实际 trace。
- `assembleBetaDebug --no-configuration-cache -PenableAppUnitTests=true` 在 2026-11-04 成功（127 个 actionable tasks 中 13 个执行、114 个 up-to-date）。生成的 Beta Debug `BuildConfig.DEBUG=false`、`PERFETTO_DRAW_TRACE=true`；另运行 `:app:generateBetaReleaseBuildConfig` 并确认 Beta Release 的 `PERFETTO_DRAW_TRACE=false`。标记只有在 API 29+ 且 `Trace.isEnabled()` 时才发出，因此默认运行及 Release 不录制这些 section。当前配置已有 `gfx` atrace 类别和 Debug 包过滤，但新增 section 尚未在手机 trace 中验证可见性或自身开销。
- 设备测试仍暂停；本轮没有安装、启动或操作设备，也没有将旧 trace 误关联到新哈希。恢复后用新哈希确认安装包，再采集收藏页切入与滚动 trace。

#### 常驻与普通版本历史滚动窗口复算（2026-11-04）

- 用忽略目录中的 `scroll-active-intervals.sql` 重算普通版 `barcode-favorites-plan-scroll1.perfetto-trace` 与常驻实验版 `barcode-favorites-retained-scroll-25s.perfetto-trace`：前者 6 段触摸窗口分别覆盖 57/59/61/61/61/61 帧，总计 360 帧（304 On-time、55 Buffer Stuffing、1 Deadline Missed）；后者 8 段窗口覆盖 117/117/117/117/119/117/35/36 帧，总计 775 帧（178 On-time、580 Buffer Stuffing、17 Deadline Missed）。两份 trace 的每个窗口中帧行数与唯一 `surface_frame_token` 数相等。
- 该复算确认了先前记录的数量，但操作长度和节奏明显不同（普通版约 0.5 秒/段，常驻版前六段约 1 秒、末两段约 0.3 秒）。因此它支持撤回常驻原型、避免把整页保留当默认优化，却不证明常驻本身造成帧退化。两份记录也早于当前候选 APK，不能替代当前版本设备对照。

#### 共用玻璃背景的源码候选（2026-11-04）

- `ComposeAppShell` 在显示底栏且非不透明玻璃模式时，把整个页面 `Column`（标题与 `AppPageTransitionHost`）包进 `recordGlassBackdrop`。`GlassBackdrop.kt` 的 `drawWithContent` 每次该绘制节点重绘时都会执行 `source.layer.record { drawContent() }`，之后再将记录层绘出；收藏 `LazyColumn` 滚动使页面绘制失效时，这条路径可能让可见页面内容重新录制到共享 GraphicsLayer。
- 底栏的 `TabLiquidGlassScene` 同时显示 `GlassBackdropSurface`；该表面在 renderer/GPU/尺寸/source 条件满足时给 Canvas graphicsLayer 设置 RuntimeShader blur，再采样共享 source layer。收藏滚动 trace 的主线程测量/重组耗时较低，而 FrameTimeline 有大量 Buffer Stuffing/Late Present，因此共享页面录制与底栏 shader 是跨路由值得验证的 CPU/GPU 候选。
- **因果尚未建立**：旧 Perfetto trace 没有玻璃路径专属 slice，也没有足以证明 shader/GPU 饱和的 GPU slice/频率完整数据；app `draw-VRI` 总耗时不能区分具体玻璃调用。Debug glass 日志没有随旧 trace 保存，实际 trace 当时是否满足 shader eligibility 也未被记录。本段只把源码链路列为待验证假设，不支持直接修改、移除玻璃效果或归因坐标回调。当前 profileable Debug 包新增了 `GlassBackdrop.recordContent`、`replayContent`、`sampleContent`、`shaderSetup` 和 `nestedFallback` section，可供下一次受控 trace 分别汇总这些阶段的主线程调用耗时；section 标记不能直接度量 GPU 执行时间。
- 当前 Xiaomi Android 15 为 API 35；`VisualEffectsPolicy` 仅在 API 36+ 读取系统高对比文本并关闭 backdrop，因此不能用当前系统现成的高对比选项做玻璃开/关对照。设备测试恢复后，需先选用可重复的对照手段，并用相同页面数据、滚动节奏和屏幕刷新模式复采；同时确认 Debug glass eligibility 日志与 Perfetto 的 RenderThread/GPU/FrameTimeline 健康状态。当前仍遵守手机测试暂停，不新增实验开关或修改页面绘制。
- 新增忽略目录查询 `.local-validation/device-validation-2026-11-03/glass-backdrop-stage-summary.sql`：分别汇总每段 ACTION_DOWN/UP 按压期间与抬手后最多 500 ms 的应用帧分类、玻璃阶段调用数和 CPU slice 交叠/平均/最大耗时。已在旧 `plan-scroll1` trace 上成功执行；能复算原有触摸窗口帧数，且没有 `backdrop_stage` 行（该旧 trace 本就不含新增 marker）。这只验证 SQL 可用，不证明 marker 在设备 trace 中可见。

#### 真机测试恢复后暂停记录（2026-11-04）

- 使用 profileable、`debuggable=false` 的 Debug 包 `com.luckyalanzhou.barcodegenerator.debug`，设备为 Xiaomi 2201122C / Android 15。启动页、四个主 Tab 和收藏数据均可正常显示；收藏页现有 Optoma 文件夹临时展开用于 40 条内容滚动，没有编辑、删除或导入数据。Beta 与正式版未操作。
- 首份重录 `barcode-favorites-entry-confirmed-gpu-20261104-071704-113.perfetto-trace` 确认应用 FrameTimeline 与 Debug 触摸事件均进入 trace：应用窗口 82 帧（43 On-time、37 Buffer Stuffing、2 App Deadline Missed）；记录到 8 个 ACTION_UP 与 `GlassBackdrop.recordContent` 等阶段标记。该样本有 5 个负时间戳丢弃，按诊断样本保存，不作通过结论。
- 四主 Tab 切换重录 `barcode-all-pages-route-entry-synced-gpu-20261104-072018-908.perfetto-trace` 覆盖 Generate、History、Favorites、Settings 两轮切换，8 个 Debug ACTION_UP；主 Activity 观察到 480 帧行（325 On-time、114 Buffer Stuffing、8 App Deadline Missed，另有 Unknown Jank），并出现玻璃阶段标记。Trace Processor 报 4 个负时间戳丢弃。结果说明页面切换链路可采集，不等于四页达到性能验收。
- 对上述四 Tab trace 单独复算 ACTION_UP 后首个 MainActivity 帧：收藏点击（第 3、7 次抬手）分别在 4.754 ms、3.660 ms 后出现应用帧，但两帧均为 Late Present / App Deadline Missed；按 display frame token 配对后的显示帧结束分别为 43.560 ms（On-time Present）和 51.810 ms（Late Present）。首个应用帧可能仍是旧页面内容，故此结果不能证明收藏首批内容的可见延迟达标；trace 仍有 4 个负时间戳丢弃。
- 收藏滚动 GPU 配置 trace `barcode-favorites-scroll-resumed-gpu-20261104-072150-993.perfetto-trace` 记录五次慢滚和两次短滑。应用窗口 875 帧，289 On-time、487 Buffer Stuffing、18 App Deadline Missed；触摸窗口覆盖 607 帧，其中 172 On-time。负时间戳丢弃 16 个。该结果与较早基线差距大，不能直接归因为代码变化。
- 为排除 GPU 频率采样影响，用历史非 GPU 配置重录 `barcode-favorites-scroll-recheck-20261104-072427-894.perfetto-trace`，重复五次慢滚和两次短滑：应用窗口 988 帧，660 On-time、142 App Deadline Missed、无 Buffer Stuffing；7 段触摸窗口共 667 帧，其中 429 On-time、140 App Deadline Missed。负时间戳丢弃 4 个；FrameTimeline parser/unpaired-end、CPU dropped-events 和 traced buffer data-loss 计数为 0，ftrace setup errors 为 36。两种采集配置的分类差异仍待同配置重复，不能据此判定原因或通过 95% On-time / 0 deadline-miss 验收。
- 该非 GPU trace 的 MainActivity 实际帧时长按 jank 分组：660 个 On-time 帧平均 8.949 ms（最大 12.042 ms）；140 个 `App Deadline Missed, Unknown Jank` 平均 11.152 ms（最大 11.970 ms）。帧时长与 FrameTimeline 呈现分类相关，但这仍没有指出延迟来自应用绘制、GPU、调度还是显示合成。
- 修正等待 session 丢失后，`scroll-render-summary.sql` 对该 trace 成功完成，耗时约 98.9 秒；五段慢滚中每段约 119–121 次 `Choreographer#doFrame`，平均 2.565–2.742 ms、最大 3.454–4.507 ms；RenderThread `DrawFrame` 平均 3.048–4.679 ms、最大 4.356–6.197 ms；`GPU completion wait` 每段约 118 次，平均 4.199–5.334 ms、最大 5.316–6.968 ms。两段短滑各覆盖 35–36 帧，GPU wait 平均 3.410–4.494 ms、最大 4.138–5.316 ms。五段慢滚中 GPU completion wait 持续存在，和期限超时同时出现，是当前 trace 中更强的待验证同步等待候选；没有 RenderStage 仍不能断言 GPU 执行过慢，也不能直接把 wait 归因于玻璃 shader。
- 对该非 GPU trace 汇总应用线程长切片：主线程 `traversal` 988 次，平均 0.845 ms、最大 2.378 ms；`draw-VRI[MainActivity]` 平均 0.780 ms、最大 2.301 ms。RenderThread 全屏 `Drawing 0,0–1440,3200` 平均 2.849 ms、最大 5.490 ms。另有 `GPU completion / waitForever` 平均 4.763 ms、最大 8.346 ms，及 HWC release `waitForever` 平均 3.109 ms、最大 4.948 ms。该数据没有显示主线程布局/绘制存在长单次阻塞；GPU completion 等待只能作为同步等待线索，不能视为 GPU 执行时间或根因证明。
- 对 GPU 配置滚动 trace 检查 Track/Counter：没有 GPU RenderStage 时间线或 GPU 执行时长/利用率 counter；仅有 `GPU completion` 队列计数 1776 点（范围 0–2、均值 0.505）及 `PrevGpuFrameMissed` 894 点（全为 0）。因此现有采集无法证明 GPU 饱和；后续设备复测需在设备/驱动支持的前提下增加真正可关联的 GPU 时间线，或继续把玻璃绘制标为未证实候选。
- 已准备本机候选配置 `.local-validation/device-validation-2026-11-03/profileable-debug-20s-gpu-renderstages.pftrace-config`，以及查询 `.local-validation/device-validation-2026-11-03/gpu-renderstage-health.sql`。查询已在当前缺少 GPU 数据的滚动 trace 上运行，返回 0 个 RenderStage、无 GPU counter；这验证了查询可报告数据缺失，不证明手机支持该数据源。恢复测试后先用 `adb shell perfetto --query` 检查设备注册名，再决定是否把配置中的 `gpu.renderstages` 改为实际 producer 名。
- 将 GPU trace 的每个 MainActivity 帧与最近一个 `GPU completion` counter 值关联后，队列值 0 与 1 都同时包含按时/迟呈现帧；值 1 时有 197 个 On-time、403 个 Buffer Stuffing、91 个其他迟帧，deadline miss 在值 0 和 1 时都出现。该 counter 没有给出能区分超期帧的阈值，不能单独解释卡顿。
- 用户要求暂停手机测试后，已停止进一步设备交互。随后熄屏命令返回 `adb.exe: device 'a9744c67' not found`，未能确认屏幕已关闭；暂停期间不再尝试连接。上述 trace 均保留在本机忽略目录。恢复时先确认设备连接与熄屏状态，再用单一固定配置复测收藏切入、慢滚和短滑，并逐页扩展其余路由；当前目标仍未验收完成。

#### 既有 Perfetto 记录的玻璃阶段离线复算（2026-11-04）

- 为在不连接手机、不上传 trace 的条件下重跑查询，将官方 Perfetto v59.1 Windows `trace_processor_shell` 放入忽略目录 `.local-validation/device-validation-2026-11-03/bin/`；版本输出为 `v59.1-1e9c61e98`，SHA-256 为 `3ACC5ACA1C764541033A82BA3BB0A47524B5C3C0AA194EEA8D9A7B0CDB346BDC`。帧关联查询保存在同一忽略目录的 `glass-frame-correlation.sql`。
- 在 `barcode-all-pages-route-entry-synced-gpu-20261104-072018-908.perfetto-trace` 上确认 `GlassBackdrop.recordContent` 481 次（均值 0.320 ms、最大 3.651 ms）、`replayContent` 481 次（均值 0.007 ms、最大 0.017 ms）、`shaderSetup` 483 次（均值 0.067 ms、最大 0.468 ms）。按 `actual_frame_timeline_slice` 的 482 个唯一应用帧 token 分组后，325 帧 On-time；其余类别包括 7 个 App Deadline Missed、1 个 Display HAL / App Deadline Missed、113 个 Buffer Stuffing、1 个 Buffer Stuffing / Unknown Jank、34 个 Unknown Jank 和 1 个 Dropped Frame。
- 帧与玻璃 CPU 阶段时间交叠汇总中，`recordContent` 的平均交叠为 On-time 帧 0.347 ms、7 个 App Deadline Missed 帧 0.969 ms、单个 Display HAL / App Deadline Missed 帧 3.770 ms；`shaderSetup` 对应均值分别为 0.092、0.042、0.033 ms。它提供“录制阶段可能参与个别长帧”的线索，但单 trace、7 个普通超时样本且有 4 个负时间戳丢弃，不能建立因果关系；标记只包围 CPU 侧代码，不等于 GPU 执行耗时。
- 收藏滚动非 GPU trace `barcode-favorites-scroll-recheck-20261104-072427-894.perfetto-trace` 重跑阶段 SQL 后仍复现 667 个触摸窗口帧、429 On-time、140 个 App Deadline Missed，但没有任何 `GlassBackdrop.*` 阶段切片。因此滚动样本无法用阶段标记做相关性分析；此前 GPU completion wait 仍只是待验证同步等待候选，GPU 饱和与玻璃 shader 根因均未证实。
- 上述离线复算没有安装、启动或操作设备；也没有修改应用代码。它更新了阶段时间与帧类别的关联证据，但不足以触发玻璃绘制实现变更。收藏滚动的同配置复测与坐标优化前后对照仍需等手机测试恢复后完成。
