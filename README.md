# 条码生成器 Beta

Android 条码生成器的 Beta 测试版本，用于验证新功能、现代化 Android 架构迁移和交互动画。

> 当前 Beta 版本：`1.0.57`<br>
> 当前分支：`beta`<br>
> 测试包名：`com.luckyalanzhou.barcodegenerator.test`<br>
> 稳定性说明：测试版，不建议作为唯一的生产环境应用使用。

## 当前 Beta 状态

- 已完成 Compose 主界面迁移，使用四个底部 Tab：生成、历史、收藏、设置。
- 底部 Tab 使用液态玻璃选中框，支持跟随手指滑动、触碰放大和弹簧回弹动画。
- 选中框当前高度为 `56dp`；图标和文字在动画结束后恢复原始尺寸。
- 增加 Beta 测试中心、调试日志导出和 Beta 专用诊断能力。
- Beta 使用独立 applicationId，应用数据和入口均独立于其他版本。

## 功能

- 生成 Code 128-B、QR Code、Code 39、EAN-13、EAN-8、UPC-A、ITF-14 和 Codabar。
- 支持一次输入多行内容并批量生成条码。
- 支持相机/图片识别文字和条码内容。
- 查看生成历史，重新生成、复制、分享和删除条码。
- 收藏条码，使用文件夹整理、重命名、移动和删除收藏内容。
- 收藏数据备份与恢复。
- 通过局域网在手机和浏览器之间分享条码和文件。
- 支持浅色/深色主题、条码结果页亮屏和应用内更新检查。

## 下载 Beta

最新 Beta Release：

<https://github.com/luckyalanzhou/Barcode-generator-for-Android/releases/tag/android-test-v1.0.57>

APK 文件名：`BarcodeGeneratorTest1.0.57.apk`

```text
应用包名：com.luckyalanzhou.barcodegenerator.test
```

## 项目结构

```text
android/
├─ app/             # Android 应用、Compose 页面和应用层业务
├─ core/domain/     # 与 Android 无关的领域模型和用例
├─ core/data/       # Room、文件和数据仓储
└─ core/ui/         # Compose 动画和共享 UI 配置
```

## 测试说明

- Beta 用于新功能、架构迁移、动画调整和兼容性验证。
- 测试反馈请附上 Beta 版本号、Android 版本、设备型号和复现步骤。
