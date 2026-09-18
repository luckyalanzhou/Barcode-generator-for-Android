# Android 架构约束

本项目使用 Compose + MVVM + StateFlow + Hilt + Repository + UseCase 的分层架构。

## 模块依赖

```text
core:domain  <-  core:data  <-  app
core:ui      <-  app
```

允许的依赖方向：

| 模块 | 可以依赖 | 禁止依赖 |
| --- | --- | --- |
| `core:domain` | Kotlin、ZXing 等纯 Kotlin 库 | Android、Compose、Room、DataStore、Activity、app |
| `core:data` | `core:domain`、Room、DataStore、文件 API | Compose、Activity、ViewModel |
| `core:ui` | Compose Runtime、Compose UI、动画库 | app ViewModel、Repository、Room、Activity |
| `app` | Domain、Data、UI、Android SDK、Hilt | 让 Composable 直接访问 DAO 或文件系统 |

## 层职责

- Domain：领域模型、Repository 接口和纯业务用例。
- Data：Room、DataStore、文件、网络数据源及 Repository 实现；负责 Entity/Domain Mapper。
- App：ViewModel、平台能力桥接、依赖注入和应用级协调。
- UI：Compose 页面、UI 状态渲染、导航和一次性事件消费。

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
