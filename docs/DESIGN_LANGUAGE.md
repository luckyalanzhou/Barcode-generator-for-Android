# 条码生成器设计语言

本文件统一应用内界面元素的名称、用途、视觉层级与交互表达，供讨论、设计、开发和验收使用。

第一版依据当前 Android Compose 实现整理。标注“现状”的内容描述当前代码；“约定”用于后续设计与验收，不代表已有界面全部完成审计。本次只整理文档，不改变页面、导航或交互，不将本文写入本机 AGENTS.md。

## 1. 核心表达

应用以内容为主体：条码、输入内容、收藏和传输消息需要清晰易读。玻璃用于导航、浮层和少量功能控件，表示它们位于内容之上。

通过三种信息区分控件：

- **用途**：切换页面、选择设置、执行操作、确认决定或预览内容。
- **形态**：底栏、按钮、下拉菜单、上下文菜单、对话框、底部面板或全屏预览。
- **状态**：选中、按压、拖动、禁用、加载、成功、失败。

“玻璃”是材质，“胶囊”“圆形”是形状，“菜单”是交互类型。不能仅因为控件外观相近，就将它们视为同一种组件。

## 2. 组件名称与应用内实例

| 统一名称 | 用途 | 当前实例 | 关键区别 |
| --- | --- | --- | --- |
| 底部导航栏 Bottom navigation | 切换主要页面 | 生成、历史、收藏、设置 | 改变当前页面，不执行页面工具动作 |
| 导航选中胶囊 Selection indicator | 标记当前或手势候选 Tab | 底栏内部移动的玻璃胶囊 | 是导航栏的一部分，不是额外按钮 |
| 上下文操作菜单 Context menu | 对当前对象或区域执行操作 | 长按 Tab；长按收藏文件或文件夹 | 依赖长按的对象，菜单内容随对象变化 |
| 单选下拉菜单 Single-choice dropdown | 从多个值中选择一个 | 设置页“外观”；生成页“条码类型” | 有当前值，选项之间互斥 |
| 多选下拉菜单 Multi-select dropdown | 独立启用多个选项 | 设置页“OCR 字符纠错” | 多个选项可同时选中 |
| 动作下拉菜单 Action dropdown | 从入口选择一个动作 | 文件传输页加号：拍摄图片、照片图库、选择文件 | 每项都是动作，没有“当前选中值” |
| 对话框 Dialog | 完成聚焦的输入、确认或告知 | 批量粘贴、输入设置数值、更新提示 | 暂停当前页面操作；需要明确的退出或完成方式 |
| 底部面板 Modal bottom sheet | 展示一个独立的辅助任务 | 局域网连接二维码 | 从屏幕底部出现，内容比短菜单更丰富 |
| 圆形功能按钮 Circular action button | 对当前结果执行工具操作 | 结果页四个圆形按钮 | 不属于导航栏，不显示导航选中胶囊 |
| 普通操作按钮 Action button | 触发清晰命名的操作 | 添加一行、启动、分享、检查更新 | 不因有圆角或边框而称为菜单 |
| 图标按钮 Icon button | 用简洁图标执行单个动作 | 添加附件、清空搜索、关闭预览 | 需要明确的无障碍名称和足够的触控区域 |
| 输入框 Text field | 输入或编辑内容 | 条码输入行、传输消息输入框 | 输入状态与按钮的选中状态不同 |
| 搜索框 Search field | 缩小当前内容范围 | 收藏搜索 | 包含查询、清空和结果状态 |
| 内容行 / 内容卡片 Content row / card | 呈现业务内容 | 历史批次、收藏文件、设置分组 | 内容本身不默认使用玻璃 |
| 消息气泡 Message bubble | 区分发送方与内容类型 | 传输页文字、文件、图片 | 图片预览和保存文件是不同动作 |
| 全屏预览 Full-screen preview | 查看内容细节 | 文件传输图片预览 | 缩放、平移和关闭，不等同于下载 |
| 状态反馈 Status feedback | 表达执行过程和结果 | 上传进度、下载中、空列表、错误提示 | 状态反馈不是菜单或导航 |

