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
- `:app` 的 `ui` 包：Compose 页面、状态渲染、导航和一次性事件消费；通过 ViewModel/回调连接 presentation，不直接操作数据源。
- `:app` 的 DI 与平台桥接：组合各模块实现，并接入 Activity、权限、文件选择器等 Android 能力。

## 状态与事件

- 持续存在、需要在重组或配置变化后恢复的内容使用 `StateFlow`。
- Toast、Snackbar、导航、系统请求和下载完成等一次性行为使用 `SharedFlow` 或 `Channel`。
- Composable 不直接访问数据库、DataStore、文件或网络。

## 导航

- `AppRoute`、标题、Chrome 显示规则和页面解释属于 UI 层。
- ViewModel 只保存可恢复的页面键和返回目标，不依赖 UI 路由枚举；页面键到 UI 路由的解释由 Compose 完成。
- 返回行为由 Activity/Compose UI 协调，业务 ViewModel 不持有导航栈。
- 页面之间传递稳定 ID 或不可变参数，不传递 DAO、Context 或可变实体。

## 兼容性

旧版 `SharedPreferences` 迁移代码属于数据兼容职责，可以保留在 Data 层；它不是新的业务状态来源。
