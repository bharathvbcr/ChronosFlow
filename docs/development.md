# ChronosFlow Development

## Local verification

The project is configured to use Android Studio's bundled JBR through `gradle.properties`.

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
.\gradlew.bat test --no-daemon
.\gradlew.bat chronosCiCheck --no-daemon
```

On machines where `java` is not already on `PATH`, use the repo helper:

```powershell
.\scripts\gradlew-jbr.ps1 :app:assembleDebug --no-daemon
.\scripts\gradlew-jbr.ps1 test --no-daemon
.\scripts\gradlew-jbr.ps1 chronosCiCheck --no-daemon
```

`chronosCiCheck` is the regular non-emulator CI path. It builds each app/core/feature module in isolation, assembles the macrobenchmark module, runs unit tests that cover the feature-owned command providers and AI suggestion staging delegate, and assembles the app/daydial Android test APKs so command-palette, AI review, and Android 17 performance scaffolding stay compile-checked without requiring a device.

If Gradle is run outside the helper, set the same runtime explicitly:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

## Source control

Some checkouts may arrive without `.git` metadata. If needed, run:

```powershell
git init
git status --short
```

If `.git` already exists, skip the `git init` step and proceed with normal branch operations.

Do not commit generated Android build outputs. The repository `.gitignore` excludes Gradle, IDE, APK, and screenshot artifacts.
