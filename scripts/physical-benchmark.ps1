param(
    [string]$Device = $env:ANDROID_SERIAL,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$OutputDir = "benchmark\build\physical-benchmark"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not (Test-Path -LiteralPath $Adb)) {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($null -eq $adbCommand) {
        throw "adb was not found. Pass -Adb or install Android platform-tools."
    }
    $Adb = $adbCommand.Source
}

if ([string]::IsNullOrWhiteSpace($Device)) {
    $devices = @(
        & $Adb devices |
            Select-String -Pattern "device$" |
            ForEach-Object { ($_ -split "\s+")[0] }
    )

    if ($devices.Count -ne 1) {
        throw "Expected exactly one connected physical device. Found $($devices.Count). Pass -Device <serial>."
    }

    $Device = $devices[0]
}

$Device = $Device.Trim()

function Get-DeviceProperty {
    param([string]$Name)

    $value = & $Adb -s $Device shell getprop $Name
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to read Android property $Name from $Device."
    }
    return ($value | Out-String).Trim()
}

$deviceProps = [ordered]@{
    "serial" = $Device
    "ro.product.manufacturer" = Get-DeviceProperty "ro.product.manufacturer"
    "ro.product.model" = Get-DeviceProperty "ro.product.model"
    "ro.build.version.release" = Get-DeviceProperty "ro.build.version.release"
    "ro.build.version.sdk" = Get-DeviceProperty "ro.build.version.sdk"
    "ro.kernel.qemu" = Get-DeviceProperty "ro.kernel.qemu"
    "ro.boot.qemu" = Get-DeviceProperty "ro.boot.qemu"
}

$isEmulator = $Device -like "emulator-*" -or
    $deviceProps["ro.kernel.qemu"] -eq "1" -or
    $deviceProps["ro.boot.qemu"] -eq "1"

if ($isEmulator) {
    throw "Refusing to run performance benchmarks on $Device because it is an emulator. AndroidX Benchmark rejects emulator results as non-representative; use a physical Android device for :benchmark:connectedCheck."
}

$absoluteOutputDir = Join-Path $repoRoot $OutputDir
New-Item -ItemType Directory -Force -Path $absoluteOutputDir | Out-Null

$deviceInfoPath = Join-Path $absoluteOutputDir "device-info.txt"
$deviceProps.GetEnumerator() |
    ForEach-Object { "$($_.Key)=$($_.Value)" } |
    Set-Content -Path $deviceInfoPath -Encoding UTF8

$env:ANDROID_SERIAL = $Device

& .\scripts\gradlew-jbr.ps1 --no-daemon --no-parallel --max-workers=1 :benchmark:connectedCheck --console=plain
if ($LASTEXITCODE -ne 0) {
    throw ":benchmark:connectedCheck failed on physical device $Device."
}

$reportPath = Join-Path $repoRoot "benchmark\build\reports\androidTests\connected\benchmark\index.html"
$resultPath = Join-Path $repoRoot "benchmark\build\outputs\androidTest-results\connected\benchmark"
$additionalOutputPath = Join-Path $repoRoot "benchmark\build\outputs\connected_android_test_additional_output\benchmark\connected"

Write-Host "Physical benchmark passed on $Device."
Write-Host "Device info: $deviceInfoPath"
Write-Host "HTML report: $reportPath"
Write-Host "Raw results: $resultPath"
Write-Host "Additional benchmark output: $additionalOutputPath"
