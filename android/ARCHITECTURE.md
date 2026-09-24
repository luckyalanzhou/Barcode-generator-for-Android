# Android 架构约束

本项目使用 Compose + MVVM + StateFlow + Hilt + Repository + UseCase 的分层架构。

## 模块依赖

```text
:app ───────────────> :core:domain
  └────────────────> :core:data ─────────> :core:domain
```

允许的依赖方向：

| 模块 | 可以依赖 | 禁止依赖 |
| --- | --- | --- |
| `:core:domain` | Kotlin/JVM、Coroutines、ZXing 等纯 Kotlin 库 | Android、Compose、Room、DataStore、Activity、`:app` |
| `:core:data` | `:core:domain`、Android SDK、Room、DataStore、文件和网络 API | Compose、`:app`、ViewModel |
| `:app` | `:core:domain`、`:core:data`、Android SDK、Compose、Hilt | 让 Composable 直接访问 DAO、Repository 实现或文件系统 |

当前只有以上三个 Gradle 模块。Compose UI 与 presentation 代码位于 `:app` 模块的不同包中；`ui` 是代码层次，不是独立的 `:core:ui` 模块。

## 层职责

- `:core:domain`：领域模型、Repository/平台能力接口和不依赖 Android 的业务规则。
- `:core:data`：Room、DataStore、文件、网络数据源及领域接口实现；负责 Entity/Domain Mapper 和持久化迁移。
- `:app` 的 `presentation` 包：ViewModel、页面状态和应用级业务协调。
- `:app` 的 `ui` 包：Compose 页面、状态渲染、页面切换和一次性事件消费；通过 ViewModel/回调连接 presentation，不直接操作数据源。
- `:app` 的 DI 与平台桥接：组合各模块实现，并接入 Activity、权限、文件选择器等 Android 能力。

## 状态与事件

- 页面运行期间、重组或配置变化后需要继续观察的内容使用 `StateFlow`；`StateFlow` 本身不保证进程重建恢复。
- Toast、Snackbar、导航、系统请求和下载完成等一次性行为使用 `SharedFlow` 或 `Channel`。
- Composable 不直接访问数据库、DataStore、文件或网络。
- 结果页使用 `SavedStateHandle` 保存条码 ID、返回来源和收藏分组 ID，进程重建后从 Repository 重载内容；外部相机/文件请求保存请求码及必要的输出路径。不要把位图或整份条码列表放入保存状态。

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

- `beta` 与 `main` 的 APK 发布工作流均为手动触发。签名前先运行领域、数据、应用层单元测试和对应 Release lint。
- 数据库迁移及收藏关联的设备仪器化测试位于 `:core:data`；云端手动构建门禁目前不运行设备测试，发布前仍需真机验证启动、进程重建及外部 Activity 回调。

## 兼容性

旧版 `SharedPreferences` 迁移代码属于数据兼容职责，可以保留在 Data 层；它不是新的业务状态来源。
