# Android 架构约束

本项目使用 Compose + MVVM + StateFlow + Hilt + Repository + UseCase 的分层架构。

## 模块依赖

```text
:app ───────────────> :core:domain
  ├────────────────> :core:data ─────────> :core:domain
  └────────────────> :core:lan-share ────> :core:domain
```

允许的依赖方向：

| 模块 | 可以依赖 | 禁止依赖 |
| --- | --- | --- |
| `:core:domain` | Kotlin/JVM、Coroutines | Android、Compose、Room、DataStore、Activity、ZXing、`:app` |
| `:core:data` | `:core:domain`、Android SDK、Room、DataStore、文件和网络 API | Compose、`:app`、ViewModel |
| `:core:lan-share` | `:core:domain`、Android SDK、NanoHTTPD/WebSocket | Compose、`:app`、`:core:data`、ViewModel |
| `:app` | `:core:domain`、`:core:data`、`:core:lan-share`、Android SDK、Compose、Hilt | 让 Composable 直接访问 DAO、Repository 实现或文件系统 |

当前共有以上四个 Gradle 模块。Compose UI 与 presentation 代码位于 `:app` 模块的不同包中；`ui` 是代码层次，不是独立的 `:core:ui` 模块。`:core:lan-share` 是 Android Library，会随 `:app` 一起打包进 APK，不需要单独安装。

Android 插件与库版本由 Gradle Version Catalog 集中管理，目录为 `android/gradle/libs.versions.toml`。

## 层职责

- `:core:domain`：领域模型、Repository/平台能力接口和不依赖 Android 的业务规则。
- `:core:data`：Room、DataStore、文件数据源及 Repository 实现；负责 Entity/Domain Mapper 和持久化迁移。
- `:core:lan-share`：局域网 HTTP 文件服务、WebSocket 会话消息、文件协议和内嵌浏览器页面；依赖 `:core:domain` 的 `LanShareGateway` 契约。
- `:app` 的 `presentation` 包：ViewModel、页面状态和应用级业务协调。
- `:app` 的 `ui` 包：Compose 页面、状态渲染、页面切换和一次性事件消费；通过 ViewModel/回调连接 presentation，不直接操作数据源。
- `:app` 的 DI 与平台桥接：组合各模块实现，并接入 Activity、权限、文件选择器等 Android 能力。当前 Hilt 装配集中在单个 `AppModule`；只有在 Core 模块需要独立复用或装配规模明显增长时，再考虑拆分模块内的 DI。

## 状态与事件

- 页面运行期间、重组或配置变化后需要继续观察的内容使用 `StateFlow`；`StateFlow` 本身不保证进程重建恢复。
- Toast、Snackbar、导航、系统请求和下载完成等一次性行为使用 `SharedFlow` 或 `Channel`。
- Composable 不直接访问数据库、DataStore、文件或网络。
- 结果页使用 `SavedStateHandle` 保存条码 ID、返回来源和收藏分组 ID，进程重建后从 Repository 重载内容；外部相机/文件请求保存请求码及必要的输出路径。不要把位图或整份条码列表放入保存状态。

## 局域网文件传输

- Android App 是分享房间创建端；同一局域网的加入端使用浏览器访问，不要求安装 App。
- 文字消息通过 `/ws` WebSocket 双向广播，并在当前服务进程内暂存；App 与浏览器两端的消息都会进入最多 100 条的会话历史，服务停止后不保留聊天记录。
- 文件内容通过 HTTP `PUT /upload` 原字节上传，文件名和发送端信息作为请求参数传递；原文件由 `GET /dl/<id>` 下载，图片预览走独立的 `GET /api/preview/<id>`，预览处理不改变下载原文件。
- 文件暂存在 App 私有目录 `filesDir/lan-share`，不写入公共 Documents。创建新分享房间或显式结束房间时清理；不将该目录作为长期文件库。
- 浏览器 WebSocket 断开后每 1 秒尝试重连。重连后浏览器请求当前会话快照，恢复文件列表和消息历史；浏览器不通过定时轮询更新列表。
- 服务端将连接、文件和消息变化折叠为最新会话状态，通过 `LanShareGateway` 暴露给 App；App ViewModel 以 `StateFlow` 更新界面。慢速观察者可以跳过中间状态，但会收到最新状态，避免无限事件队列积压。
- `:core:lan-share` 隔离 HTTP/WebSocket 实现、浏览器页面和传输缓冲细节；presentation/UI 通过领域层契约访问能力，仅 App 的 DI 装配层了解具体实现。传输协议、服务生命周期与 Compose UI 保持解耦。

## 导航

- 当前实现的 `NavigationRoute` 定义在 `presentation/navigation`，包含页面名、标题、Chrome 显示规则与主标签位置；`ui/app/AppRoute` 是它的类型别名。
- `AppNavigationViewModel` 的 `StateFlow` 持有当前 `NavigationRoute`，Activity 在 `onSaveInstanceState` 保存页面名和设置页返回目标，并在重建时恢复。Compose 根据路由渲染页面，没有 `NavController` 导航栈。
- 返回行为由 Activity/Compose UI 协调，业务 ViewModel 不持有导航栈。
- 页面之间传递稳定 ID 或不可变参数，不传递 DAO、Context 或可变实体。

## 持久化与备份

- `BarcodePersistenceCoordinator` 是进程内单例，按顺序写入 Room；写入协程不依附于页面 ViewModel，页面销毁不应取消已入队任务。
- 页面数据先更新内存，再排队落库；失败时通过 `writeFailures` 通知 `LibraryDataViewModel` 重载持久化快照。进程被强制结束前尚未提交的写入仍有丢失风险，不能把入队等同于事务提交成功。
- 用户条码数据允许备份；`lan-share/` 临时附件及 Beta 诊断日志在旧版备份规则和 Android 12+ 数据提取规则中均被排除。

## 构建验证

- `:core:domain`、`:core:data`、`:core:lan-share` 和 `:app` 的自动化测试目前是 JVM 单元测试，位于 `src/test`。其中 `:core:lan-share` 会启动本地 HTTP 服务并用客户端验证定长/分块上传、原始字节和 `/dl/<id>` 下载；WebSocket 覆盖握手及会话状态，但不等同于真实设备上的完整双端测试。
- 当前没有维护 Android `src/androidTest` 仪器化测试；局域网地址发现、Wi-Fi 通信、应用生命周期等设备行为由真机手动验证。
- 发布前在本地运行相关单元测试及目标变体的 Release lint。`beta` 与 `main` 的 GitHub 手动发布工作流只负责构建、签名和发布 APK，不在远端重复运行测试或 lint。

## 兼容性

旧版 `SharedPreferences` 迁移代码属于数据兼容职责，可以保留在 Data 层；它不是新的业务状态来源。
