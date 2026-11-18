# Barcode Generator for Android 项目规则

## 模拟器与真机验收

- 常规 UI、功能、回归与验收测试直接运行已安装的 `betaDebug`（包名 `com.luckyalanzhou.barcodegenerator.debug`）。Debug 是本项目专用测试包，与 Beta、Official 的安装和应用数据隔离；不要改用 Preview 包做常规验收。
- `betaDebug` 构建和安装固定使用项目本地签名目录 `android/android/.local-validation/android-user-home` 中的 `debug.keystore`，并将 `ANDROID_USER_HOME` 指向该目录；不要使用系统默认 `%USERPROFILE%\.android\debug.keystore`，也不要自动生成另一把 Debug 签名。安装前核对 APK 的 SHA-256 签名与已安装 Debug 包一致；本地签名不可用或指纹不一致时停止安装并查明原因。
- 保留 Debug 现有数据。测试新增的目录、条码内容和收藏使用明确的 `QA` 前缀；结束后只清理本次新增的数据。
- 只有需要与常规 Debug 并行安装独立实例时才使用 `com.luckyalanzhou.barcodegenerator.debug.preview`。继续使用固定 Debug 签名，并在预览构建结束前恢复 `android/app/build.gradle.kts` 中的常规 Debug 包名。