## 3. 菜单的交互语言

### 单选下拉菜单

现状：外观入口显示“跟随系统 / 浅色 / 深色”之一，选中后保存并关闭菜单。条码类型也是单选设置。

约定：

- 入口展示当前值；外观、条码格式和字符纠错使用原始 `unfold_more` 上下双箭头提示可以展开。
- 当前选项有明确选中标记；不能仅靠颜色表达选中。
- 点击选项提交选择；点击外部或返回只关闭菜单。
- 菜单围绕入口定位，避免超出窗口或被键盘遮住。

### 多选下拉菜单

现状：OCR 字符纠错可勾选多项；点击选项更新设置，菜单保持打开。入口按状态显示“关闭”、单项名称或“启用 N 项”。

约定：每项独立勾选，不将其表现为单选列表。点击外部或返回关闭菜单；不得在关闭时额外清空已选择项。

### 动作下拉菜单

现状：添加附件打开来源菜单；选择来源后先关闭菜单，再启动相机、图库或系统文件选择器。

约定：使用动作名称，不画单选标记。取消系统选择器不应产生发送动作。

### 上下文操作菜单

现状：长按 Tab 和收藏对象使用窗口级菜单宿主，可突出来源 Tab 或内容行，展示与来源相关的操作。

约定：

- 保留来源对象和菜单之间的视觉关系，菜单不能遮住用户需要辨认的关键内容。
- 操作名称描述结果，例如“重命名”“移动”“导出”，避免只写“确定”。
- 关闭菜单不执行操作；选择操作时避免菜单退场与下一弹窗互相抢焦点。
- 破坏性操作使用破坏性语义色；是否再次确认由数据损失风险决定，不统一套确认框。
- 提供可发现的替代入口或无障碍操作，避免关键功能只能通过长按完成。

## 4. 导航与动作的边界

底栏包含一层外部玻璃胶囊，以及一个独立的选中胶囊。未选中图标与文字维持普通导航项，不各自增加玻璃按钮。

点按、拖动和长按是不同手势：点按切页，拖动驱动导航指示器，长按打开上下文菜单。修改其中一种手势时，应分别回归另外两种，不能以点按测试替代拖动测试。

具体页面提交时机以 `ComposeBottomTabBar` 的现有实现为准；本文件不授权更改导航时机。结果页四个圆形按钮属于功能工具栏，保持独立圆形按钮，不套用底栏的选中胶囊。

## 5. 颜色与明暗主题

现状：原始强调蓝为 **`#0080F4`**，用于导航选中态、图标、滑轨等。当前玻璃底栏叠色后，真机选中内容的主要显示色为浅色 **`#0073DB`**、深色 **`#1A8DF5`**。它们是当前材质与背景下的结果，不能再次作为原始色叠加，也不代表所有蓝色控件都显示这两个值。

颜色按语义使用：

| 语义 | 使用原则 |
| --- | --- |
| 强调色 `controls.accent` | 强调选中状态和可操作入口 |
| 主操作填色 `controls.primaryAction` | 配合按钮文字，保持实色按钮可读性 |
| 链接色 `text.link` | 适用于所在背景上的文字链接 |
| 主文字 / 次文字 | 区分信息层级，不能用次文字色掩盖错误或禁用原因 |
| 破坏性颜色 / 删除图标色 | 表示删除等风险，不用于普通选中态 |
| 禁用色 | 说明当前不可操作，结合实际禁用交互与语义 |
| 内容表面色 | 区分页面、卡片、输入区域和浮层 |

约定：从 `LocalAppColorScheme` 取语义颜色，不在页面中散落原始色值。普通文字对比度以至少 4.5:1 为验收目标；透明材质需针对实际合成背景检查。白字按钮和文件气泡不直接套亮蓝色强调值。

浅色模式以浅色画布、白色内容面分层；深色模式以黑色画布和逐层提亮的内容面分层。主题切换不改变控件尺寸或触控范围。

## 6. 形状、尺寸与间距

以下是现有基础值，不要求用它们机械替换所有特殊组件：

