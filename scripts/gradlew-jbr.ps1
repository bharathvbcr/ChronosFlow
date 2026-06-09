$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$jbrHome = "C:\Program Files\Android\Android Studio\jbr"

if (-not (Test-Path (Join-Path $jbrHome "bin\java.exe"))) {
    throw "Android Studio JBR was not found at $jbrHome"
}

$env:JAVA_HOME = $jbrHome
$env:Path = "$jbrHome\bin;$env:Path"

Push-Location $repoRoot
try {
    & .\gradlew.bat @args
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
