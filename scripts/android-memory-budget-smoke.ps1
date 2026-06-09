param(
    [string]$Device = $env:ANDROID_SERIAL,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$OutputDir = "app\build\memory-budget",
    [int]$MaxTotalPssMb = 512
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $PSScriptRoot "gradlew-jbr.ps1"
$apk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
$packageName = "com.chronosflow"
$activityName = "com.chronosflow/.MainActivity"
$remoteWindowPath = "/sdcard/chronos-memory-window.xml"

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
        throw "Expected exactly one connected device. Found $($devices.Count). Pass -Device <serial>."
    }

    $Device = $devices[0]
}

$Device = $Device.Trim()
$absoluteOutputDir = Join-Path $repoRoot $OutputDir
New-Item -ItemType Directory -Force -Path $absoluteOutputDir | Out-Null

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)

    & $Adb -s $Device @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "adb command failed: $($Arguments -join ' ')"
    }
}

function Invoke-AdbCapture {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)

    $output = & $Adb -s $Device @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "adb command failed: $($Arguments -join ' ')"
    }
    return $output
}

function Get-BoundsCenter {
    param([string]$Bounds)

    if ($Bounds -notmatch "^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$") {
        throw "Unexpected UI bounds format: $Bounds"
    }

    return @{
        X = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
        Y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    }
}

function Get-UiNodes {
    param([string]$Label)

    $localWindowPath = Join-Path $absoluteOutputDir "$Label-window.xml"
    Invoke-Adb shell uiautomator dump $remoteWindowPath | Out-Null
    Invoke-Adb pull $remoteWindowPath $localWindowPath | Out-Null

    [xml]$window = Get-Content -LiteralPath $localWindowPath -Raw
    return @($window.SelectNodes("//node"))
}

function Find-UiNode {
    param(
        [string]$Label,
        [string[]]$Texts = @(),
        [string[]]$Descriptions = @()
    )

    foreach ($node in Get-UiNodes -Label $Label) {
        $text = $node.GetAttribute("text")
        $description = $node.GetAttribute("content-desc")
        if ($Texts -contains $text -or $Descriptions -contains $description) {
            return $node
        }
    }

    return $null
}

function Tap-UiNode {
    param(
        [string]$Label,
        [string[]]$Texts = @(),
        [string[]]$Descriptions = @()
    )

    $node = Find-UiNode -Label $Label -Texts $Texts -Descriptions $Descriptions
    if ($null -eq $node) {
        $targets = @($Texts + $Descriptions) -join "', '"
        throw "Could not find UI node '$targets' while collecting $Label."
    }

    $center = Get-BoundsCenter -Bounds $node.GetAttribute("bounds")
    Invoke-Adb shell input tap $center.X $center.Y
    Start-Sleep -Milliseconds 900
}

function Assert-UiNode {
    param(
        [string]$Label,
        [string[]]$Texts = @(),
        [string[]]$Descriptions = @()
    )

    $node = Find-UiNode -Label $Label -Texts $Texts -Descriptions $Descriptions
    if ($null -eq $node) {
        $targets = @($Texts + $Descriptions) -join "', '"
        throw "Expected UI node '$targets' was not visible during $Label."
    }
}

function Get-TotalPssKb {
    param([string[]]$MemInfo)

    foreach ($line in $MemInfo) {
        if ($line -match "TOTAL\s+PSS:\s+(\d+)") {
            return [int]$Matches[1]
        }
    }

    foreach ($line in $MemInfo) {
        if ($line -match "^\s*TOTAL\s+(\d+)\s+") {
            return [int]$Matches[1]
        }
    }

    throw "Could not parse total PSS from dumpsys meminfo output."
}

function Capture-Memory {
    param(
        [string]$Label,
        [string]$ProcessId,
        [string[]]$ExpectedTexts = @(),
        [string[]]$ExpectedDescriptions = @()
    )

    if ($ExpectedTexts.Count -gt 0 -or $ExpectedDescriptions.Count -gt 0) {
        Assert-UiNode -Label $Label -Texts $ExpectedTexts -Descriptions $ExpectedDescriptions
    }

    $memInfo = Invoke-AdbCapture shell dumpsys meminfo $packageName
    $memInfoPath = Join-Path $absoluteOutputDir "$Label-meminfo.txt"
    $memInfo | Set-Content -LiteralPath $memInfoPath -Encoding UTF8

    $totalPssKb = Get-TotalPssKb -MemInfo $memInfo
    $totalPssMb = [math]::Round($totalPssKb / 1024, 1)

    return [pscustomobject]@{
        Surface = $Label
        ProcessId = $ProcessId
        TotalPssKb = $totalPssKb
        TotalPssMb = $totalPssMb
        MaxTotalPssMb = $MaxTotalPssMb
        RawMemInfo = $memInfoPath
    }
}

function Start-App {
    Invoke-Adb shell am force-stop $packageName
    Invoke-Adb shell am start "-W" "-n" $activityName | Out-Null
    Start-Sleep -Seconds 5

    $startedPid = (Invoke-AdbCapture shell pidof $packageName | Out-String).Trim()
    if ([string]::IsNullOrWhiteSpace($startedPid)) {
        throw "$packageName is not running after launch."
    }

    return $startedPid
}

& $gradle --no-daemon --no-parallel --max-workers=1 :app:assembleDebug --console=plain
if (-not (Test-Path -LiteralPath $apk)) {
    throw "Debug APK was not produced at $apk."
}

Invoke-Adb install "-r" $apk | Out-Null

$results = New-Object System.Collections.Generic.List[object]

$appPid = Start-App
Tap-UiNode -Label "today-open" -Descriptions "Today" -Texts "Today"
$results.Add((Capture-Memory -Label "today-dial" -ProcessId $appPid -ExpectedTexts "Today" -ExpectedDescriptions "Open command palette"))

$appPid = Start-App
Tap-UiNode -Label "calendar-open-menu" -Descriptions "Menu"
Start-Sleep -Milliseconds 500
$calendarNode = Find-UiNode -Label "calendar-find" -Texts "Calendars"
if ($null -eq $calendarNode) {
    Invoke-Adb shell input swipe 360 2320 360 900 300
    Start-Sleep -Milliseconds 500
}
Tap-UiNode -Label "calendar-open" -Texts "Calendars"
$results.Add((Capture-Memory -Label "calendar" -ProcessId $appPid -ExpectedTexts "Calendars"))

$appPid = Start-App
Tap-UiNode -Label "focus-open" -Descriptions "Focus" -Texts "Focus"
$results.Add((Capture-Memory -Label "focus-planner" -ProcessId $appPid -ExpectedTexts "Ready to focus"))

$csvPath = Join-Path $absoluteOutputDir "memory-budget.csv"
$results | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding UTF8

$failed = @($results | Where-Object { $_.TotalPssMb -gt $MaxTotalPssMb })
Write-Host "Memory budget smoke captured $($results.Count) surfaces on $Device."
Write-Host "Summary: $csvPath"
foreach ($result in $results) {
    Write-Host "$($result.Surface): $($result.TotalPssMb) MB total PSS (pid $($result.ProcessId), limit $MaxTotalPssMb MB)"
}

if ($failed.Count -gt 0) {
    $failedSurfaces = ($failed | ForEach-Object { "$($_.Surface)=$($_.TotalPssMb)MB" }) -join ", "
    throw "Memory budget exceeded: $failedSurfaces"
}
