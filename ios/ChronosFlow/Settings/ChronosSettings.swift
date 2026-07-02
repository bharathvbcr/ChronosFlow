import SwiftUI
import Observation
import ChronosCore

// MARK: - ChronosSettings
//
// The iOS-native analogue of Android's settings surface (AI Settings, Privacy/Sync,
// Notifications, Appearance) plus the developer feature-flag set. On Android these live in
// the sidebar pages + DataStore-backed `ChronosFeatureFlags`; here a single `@Observable`
// store backed by the App-Group `UserDefaults` is the source of truth, so the app, widgets,
// and App Intents all read the same preferences.
//
// Uses the iOS 27 `@Observable` macro (lazy `@State` initialization). Each stored property
// persists to `UserDefaults` in `didSet`; `init` rehydrates from disk. No SwiftData schema
// change — these are device-local preferences, exactly like Android's DataStore.

@Observable
final class ChronosSettings {
    /// Process-wide singleton so any view (and the focus model, notification scheduler, etc.)
    /// can read prefs without injection plumbing — mirrors the Android `ChronosFeatureFlags`
    /// being globally reachable.
    @MainActor static let shared = ChronosSettings()

    @ObservationIgnored private let defaults: UserDefaults

    @MainActor private init() {
        // App-Group store so the widget/intents extensions see the same flags; falls back to
        // `.standard` when the App Group isn't provisioned (e.g. a bare simulator).
        self.defaults = UserDefaults(suiteName: ChronosStore.appGroup) ?? .standard

        // Feature flags. Each graduated flag is resolved through ChronosCore's FeatureGraduations:
        // installs that parked a stale `false` before the feature graduated are promoted on-by-default
        // exactly once (while the wave's promotion marker is unset), after which a deliberate stored
        // choice is respected. The one-time promotion is made durable here by persisting the marker.
        // `routines` is the only flag that never shipped off, so it is not graduated (plain default).
        habitsEnabled = Self.resolveGraduated(defaults, "flag.habits", FeatureGraduationKeys.habitsEnabled)
        medicationEnabled = Self.resolveGraduated(defaults, "flag.medication", FeatureGraduationKeys.medicationEnabled)
        insightsEnabled = Self.resolveGraduated(defaults, "flag.insights", FeatureGraduationKeys.reviewEnabled)
        goalsEnabled = Self.resolveGraduated(defaults, "flag.goals", FeatureGraduationKeys.goalsEnabled)
        journalEnabled = Self.resolveGraduated(defaults, "flag.journal", FeatureGraduationKeys.journalEnabled)
        sleepEnabled = Self.resolveGraduated(defaults, "flag.sleep", FeatureGraduationKeys.sleepEnabled)
        routinesEnabled = defaults.boolOr("flag.routines", true)

        // Appearance.
        themeMode = ThemeMode(rawValue: defaults.string(forKey: "appearance.theme") ?? "") ?? .system
        backdrop = BackdropStyle(rawValue: defaults.string(forKey: "appearance.backdrop") ?? "") ?? .aurora
        dynamicColorEnabled = defaults.boolOr("appearance.dynamicColor", true)
        glassEnabled = defaults.boolOr("appearance.glass", true)
        reduceMotionPreference = defaults.boolOr("appearance.reduceMotion", false)
        increaseContrast = defaults.boolOr("appearance.increaseContrast", false)

        // AI. On iOS the on-device Foundation Models is the only path, so privacy mode is
        // effectively "on-device"; we still expose the planning-style + auto-apply controls.
        aiEnabled = defaults.boolOr("ai.enabled", true)
        planningStyle = PlanningStyle(rawValue: defaults.string(forKey: "ai.planningStyle") ?? "") ?? .balanced
        autoApplyPlan = defaults.boolOr("ai.autoApply", false)
        autoApplyAssist = defaults.boolOr("ai.autoApplyAssist", false)

        // Planning behaviour toggles (Android parity: SidebarPageContent.kt lines 961–963,
        // "Protect focus blocks" / "Add breaks automatically" / "Preserve manual blocks").
        // Defaults match Android's CheckboxSetting initial state: protect focus on, auto-breaks on,
        // preserve manual edits on — conservative choices that never silently discard the user's work.
        protectFocusBlocks = defaults.boolOr("ai.protectFocus", true)
        addBreaksAutomatically = defaults.boolOr("ai.autoBreaks", true)
        preserveManualBlocks = defaults.boolOr("ai.preserveManual", true)

        // Notifications.
        remindersEnabled = defaults.boolOr("notif.reminders", true)
        medicationRemindersEnabled = defaults.boolOr("notif.medication", true)
        habitRemindersEnabled = defaults.boolOr("notif.habits", true)
        taskRemindersEnabled = defaults.boolOr("notif.tasks", true)
        focusLiveActivityEnabled = defaults.boolOr("notif.focusLiveActivity", true)
        quietHoursStartMinute = defaults.intOr("notif.quietStart", 22 * 60)
        quietHoursEndMinute = defaults.intOr("notif.quietEnd", 7 * 60)
        logReminderEnabled = defaults.boolOr("notif.logReminder", false)

        // Privacy.
        medicationLockEnabled = defaults.boolOr("privacy.medicationLock", true)
        healthKitSleepEnabled = defaults.boolOr("privacy.healthKitSleep", false)
        sensitiveTitlesRedacted = defaults.boolOr("privacy.redactTitles", false)

        // AI privacy mode. iOS Foundation Models is on-device only, so the default is the on-device
        // path; the enum still mirrors Android (ON_DEVICE_ONLY / CLOUD_ALLOWED / DISABLED) so a cloud
        // route can graduate in later. Android parity: the assistant privacy-mode preference.
        privacyMode = PrivacyMode(rawValue: defaults.string(forKey: "ai.privacyMode") ?? "") ?? .onDeviceOnly

        // Companion-app (Meridian / DevTime) interop. Off until the user grants consent in each app
        // independently (no cross-app trust pinning on iOS). Android parity: KEY_INTEROP_SHARING_*.
        interopConsentGranted = defaults.boolOr("privacy.interop.consentGranted", false)
        interopMedicationSharingEnabled = defaults.boolOr("privacy.interop.medicationSharing", false)

        // Collapsible Privacy & Sync section expand-state (Android parity: privacy.*.expanded,
        // rememberPersistentUiBooleanSetting defaultValue = true → all open on first run).
        privacyAppPermissionsExpanded = defaults.boolOr("privacy.appPermissions.expanded", true)
        privacySensitiveContentExpanded = defaults.boolOr("privacy.sensitiveContent.expanded", true)
        privacyCloudSyncExpanded = defaults.boolOr("privacy.cloudSync.expanded", true)
        privacyWearLinkExpanded = defaults.boolOr("privacy.wearLink.expanded", true)
        privacyCompanionAppExpanded = defaults.boolOr("privacy.companionApp.expanded", true)

        // Focus: remember the user's last-selected work/break split preset across sessions.
        // Android parity: presets are not persisted there, but iOS keeps the last pick (UX polish).
        focusLastPreset = FocusSplitPreset(rawValue: defaults.string(forKey: "focus.lastPreset") ?? "") ?? .p25_5

        // Screen Time.
        screenTimeEnabled = defaults.boolOr("screenTime.enabled", false)

        // Sync. Shares the key ChronosStore reads at container-build time; takes effect next launch.
        cloudSyncEnabled = defaults.boolOr(ChronosStore.cloudSyncKey, false)
    }

