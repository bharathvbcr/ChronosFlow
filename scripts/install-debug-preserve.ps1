param(
    [string]$Device
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"

if (-not (Test-Path $adb)) {
    $adb = "adb"
}

& (Join-Path $PSScriptRoot "gradlew-jbr.ps1") --no-daemon --max-workers=1 :app:assembleDebug
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

if (-not (Test-Path $apk)) {
    throw "Debug APK was not produced at $apk"
}

$deviceArgs = @()
if ($Device) {
    $deviceArgs += @("-s", $Device)
}

& $adb @deviceArgs install -r $apk
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& $adb @deviceArgs shell am start -n "com.chronosflow/.MainActivity"
exit $LASTEXITCODE
