param(
    [string]$Device = $env:ANDROID_SERIAL,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [switch]$SkipConnectedTests
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$gradle = Join-Path $PSScriptRoot "gradlew-jbr.ps1"
$apk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
$packageName = "com.chronosflow"
$activityName = "com.chronosflow/.MainActivity"

if (-not (Test-Path $Adb)) {
    $Adb = "adb"
}

if ([string]::IsNullOrWhiteSpace($Device)) {
    $devices = @(& $Adb devices | Select-String -Pattern "device$" | ForEach-Object {
        ($_ -split "\s+")[0]
    })

    if ($devices.Count -ne 1) {
        throw "Set ANDROID_SERIAL or pass -Device when zero or multiple devices are attached."
    }

    $Device = $devices[0]
}

Push-Location $repoRoot
try {
    $sdk = (& $Adb -s $Device shell getprop ro.build.version.sdk).Trim()
    if ($sdk -ne "37") {
        throw "Expected Android 17 / API 37 device, but $Device reports API $sdk."
    }

    & $gradle --no-daemon --no-parallel --max-workers=1 :app:assembleDebug --console=plain

    if (-not (Test-Path $apk)) {
        throw "Debug APK was not produced at $apk."
    }

    & $Adb -s $Device install -r $apk
    & $Adb -s $Device logcat -c
    & $Adb -s $Device shell am force-stop $packageName
    & $Adb -s $Device shell am start -W -n $activityName
    Start-Sleep -Seconds 5

    $appPid = (& $Adb -s $Device shell pidof $packageName).Trim()
    if ([string]::IsNullOrWhiteSpace($appPid)) {
        throw "$packageName is not running after launch."
    }

    $packageDump = & $Adb -s $Device shell dumpsys package $packageName
    if ($packageDump | Select-String -Pattern "ACCESS_LOCAL_NETWORK" -Quiet) {
        throw "ACCESS_LOCAL_NETWORK is declared, but Android 17 smoke expects cloud-only sync."
    }

    $crashLines = & $Adb -s $Device logcat -d -t 1200 |
        Select-String -Pattern "FATAL EXCEPTION|AndroidRuntime|ANR"
    if ($crashLines) {
        $crashLines | ForEach-Object { Write-Host $_.Line }
        throw "Crash or ANR signature found after launch."
    }

    if (-not $SkipConnectedTests) {
        & $gradle --no-daemon --no-parallel --max-workers=1 `
            :app:connectedDebugAndroidTest `
            "-Pandroid.testInstrumentationRunnerArguments.class=com.chronosflow.MainActivityTest" `
            --console=plain
    }

    & $gradle --no-daemon --no-parallel --max-workers=1 `
        :core:notifications:testDebugUnitTest `
        --console=plain

    Write-Host "Android 17 smoke passed on $Device with process $appPid."
} finally {
    Pop-Location
}