    // MARK: Feature flags
    //
    // Every graduated flag persists both the stored value AND its wave's promotion marker on write:
    // a deliberate user choice records that the one-time off→on promotion has happened, so the stored
    // value (including an explicit `false`) is respected from then on. Mirrors Android's
    // writeChronosUiBooleanSetting, which sets the marker on every deliberate write of a graduated key.
    var habitsEnabled: Bool {
        didSet { defaults.set(habitsEnabled, forKey: "flag.habits"); markPromoted(FeatureGraduationKeys.habitsEnabled) }
    }
    var medicationEnabled: Bool {
        didSet { defaults.set(medicationEnabled, forKey: "flag.medication"); markPromoted(FeatureGraduationKeys.medicationEnabled) }
    }
    var insightsEnabled: Bool {
        didSet { defaults.set(insightsEnabled, forKey: "flag.insights"); markPromoted(FeatureGraduationKeys.reviewEnabled) }
    }
    var goalsEnabled: Bool {
        didSet { defaults.set(goalsEnabled, forKey: "flag.goals"); markPromoted(FeatureGraduationKeys.goalsEnabled) }
    }
    var journalEnabled: Bool {
        didSet { defaults.set(journalEnabled, forKey: "flag.journal"); markPromoted(FeatureGraduationKeys.journalEnabled) }
    }
    var sleepEnabled: Bool {
        didSet { defaults.set(sleepEnabled, forKey: "flag.sleep"); markPromoted(FeatureGraduationKeys.sleepEnabled) }
    }
    /// Routines never shipped disabled, so it is a plain default-on flag (no graduation marker).
    var routinesEnabled: Bool { didSet { defaults.set(routinesEnabled, forKey: "flag.routines") } }