| 项目 | 当前值 / 约定 | 实现依据 |
| --- | --- | --- |
| 页面水平留白 | 18dp 基础值 | `AppDimensions` |
| 内容卡片圆角 | 18dp 基础值 | `AppDimensions` |
| 普通按钮圆角 | 16dp 基础值 | `AppDimensions`；具体控件可覆盖 |
| 设置行高度 | 48dp 基础值 | `AppDimensions` |
| 设置分组间距 | 8dp 基础值 | `AppDimensions` |
| 底栏高度 | 72dp 基础值，允许为大字标签增长 | `AppDimensions` |
| 设置下拉菜单圆角 | 16dp | `SettingsDropdown` |
| 图标按钮触控目标 | 至少 48dp 为约定 | 图标视觉尺寸与触控尺寸分别设计 |
| 传输输入栏横向间距 | 加号到输入框、输入框到发送按钮均为 8dp | `LanShareInputBar` |

约定：尺寸使用 dp，文字使用 sp；适配以窗口可用空间、系统栏、键盘和字体缩放为依据，不按某台手机的像素分辨率固定坐标。长文件名和标签应换行或明确省略，不能直接截掉而没有提示。

标题、正文和辅助说明表达不同信息层级。具体字号由组件与已有主题决定；不能为了塞入文字而缩小触控目标。

## 7. 玻璃材质

现状：导航、菜单和圆形功能按钮等通过 Kyant Backdrop 采样背景，使用模糊、vibrancy、透镜折射、半透明底色、高光与阴影。不同组件的材质参数不必完全相同。

约定：

- 采样控件后方的正确背景；跨窗口浮层需处理窗口与屏幕坐标关系。
- 玻璃面板与文字、图标分层，优先确保前景清晰。
- 按压或拖动可增强折射、高光与阴影；空闲时不做无意义的持续动画。
- 层次通过适度高光、阴影和表面色共同表达，不用厚重外阴影替代层次。
- 不把条码图像、普通内容列表和整张页面做成玻璃。
- 遵守减少动态效果、高对比度与不透明材质策略；外观效果不能影响操作完成。
- 当前项目已有兼容路径；本文不扩大旧系统开发范围，也不因材质规范调整 minSdk。

## 8. 动效与状态

| 动效名称 | 表达目的 | 约定 |
| --- | --- | --- |
| 按压反馈 Press feedback | 确认触碰目标 | 短促、可中断；松手或取消后恢复 |
| 选中胶囊位移 Selection motion | 表达导航目标变化 | 跟手与落位连续，取消后状态一致 |
| 锚点展开 Anchor reveal | 表达下拉菜单来自哪个入口 | 从入口附近展开，退场回收，不改变选项语义 |
| 来源聚焦 Context focus | 表达上下文菜单针对哪个对象 | 聚焦来源，保留其可辨认信息 |
| 展开 / 折叠 Expand / collapse | 表达收藏树结构变化 | 由当次操作触发，避免返回页面时无意义重播 |
| 进度反馈 Progress | 表达操作正在执行 | 已知进度用确定进度；避免重复提交 |
| 预览缩放 Preview zoom | 查看图片局部 | 双击与双指协调，平移受图片边界限制 |

现有共用参数入口为 `ComposeAnimationConfig`；部分玻璃手势在专用状态类中定义。动效实现应复用对应入口，不能把文档中的示意时长视作所有组件的实测性能。

状态约定：加载、空内容、无搜索结果、执行失败与禁用必须区分。取消不等于失败；预览不等于保存；没有实际呈现帧数据，不能宣称达到某个帧率。

## 9. 页面组合约定

- **生成页**：输入行、录入辅助、单选格式菜单、生成操作，保持录入到生成的顺序。
- **历史页**：按生成批次呈现内容，行内编辑和删除属于对象操作，保持适当间距。
- **收藏页**：搜索框、文件夹层级、内容行与上下文菜单，保留搜索和普通列表各自的位置语义。
- **设置页**：按外观、条码样式、识别辅助、文件传输、诊断（按构建配置显示）、关于排列；分组标题位于卡片外。条码文字大小与尺寸归组，恢复操作只作用于本组四项参数。页面入口使用整行右箭头，设置值使用单选 / 多选下拉，立即执行的动作保留按钮；版本、作者与检查更新分别成行。
- **结果页**：条码内容与四个圆形功能按钮分层，按钮不承担主导航语义。
- **文件传输页**：标题和二维码入口固定，消息列表独立滚动，输入栏固定；图片缩略图打开预览，文件名或下载入口执行保存。

