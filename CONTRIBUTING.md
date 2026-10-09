# 贡献与修改指南

[项目入口](README.md) · 当前：贡献指南 · [架构原则](android/ARCHITECTURE.md) · [开发与验证](android/DEVELOPMENT.md) · [通信协议](android/core/lan-share/PROTOCOL.md)

> 修改顺序：定位代码 → 确认职责层 → 验证改动 → 检查差异。

## 修改前先确认

在仓库根目录的 PowerShell 中检查分支和已有改动：

```powershell
$repo = 'D:\GitHub\Barcode-generator-for-Android'
git -c safe.directory=$repo -C $repo status --short --branch
```

日常开发使用 `beta`。发现不认识的修改时先停下确认，不要覆盖或删除。正式版只从 `main` 发布，不要把日常修改直接推到 `main`。

## 代码地图

先按功能搜索，再从页面一路追踪状态、业务规则和数据实现。下列链接指向仓库中的实际文件。

<details>
<summary><strong>应用、界面与页面状态</strong></summary>

- 应用入口：[BarcodeUi.kt](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/BarcodeUi.kt)、[ComposeAppShell.kt](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/ComposeAppShell.kt)。
- 生成：[GenerateContent.kt](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/generate/GenerateContent.kt)（界面）、[GenerateViewModel.kt](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/generate/GenerateViewModel.kt)（状态与操作）。
- 历史：[ComposeHistoryScreen.kt](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/history/ComposeHistoryScreen.kt)、[HistoryViewModel.kt](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/history/HistoryViewModel.kt)。
- 收藏：[FavoritesContent.kt](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/favorites/content/FavoritesContent.kt)、[FavoritesViewModel.kt](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/favorites/FavoritesViewModel.kt)。
- 结果：[ResultsContent.kt](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/results/ResultsContent.kt)、[ResultsViewModel.kt](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/results/ResultsViewModel.kt)。
- 设置：[SettingsContent.kt](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/settings/SettingsContent.kt)、[SettingsViewModel.kt](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/settings/SettingsViewModel.kt)。
- 局域网界面：[LanShareContent.kt](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/feature/lanshare/LanShareContent.kt)、[LanShareViewModel.kt](android/app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/lanshare/LanShareViewModel.kt)。

</details>

<details>
<summary><strong>业务、数据和局域网实现</strong></summary>

- [`android/core/domain/`](android/core/domain/)：业务规则、数据模型和接口。
- [`android/core/data/`](android/core/data/)：数据库、设置、收藏备份和平台适配。
- [`android/core/lan-share/`](android/core/lan-share/)：HTTP/WebSocket 服务、浏览器页面和文件传输。
- [LanShareServer.kt](android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/server/LanShareServer.kt)：App 端服务。
- [LanShareClient.kt](android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/client/LanShareClient.kt)：App 端连接和发送。
- [LanShareWebScript.kt](android/core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/web/LanShareWebScript.kt)：浏览器交互代码。
- [libs.versions.toml](android/gradle/libs.versions.toml)：Gradle 插件与依赖版本。

</details>

## 修改步骤

1. **搜索功能**：用界面文字、类名或操作名称定位代码。例如：

   ```powershell
   rg -n -i "文件传输|LanShareGateway" android
   ```

   没有 `rg` 时，可在编辑器中按 `Ctrl+Shift+F`，并把搜索范围设为 `android`。

2. **追踪职责**：先看 UI，再确认对应的 ViewModel、Domain 接口、Data 或 LAN 实现和相关测试。搜索结果可能来自测试或注释，不一定是实际运行代码。

3. **改在正确位置**：纯视觉变化留在 UI；用户操作和页面状态放在 Presentation；业务规则放在 Domain；存储或网络细节放在对应实现模块。不要让 UI 直接访问数据库、文件系统或网络实现。

4. **写清楚代码**：新增或改动的主要功能、按钮、界面和操作应有简明中文注释。注释说明意图和容易误解的行为，不要逐行重复代码本身。

   关键异步操作须记录开始、完成、取消和失败，必要时用操作 ID 关联；捕获异常和渲染回退不可静默丢弃。诊断不记录私人内容、不逐帧写入；导出规则见[调试日志](android/DEVELOPMENT.md#导出调试日志)。

5. **验证修改**：按改动范围运行相关测试；涉及 Android 系统、相机、触感、GPU、Wi-Fi 或生命周期时，还要在设备上检查。具体命令见[开发与验证](android/DEVELOPMENT.md#本地验证)。

6. **检查差异**：

   ```powershell
   $repo = 'D:\GitHub\Barcode-generator-for-Android'
   git -c safe.directory=$repo -C $repo status --short --branch
   git -c safe.directory=$repo -C $repo diff --check
   git -c safe.directory=$repo -C $repo diff
   ```

   `-` 表示旧内容，`+` 表示新内容。确认每个文件都与本次修改有关；只暂存明确的文件，不要使用 `git add .`。

## 提交、推送与构建

提交和推送前应先完成相应验证，并复查暂存内容。日常只推送到 `beta`；推送不会自动构建 APK。需要安装包时，按[开发与验证](android/DEVELOPMENT.md#github-actions)手动启动对应工作流。不要提交密钥、令牌或个人机器配置。

遇到权限错误，不要删除 `.git/index.lock`；记录错误并按[开发与验证](android/DEVELOPMENT.md#构建文件被占用时)中的安全步骤处理。不要运行 `git reset --hard`、`git clean -fd` 或强制推送来“清理”问题。