    // MARK: Appearance
    var themeMode: ThemeMode { didSet { defaults.set(themeMode.rawValue, forKey: "appearance.theme") } }
    var backdrop: BackdropStyle { didSet { defaults.set(backdrop.rawValue, forKey: "appearance.backdrop") } }
    var dynamicColorEnabled: Bool { didSet { defaults.set(dynamicColorEnabled, forKey: "appearance.dynamicColor") } }
    var glassEnabled: Bool { didSet { defaults.set(glassEnabled, forKey: "appearance.glass") } }
    /// User-chosen "reduce motion" preference, OR-ed with the system accessibility setting at
    /// the call site so either source disables motion (Android parity: app toggle + system).
    var reduceMotionPreference: Bool { didSet { defaults.set(reduceMotionPreference, forKey: "appearance.reduceMotion") } }
    var increaseContrast: Bool { didSet { defaults.set(increaseContrast, forKey: "appearance.increaseContrast") } }

    // MARK: AI
    var aiEnabled: Bool { didSet { defaults.set(aiEnabled, forKey: "ai.enabled") } }
    var planningStyle: PlanningStyle { didSet { defaults.set(planningStyle.rawValue, forKey: "ai.planningStyle") } }
    var autoApplyPlan: Bool { didSet { defaults.set(autoApplyPlan, forKey: "ai.autoApply") } }
    /// When on, a NEW task form auto-applies non-destructive smart-fill suggestions: empty fields
    /// are filled from the detections; fields the user already set are never overwritten.
    /// Android parity: ChronosUiSettingsKeys.KEY_ASSIST_AUTO_APPLY ("Auto-apply form suggestions").
    var autoApplyAssist: Bool { didSet { defaults.set(autoApplyAssist, forKey: "ai.autoApplyAssist") } }
    /// When on, the planner never reschedules or shortens existing FOCUS blocks when it regenerates a
    /// plan. Android parity: onProtectFocusChanged ("Protect focus blocks").
    var protectFocusBlocks: Bool { didSet { defaults.set(protectFocusBlocks, forKey: "ai.protectFocus") } }
    /// When on, the planner inserts short recovery breaks between long work stretches automatically.
    /// Android parity: onAddBreaksAutomaticallyChanged ("Add breaks automatically").
    var addBreaksAutomatically: Bool { didSet { defaults.set(addBreaksAutomatically, forKey: "ai.autoBreaks") } }
    /// When on, blocks the user placed or edited by hand are kept exactly as-is across regenerations.
    /// Android parity: onPreserveManualBlocksChanged ("Preserve manual blocks").
    var preserveManualBlocks: Bool { didSet { defaults.set(preserveManualBlocks, forKey: "ai.preserveManual") } }

