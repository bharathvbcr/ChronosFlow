# GitNexus + Android CLI Agent Guide

Use both tools as the default workflow in this repo:
- GitNexus for repository analysis, ownership checks, and impact tracing.
- Android CLI for runtime debugging and device/app workflows.

## Most common commands we use daily

- `android -h`
- `android info`
- `.\scripts\install-debug-preserve.ps1 [-Device <serial>]`
- `android layout [--pretty] [--output=<path>] [--diff]`
- `android screen capture [--output=<path>] [--annotate]`
- `android screen resolve --screenshot=<path> --string="<command #N>"`
- `android sdk list <pattern>`
- `android sdk install <package[@version]>`
- `android docs search "<query>"`
- `android docs fetch kb://...`
- `android skills list [--long]`
- `android -V`
- `gitnexus-guide`
- `gitnexus-impact-analysis`
- `gitnexus-debugging`
- `gitnexus-exploring`

## 1) Quick start

- Verify CLI is available:

  ```bash
  which android
  ```

- Confirm command help (always use this before running a new path):

  ```bash
  android -h
  ```

## 2) Project flow (development)

- Create scaffold:

  ```bash
  android create --name=<app-name> --output=<path> [template]
  ```

  - `android create list` to view templates
  - Add `--dry-run` to validate without writing files
  - Add `--verbose` to show copied files

- Inspect project outputs:

  ```bash
  android describe [--project_dir=<path>]
  ```

- Read docs from terminal:

  ```bash
  android docs search "<query>"
  android docs fetch kb://...
  ```

## 3) Emulator/devices

> Note: `emulator` command has reported Windows issues in Android’s CLI docs. Use your existing emulator tooling if the command is unavailable.

- List/create/start/stop:

  ```bash
  android emulator create [--list-profiles] [--profile=<name>]
  android emulator list
  android emulator start <device-name>
  android emulator stop <serial-number>
  ```

## 4) Build/debug loop on device/emulator

- Install required dependencies:

  ```bash
  android sdk list <pattern>
  android sdk install <package[@version]> [--beta|--canary]
  android sdk update [--beta|--canary] [<package>]
  ```

- Run app APKs (no rebuild step):

  ```bash
  android run --apks=<apk1>,<apk2> [--debug] [--device=<serial>] [--activity=<activity>] [--type=<type>]
  ```

  - Use `--device` when multiple targets are attached.
  - `--type` options: `ACTIVITY`, `WATCH_FACE`, `TILE`, `COMPLICATION`, `DECLARATIVE_WATCH_FACE`.
  - For ChronosFlow phone app debugging, prefer `.\scripts\install-debug-preserve.ps1 [-Device <serial>]`. It builds the debug APK, deploys with `adb install -r`, and launches `.MainActivity` without uninstalling the existing package.

## 5) UI investigation

- Capture and inspect layout:

  ```bash
  android layout [--pretty] [--output=<path>] [--diff]
  ```

- Screenshot + coordinate resolve:

  ```bash
  android screen capture [--output=<path>] [--annotate]
  android screen resolve --screenshot=<path> --string="<command #N>"
  ```

## 6) Agent skills

- Manage installed skills:

  ```bash
  android skills list [--long]
  android skills find <query>
  android skills add [--all] [--agent=<agent>] [--skill=<skill>]
  android skills remove --skill=<skill> [--agent=<agent>]
  ```

## 7) Default agent debug path

When anything fails:

1. `android -h`
2. `android info`
3. `android sdk list`
4. `.\scripts\install-debug-preserve.ps1 [-Device <serial>]`
5. `android layout` and `android screen capture --annotate`

## 8) Daily one-liner sequence

Copy/paste when starting work:

```bash
android -h
android info
android sdk list "platforms|build-tools"
.\scripts\install-debug-preserve.ps1
android layout --pretty --output=./layout.json
android screen capture --annotate --output=./ui.png
```

If a command fails:

```bash
android info
android sdk list <pattern>
```

Then re-run the exact failing step with `-h` first.

## 9) GitNexus workflow

Use GitNexus for any non-trivial change:

1. `gitnexus-guide`: confirm repo conventions and owner boundaries.
2. `gitnexus-impact-analysis`: identify blast radius and sibling files.
3. `gitnexus-exploring`: verify architecture context before touching code.
4. `gitnexus-debugging`: run structured debugging passes when logs are unclear.
5. Make the minimal fix and re-check with GitNexus impact again.

## 10) Daily integration pattern

- Start with GitNexus impact pass.
- Apply fix with Android CLI commands.
- Finish with GitNexus verification pass.
