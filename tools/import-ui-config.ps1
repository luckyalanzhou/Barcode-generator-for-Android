param(
    [string]$InputFile = 'D:\BarcodeGeneratorVerify\UiConfig\incoming\ui-config-beta.json'
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$target = Join-Path $repo 'android\app\src\beta\java\com\luckyalanzhou\barcodegenerator\UiOverrides.generated.kt'
$archiveDir = 'D:\BarcodeGeneratorVerify\UiConfig\processed'

if (-not (Test-Path -LiteralPath $InputFile)) {
    throw "找不到 UI 配置文件：$InputFile"
}
$config = Get-Content -LiteralPath $InputFile -Raw -Encoding UTF8 | ConvertFrom-Json
if ($config.version -ne 1 -or $config.variant -ne 'beta' -or $null -eq $config.adjustments) {
    throw 'UI 配置格式不正确：需要 version=1、variant=beta 和 adjustments。'
}

function Number([object]$value) {
    if ($null -eq $value -or -not ($value -is [double] -or $value -is [int] -or $value -is [decimal])) { throw 'UI 配置包含非数字参数。' }
    return ([double]$value).ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
}
function KotlinString([string]$value) { return '"' + $value.Replace('\\', '\\\\').Replace('"', '\\"') + '"' }

$entries = foreach ($item in @($config.adjustments)) {
    if ([string]::IsNullOrWhiteSpace($item.key)) { throw 'UI 配置存在空 key。' }
    $values = @('widthDp','heightDp','translationXDp','translationYDp','marginLeftDp','marginTopDp','marginRightDp','marginBottomDp','paddingLeftDp','paddingTopDp','paddingRightDp','paddingBottomDp','textSizeSp','alpha','rotation','scaleX','scaleY','minimumWidthDp','minimumHeightDp','cornerRadiusDp') | ForEach-Object { Number $item.$_ }
    '        ' + (KotlinString $item.key) + ' to UiAdjustment(' + ((KotlinString $item.key) + ', ' + ($values -join ', ')) + ')'
}

$body = @"
package com.luckyalanzhou.barcodegenerator

/** 由 tools/import-ui-config.ps1 自动生成；仅供 Beta 测试中心使用。 */
internal object UiOverridesGenerated {
    val values: Map<String, UiAdjustment> = mapOf(
$($entries -join ",`n")
    )
}
"@
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
Set-Content -LiteralPath $target -Value $body -Encoding UTF8
New-Item -ItemType Directory -Force -Path $archiveDir | Out-Null
Move-Item -LiteralPath $InputFile -Destination (Join-Path $archiveDir ([IO.Path]::GetFileName($InputFile))) -Force
Write-Host "已生成：$target"
Write-Host '请在 Beta 测试中心重新打开目标弹窗并运行编译验证。'