    // MARK: Notifications
    var remindersEnabled: Bool { didSet { defaults.set(remindersEnabled, forKey: "notif.reminders") } }
    var medicationRemindersEnabled: Bool { didSet { defaults.set(medicationRemindersEnabled, forKey: "notif.medication") } }
    var habitRemindersEnabled: Bool { didSet { defaults.set(habitRemindersEnabled, forKey: "notif.habits") } }
    var taskRemindersEnabled: Bool { didSet { defaults.set(taskRemindersEnabled, forKey: "notif.tasks") } }
    var focusLiveActivityEnabled: Bool { didSet { defaults.set(focusLiveActivityEnabled, forKey: "notif.focusLiveActivity") } }
    var quietHoursStartMinute: Int { didSet { defaults.set(quietHoursStartMinute, forKey: "notif.quietStart") } }
    var quietHoursEndMinute: Int { didSet { defaults.set(quietHoursEndMinute, forKey: "notif.quietEnd") } }
    /// When on, a 20:00 nudge reminds the user to log tonight's sleep and write in their journal.
    /// Android parity: AlarmRequestType.LOG_REMINDER + the "Log sleep & journal" notification toggle.
    var logReminderEnabled: Bool { didSet { defaults.set(logReminderEnabled, forKey: "notif.logReminder") } }

    // MARK: Privacy
    /// When on, the Medication tab is gated behind Face ID / Touch ID / device passcode.
    /// Android parity: the SensitiveArea.MEDICATION app-lock gate.
    var medicationLockEnabled: Bool { didSet { defaults.set(medicationLockEnabled, forKey: "privacy.medicationLock") } }
    /// When on, sleep nights are imported read-only from HealthKit (Android: Health Connect).
    var healthKitSleepEnabled: Bool { didSet { defaults.set(healthKitSleepEnabled, forKey: "privacy.healthKitSleep") } }
    /// When on, block titles are hidden in widgets and notifications (show a placeholder instead).
    /// Off by default. Android parity: Wear redaction default OFF + "Hide sensitive titles" toggle.
    var sensitiveTitlesRedacted: Bool { didSet { defaults.set(sensitiveTitlesRedacted, forKey: "privacy.redactTitles") } }

    // MARK: AI privacy mode
    /// Which inference paths the AI assist surfaces may use. iOS ships on-device-only; the enum mirrors
    /// Android so a cloud path can graduate later. Android parity: PrivacyMode (ON_DEVICE_ONLY / CLOUD_ALLOWED / DISABLED).
    var privacyMode: PrivacyMode { didSet { defaults.set(privacyMode.rawValue, forKey: "ai.privacyMode") } }

    // MARK: Companion-app interop (Meridian / DevTime)
    /// Whether the user has consented to cross-app interop with the Meridian companion app. Off until
    /// granted in each app independently (iOS uses App-Group entitlements, not signature-pinned trust).
    var interopConsentGranted: Bool { didSet { defaults.set(interopConsentGranted, forKey: "privacy.interop.consentGranted") } }
    /// Whether medication names may be shared with the companion app. Off by default.
    /// Android parity: PrivacyPreferences.isMedicationSharingEnabled / KEY_INTEROP_SHARING_MEDICATIONS.
    var interopMedicationSharingEnabled: Bool { didSet { defaults.set(interopMedicationSharingEnabled, forKey: "privacy.interop.medicationSharing") } }

    // MARK: Privacy & Sync collapsible section state (default expanded)
    var privacyAppPermissionsExpanded: Bool { didSet { defaults.set(privacyAppPermissionsExpanded, forKey: "privacy.appPermissions.expanded") } }
    var privacySensitiveContentExpanded: Bool { didSet { defaults.set(privacySensitiveContentExpanded, forKey: "privacy.sensitiveContent.expanded") } }
    var privacyCloudSyncExpanded: Bool { didSet { defaults.set(privacyCloudSyncExpanded, forKey: "privacy.cloudSync.expanded") } }
    var privacyWearLinkExpanded: Bool { didSet { defaults.set(privacyWearLinkExpanded, forKey: "privacy.wearLink.expanded") } }
    var privacyCompanionAppExpanded: Bool { didSet { defaults.set(privacyCompanionAppExpanded, forKey: "privacy.companionApp.expanded") } }

    // MARK: Focus
    /// The user's last-selected work/break split preset, restored on the next focus session.
    var focusLastPreset: FocusSplitPreset { didSet { defaults.set(focusLastPreset.rawValue, forKey: "focus.lastPreset") } }

    // MARK: Screen Time
    /// When on, app usage is read from Screen Time to surface productive vs. wasted time in Insights.
    /// Android parity: screenTime.enabled + UsageStatsManager opt-in.
    var screenTimeEnabled: Bool { didSet { defaults.set(screenTimeEnabled, forKey: "screenTime.enabled") } }

