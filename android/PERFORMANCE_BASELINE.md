# 渲染性能基线

用于比较生成耗时和结果行位图驻留内存的本地诊断基准：

```powershell
Set-Location android
./gradlew.bat :app:testBetaDebugUnitTest --no-daemon --no-configuration-cache -PenableAppUnitTests=true --tests 'com.luckyalanzhou.barcodegenerator.ui.feature.results.BarcodeRenderPerformanceBaselineTest'
```

基准使用真实 ZXing CODE_128 编码、Robolectric SDK 35、密度 3、结果行宽 1080 px；每种数量运行 3 次并取中位数。时间是本机 JVM/模拟 Android 图形环境数据，不代表手机 GPU 或滚动帧率。内存按 Bitmap allocation byte count 统计，包含原始码图与完整结果行。

| 条码数 | ARGB_8888 时间范围 / 驻留内存 | RGB_565 时间范围 / 驻留内存 |
| ---: | ---: | ---: |
| 10 | 64–231 ms / 13.89 MiB | 64–97 ms / 8.99 MiB |
| 20 | 91–207 ms / 27.78 MiB | 110–185 ms / 17.98 MiB |
| 100 | 364–802 ms / 140.98 MiB | 415–827 ms / 91.95 MiB |

变化：两次独立本机运行都在同一测试进程内比较两种格式。RGB_565 将结果行位图减半；100 条总驻留内存减少约 49.03 MiB（约 34.8%），20 条减少约 9.81 MiB。计时跨运行波动很大且区间重叠，不能据此宣称性能变快或变慢；因此按稳定的内存数据和常见批量 10–20 条这一使用事实，生产策略仅在超过 20 条时为屏幕预览选用 RGB_565，常见批量保持高精度。分享/保存导出仍默认 ARGB_8888。浅/深色逐像素测试将最大色差限制为 8/255。

100 行结果列表通过 Compose 语义滚动到底端，在两次本机 Robolectric 运行中分别耗时 31 ms 和 23 ms。该数字仅验证真实 `ResultsContent` 滚动路径可执行且可量化，不代表手机帧时间、GPU Jank 或触控滚动速度。

尚缺真实设备上的滚动帧时间/Jank 与实际进程峰值内存数据；当前没有连接设备，需在接入 Android 设备后补测，不以 Robolectric 结果替代。
