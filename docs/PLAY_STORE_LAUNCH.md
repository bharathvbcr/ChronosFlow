# Play Store Launch Checklist — ChronosFlow

Status snapshot as of 16 June 2026. This is the prep doc for an **alpha/beta (closed
testing)** release of `com.chronosflow` (+ companion Wear app). Items marked **MANUAL**
are done in the Play Console or on a device, not in the repo.

## 0. Readiness verdict

- **Build/release config:** READY. `versionCode=1`, `versionName=0.1.0`, `targetSdk=37`,
  `minSdk=26`, release signing wired via `keystore.properties`.
- **Privacy policy:** DONE — see [`/PRIVACY.md`](../PRIVACY.md). Must be **hosted at a
  public URL** before submission (e.g. GitHub Pages) and linked in the Console.
- **Code shrinking (R8):** Intentionally **OFF** for the alpha (see §5). Safe; larger APK.
- **Remaining gates:** hosted privacy URL, Play Console declarations (§3), a clean release
  branch (§6), and an on-device smoke pass on the signed build (§7).

## 1. App identity & versioning

| Field | Value | Source |
|-------|-------|--------|
| applicationId | `com.chronosflow` | `app/build.gradle.kts:40` |
| versionCode (phone) | `1` | `gradle.properties` → `chronos.versionCode` |
| versionCode (wear) | `100001` (base + 100000 band) | `wear/build.gradle.kts` |
| versionName | `0.1.0` | `gradle.properties` → `chronos.versionName` |
| minSdk / targetSdk / compileSdk | 26 / 37 / 37 | `app/build.gradle.kts:37-42` |

Phone and Wear share `applicationId` + signing key (required for the Wearable Data Layer)
and use distinct versionCodes so Play multi-APK delivery routes each to the right device.

## 2. Privacy policy — **(was the hard blocker)**

- Content written: [`/PRIVACY.md`](../PRIVACY.md). Covers Health Connect sleep,
  medication/mood, screen-time (Usage Access), calendar, contacts, biometric, on-device +
  optional cloud AI, backups/export, and Wear sync.
- **MANUAL:** host it at a stable public URL and paste that URL into
  **Play Console → App content → Privacy policy** (and into the store listing).

## 3. Play Console declarations — **MANUAL**

### 3a. Foreground service (specialUse)
- Declared in manifest: `FocusService`, `foregroundServiceType="specialUse"`,
  subtype property `"Active focus session timer"` (`AndroidManifest.xml:114-122`).
- Console justification text to submit:
  > "Active focus session timer. The user starts a countdown from a scheduled work block;
  > the timer must keep running and update its progress notification after the app is
  > backgrounded or the screen is off, with pause/resume/extend/stop controls. No
  > equivalent standard FGS type fits a user-initiated focus timer."

### 3b. Health Connect declaration
- Permissions: `health.READ_SLEEP`, `health.READ_HEALTH_DATA_IN_BACKGROUND`
  (`AndroidManifest.xml:16-17`). Read-only.
- Console → Health apps declaration: state read-only sleep import, used for sleep-aware
  planning + trends, never shared/sold, with the privacy URL linked. The permission
  rationale screen is already wired (`SHOW_PERMISSIONS_RATIONALE` +
  `VIEW_PERMISSION_USAGE` alias, `AndroidManifest.xml:90-104`).

### 3c. Sensitive / special-access permissions
- `PACKAGE_USAGE_STATS` (Usage Access): off by default, opt-in, on-device only — declare
  purpose as screen-time insights.
- `SCHEDULE_EXACT_ALARM`: justify as user-set medication/focus reminders. (No
  `USE_EXACT_ALARM` is declared, so no "alarm/clock app" eligibility claim is needed.)
- `READ_CONTACTS`, `READ_CALENDAR`/`WRITE_CALENDAR`: declare per Data Safety (§4).

### 3d. Data Safety form answers
- Data collected/stored on device: planning data, medication/mood, sleep (HC), screen-time,
  calendar, contacts, journal/goals.
- **Data shared with third parties: No** (cloud AI is optional/user-initiated; disclose it
  as "may be processed by Google's Gemini if you enable cloud suggestions").
- **Data sold: No.** No advertising or analytics SDKs.
- Data encrypted in transit: N/A for on-device; cloud AI uses HTTPS.
- Users can request deletion: data is local; uninstall removes it (state this).

## 4. Permissions inventory (from `AndroidManifest.xml`)

`INTERNET`, `POST_NOTIFICATIONS`, `POST_PROMOTED_NOTIFICATIONS`, `READ_CALENDAR`,
`WRITE_CALENDAR`, `READ_CONTACTS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`,
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `USE_BIOMETRIC`,
`health.READ_SLEEP`, `health.READ_HEALTH_DATA_IN_BACKGROUND`, `PACKAGE_USAGE_STATS`.

No location, camera, microphone, SMS, or `QUERY_ALL_PACKAGES`. Exported components are
limited to the launcher activity, the Health permission-usage alias (protected by
`START_VIEW_PERMISSION_USAGE`), home-screen widget receivers (framework-required), and the
Wear message listener (requires a paired watch). All other components are `exported=false`.

## 5. Code shrinking (R8) — intentionally off for alpha

The `release` build type (`app/build.gradle.kts:70-72`) sets only the signing config; it
does **not** set `isMinifyEnabled`, so R8 shrinking/obfuscation is **off**. This is
deliberate for the first alpha — it avoids any risk of stripping reflection-driven code
(AppFunctions aggregation, Glance, Room, Hilt) without verified keep rules. Trade-off:
larger download.

**Before enabling R8 later:** set `isMinifyEnabled = true` (+ `isShrinkResources = true`),
add a `proguard-rules.pro` with keep rules for AppFunctions/Glance, then do a **full
on-device smoke pass on the minified build** (the failure mode is "works in debug, crashes
in release"). Do not enable it in the same release you submit for the first time.

## 6. Source / branch hygiene — **MANUAL, do before tagging**

- Work is currently on `recover-merge-floating-sidebar` with a large dirty tree and recent
  `WIP:`/`Merge recovered-lost-work` commits. **Land the merge to `main` and get a clean
  working tree** before cutting the release tag, so the alpha is built from a known commit.

## 7. Pre-submission build & smoke test

1. Build the signed bundle: `./gradlew :app:bundleRelease`
   (and `./gradlew :wear:bundleRelease` for the watch). Output AAB:
   `app/build/outputs/bundle/release/`.
2. Install the release build on a device and smoke-test the recently-fixed/unverified areas:
   - Focus start → background → notification progress → pause/resume/extend → complete.
   - Plan timeline: create / delete / duplicate / undo a block (latency fix).
   - App lock on a device **with no screen lock** (medication fail-open fix).
   - Wear sync on app open; widget body tap opens the app.
   - Predictive back gesture across sections.
   - **AOD (always-on display):** live focus countdown — this path is compile-verified only
     and must be confirmed on a real device that supports promoted/Live-Update notifications.
3. On Windows, if the build hits a daemon file-lock or stale KSP factory: `./gradlew --stop`
   and/or re-run with `--rerun-tasks` (environment issue, not a code error).

## 8. Store listing assets — **MANUAL**

- App icon (present: `@mipmap/ic_launcher`), feature graphic, phone + Wear screenshots,
  short + full description, content rating questionnaire, target audience, and the
  privacy-policy URL from §2.

---

### Go / no-go for closed alpha

**Blocking:** host privacy URL (§2), Console declarations (§3), clean release commit (§6),
and the signed-build smoke pass (§7) — including the AOD check that is currently
device-unverified. Build/signing/permissions are otherwise ready.