    // MARK: Sync
    /// iCloud/CloudKit mirroring. `ChronosStore` reads the same key when building its container, so a
    /// change here applies on the next launch (Android parity: the Privacy/Sync sync toggle).
    var cloudSyncEnabled: Bool { didSet { defaults.set(cloudSyncEnabled, forKey: ChronosStore.cloudSyncKey) } }

    /// True when motion should be suppressed for the given system setting. Call as
    /// `settings.motionDisabled(systemReduceMotion)` so either the app toggle or the OS wins.
    func motionDisabled(_ systemReduceMotion: Bool) -> Bool {
        reduceMotionPreference || systemReduceMotion
    }

    /// The generation profile the planner should use, derived from the user's planning style.
    /// Mirrors Android's JSON-planner→DETERMINISTIC and the BALANCED/CREATIVE spread.
    var plannerProfile: GenerationProfile {
        switch planningStyle {
        case .focused: .deterministic
        case .balanced: .balanced
        case .flexible: .creative
        }
    }

    // MARK: Insights collapsible-card state
    //
    // The Insights/Review page persists each card's collapsed state under `insights.collapsed.<key>`
    // (Android parity: the `insights.collapsed.*` DataStore keys). The view owns its own card key list,
    // so these accessors keep ChronosSettings the single UserDefaults source of truth for that namespace.

    /// Whether the insights card named `key` is collapsed. Defaults to `false` (expanded) so newly
    /// added cards start open, matching Android's `rememberPersistentBoolean(default = false)`.
    func insightsCardCollapsed(_ key: String) -> Bool {
        defaults.boolOr("insights.collapsed.\(key)", false)
    }

    /// Persist the collapsed state for the insights card named `key`.
    func setInsightsCardCollapsed(_ key: String, _ collapsed: Bool) {
        defaults.set(collapsed, forKey: "insights.collapsed.\(key)")
    }

    // MARK: - Feature graduation plumbing (O01)

    /// Resolve a graduated feature flag's effective value at load time via ChronosCore's
    /// `FeatureGraduations`, persisting the wave's promotion marker the first time it forces the
    /// feature on. `storeKey` is the iOS UserDefaults key; `graduationKey` is the Android-parity
    /// graduated key whose wave owns the promotion marker.
    private static func resolveGraduated(_ defaults: UserDefaults, _ storeKey: String, _ graduationKey: String) -> Bool {
        let stored: Bool? = defaults.object(forKey: storeKey) == nil ? nil : defaults.bool(forKey: storeKey)
        let markerKey = FeatureGraduations.markerKey(forKey: graduationKey)
        let markerSet = markerKey.map { defaults.bool(forKey: $0) } ?? true
        let result = FeatureGraduations.graduationForFlag(stored: stored, markerSet: markerSet, defaultOn: true)
        if result.newMarker, let markerKey {
            // One-time promotion is now due: make it durable so the next read respects the stored value.
            defaults.set(true, forKey: markerKey)
        }
        return result.effective
    }

    /// Record that the user has made a deliberate choice for a graduated flag, setting its wave's
    /// promotion marker so the stored value (including an explicit `false`) is honored from now on.
    private func markPromoted(_ graduationKey: String) {
        guard let markerKey = FeatureGraduations.markerKey(forKey: graduationKey) else { return }
        defaults.set(true, forKey: markerKey)
    }
}

// MARK: - Settings enums

enum ThemeMode: String, CaseIterable, Identifiable, Sendable {
    case system, light, dark
    var id: String { rawValue }
    var label: String {
        switch self {
        case .system: "System"
        case .light: "Light"
        case .dark: "Dark"
        }
    }
    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }
}