以上描述界面职责，不改变 Presentation、Domain 或 Data 的责任边界。

## 10. 代码索引

下列路径相对 `android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/`：

| 设计元素 | 当前实现入口 |
| --- | --- |
| 语义颜色 / 原始色 / 尺寸 | [theme/AppColorScheme.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/theme/AppColorScheme.kt)、[theme/ColorTokens.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/theme/ColorTokens.kt)、[theme/AppDimensions.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/theme/AppDimensions.kt) |
| 明暗主题 / 可访问性效果策略 | [theme/AppTheme.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/theme/AppTheme.kt)、[theme/VisualEffectsPolicy.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/theme/VisualEffectsPolicy.kt) |
| 底栏与选中玻璃胶囊 | [app/ComposeBottomTabBar.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/ComposeBottomTabBar.kt)、[app/TabLiquidGlassScene.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/TabLiquidGlassScene.kt) |
| 上下文菜单宿主 / 合同 | [app/TabLongPressActionDialog.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/TabLongPressActionDialog.kt)、[component/menu/LongPressMenuContract.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/component/menu/LongPressMenuContract.kt) |
| 下拉菜单定位与展开 | [dialogs/ComposeCommonDialogs.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/dialogs/ComposeCommonDialogs.kt)、[dialogs/CornerDropdownMenu.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/dialogs/CornerDropdownMenu.kt) |
| 菜单玻璃材质 / 选择项 | [component/menu/MenuSurface.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/component/menu/MenuSurface.kt)、[component/menu/MenuChoiceItem.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/component/menu/MenuChoiceItem.kt) |
| 外观与 OCR 菜单 | [feature/settings/SettingsContent.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/settings/SettingsContent.kt)、[feature/settings/ComposeSettingsComponents.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/settings/ComposeSettingsComponents.kt) |
| 圆形功能按钮 | [component/glass/GlassRoundActionButton.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/component/glass/GlassRoundActionButton.kt)、[feature/results/ResultsContent.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/results/ResultsContent.kt) |
| 传输输入栏 / 消息气泡 | [feature/lanshare/LanShareInputBar.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/lanshare/LanShareInputBar.kt)、[feature/lanshare/LanShareMessageBubble.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/lanshare/LanShareMessageBubble.kt) |
| 底部二维码面板 / 全屏图片预览 | [feature/lanshare/ComposeLanShareQrDialog.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/lanshare/ComposeLanShareQrDialog.kt)、[feature/lanshare/LanShareImagePreviewDialog.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/lanshare/LanShareImagePreviewDialog.kt) |
| 共用动效参数 | [animation/ComposeAnimationConfig.kt](../android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/animation/ComposeAnimationConfig.kt) |

## 11. 后续沟通与验收

可以这样描述改动：

- “调整**外观单选下拉菜单**的展开位置，保留选中后关闭的行为。”
- “调整**OCR 多选下拉菜单**的选中标记，保留连续多选。”
- “调整**Tab 上下文操作菜单**的来源聚焦和玻璃厚度。”
- “调整**结果页圆形功能按钮**的按压反馈，不改为导航按钮。”
- “调整**传输输入栏**的横向间距，固定标题与二维码入口。”

每次改动需明确组件、状态和操作场景。UI、动效与交互分别在深浅模式检查，并记录：普通与选中状态、按压 / 取消、展开 / 收起、返回关闭、字体放大、键盘与系统栏影响。检查范围按实际修改确定，不将本文作为每次都重测全应用的要求。

文档更新只说明规范变化，不代表对应实现已经修改或验收。实际偏差应先记录证据，再单独决定修复范围。