/// The living-backdrop options (Android parity: the 7 ChronosScreenBackdrop styles).
enum BackdropStyle: String, CaseIterable, Identifiable, Sendable {
    case aurora, dawn, dusk, tide, ember, mono, off
    var id: String { rawValue }
    var label: String {
        switch self {
        case .aurora: "Aurora"
        case .dawn: "Dawn"
        case .dusk: "Dusk"
        case .tide: "Tide"
        case .ember: "Ember"
        case .mono: "Mono"
        case .off: "Off"
        }
    }
    /// The three drifting blob tints for this backdrop. `off` returns an empty set (flat).
    var blobColors: [Color] {
        switch self {
        case .aurora: [ChronosColors.brandPrimary, ChronosColors.brandSecondary, ChronosColors.brandAccent]
        case .dawn: [Color(red: 0.98, green: 0.62, blue: 0.45), Color(red: 0.95, green: 0.45, blue: 0.55), ChronosColors.brandPrimary]
        case .dusk: [Color(red: 0.36, green: 0.40, blue: 0.78), ChronosColors.brandPrimary, Color(red: 0.20, green: 0.30, blue: 0.55)]
        case .tide: [ChronosColors.brandSecondary, Color(red: 0.20, green: 0.55, blue: 0.86), Color(red: 0.30, green: 0.74, blue: 0.55)]
        case .ember: [Color(red: 0.92, green: 0.45, blue: 0.20), ChronosColors.brandAccent, Color(red: 0.86, green: 0.27, blue: 0.34)]
        case .mono: [Color(white: 0.5), Color(white: 0.6), Color(white: 0.4)]
        case .off: []
        }
    }
}

/// The day-planner personality. Maps to the on-device generation profile.
enum PlanningStyle: String, CaseIterable, Identifiable, Sendable {
    case focused, balanced, flexible
    var id: String { rawValue }
    var label: String {
        switch self {
        case .focused: "Focused"
        case .balanced: "Balanced"
        case .flexible: "Flexible"
        }
    }
    var detail: String {
        switch self {
        case .focused: "Long deep-work stretches, fewer breaks"
        case .balanced: "A steady mix of focus and recovery"
        case .flexible: "Shorter blocks, more breathing room"
        }
    }
}

/// A calendar/timeline source label, mirroring Android's `CalendarTimelineSourceLabels`
/// (SidebarPageContent.kt). The six sources the Detailed Calendar groups items under, in the same
/// sort order Android uses (Calendar, All-day calendar, then the schedule-side sources).
enum CalendarTimelineSource: String, CaseIterable, Identifiable, Sendable {
    case calendar = "Calendar"
    case allDayCalendar = "All-day calendar"
    case task = "Task"
    case habit = "Habit"
    case medication = "Medication"
    case plan = "Plan"

    var id: String { rawValue }
    var label: String { rawValue }

    /// SF Symbol used for the source header / accent, chosen to read like Android's per-source icons.
    var symbol: String {
        switch self {
        case .calendar: "calendar"
        case .allDayCalendar: "calendar.day.timeline.left"
        case .task: "checklist"
        case .habit: "repeat"
        case .medication: "pills"
        case .plan: "sparkles"
        }
    }
}

/// The three segmented presets the Android Detailed Calendar exposes (CalendarTimelinePreset).
/// `all` = all 6 sources, `schedule` = the 4 schedule-side sources, `calendar` = the 2 calendar sources.
/// (Android also has a `custom` case for manual mixes; iOS keeps the three segmented presets.)
enum CalendarTimelinePreset: String, CaseIterable, Identifiable, Sendable {
    case all, schedule, calendar

    var id: String { rawValue }
    var label: String {
        switch self {
        case .all: "All"
        case .schedule: "Schedule"
        case .calendar: "Calendar"
        }
    }

    /// The set of sources this preset surfaces — byte-aligned with Android's preset→source mapping
    /// (SidebarPageContent.kt lines 1548–1553).
    var sources: Set<CalendarTimelineSource> {
        switch self {
        case .all: Set(CalendarTimelineSource.allCases)
        case .schedule: [.task, .habit, .medication, .plan]
        case .calendar: [.calendar, .allDayCalendar]
        }
    }
}

// MARK: - UserDefaults helpers (treat missing keys as the supplied default)

private extension UserDefaults {
    func boolOr(_ key: String, _ fallback: Bool) -> Bool {
        object(forKey: key) == nil ? fallback : bool(forKey: key)
    }
    func intOr(_ key: String, _ fallback: Int) -> Int {
        object(forKey: key) == nil ? fallback : integer(forKey: key)
    }
}
