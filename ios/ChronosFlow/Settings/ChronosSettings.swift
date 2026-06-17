import SwiftUI
import Observation

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

        // Feature flags (graduated on-by-default, matching Android's FeatureGraduations).
        habitsEnabled = defaults.boolOr("flag.habits", true)
        goalsEnabled = defaults.boolOr("flag.goals", true)
        medicationEnabled = defaults.boolOr("flag.medication", true)
        journalEnabled = defaults.boolOr("flag.journal", true)
        sleepEnabled = defaults.boolOr("flag.sleep", true)
        routinesEnabled = defaults.boolOr("flag.routines", true)
        insightsEnabled = defaults.boolOr("flag.insights", true)

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

        // Notifications.
        remindersEnabled = defaults.boolOr("notif.reminders", true)
        medicationRemindersEnabled = defaults.boolOr("notif.medication", true)
        habitRemindersEnabled = defaults.boolOr("notif.habits", true)
        taskRemindersEnabled = defaults.boolOr("notif.tasks", true)
        focusLiveActivityEnabled = defaults.boolOr("notif.focusLiveActivity", true)
        quietHoursStartMinute = defaults.intOr("notif.quietStart", 22 * 60)
        quietHoursEndMinute = defaults.intOr("notif.quietEnd", 7 * 60)

        // Privacy.
        medicationLockEnabled = defaults.boolOr("privacy.medicationLock", true)
        healthKitSleepEnabled = defaults.boolOr("privacy.healthKitSleep", false)

        // Sync. Shares the key ChronosStore reads at container-build time; takes effect next launch.
        cloudSyncEnabled = defaults.boolOr(ChronosStore.cloudSyncKey, false)
    }

    // MARK: Feature flags
    var habitsEnabled: Bool { didSet { defaults.set(habitsEnabled, forKey: "flag.habits") } }
    var goalsEnabled: Bool { didSet { defaults.set(goalsEnabled, forKey: "flag.goals") } }
    var medicationEnabled: Bool { didSet { defaults.set(medicationEnabled, forKey: "flag.medication") } }
    var journalEnabled: Bool { didSet { defaults.set(journalEnabled, forKey: "flag.journal") } }
    var sleepEnabled: Bool { didSet { defaults.set(sleepEnabled, forKey: "flag.sleep") } }
    var routinesEnabled: Bool { didSet { defaults.set(routinesEnabled, forKey: "flag.routines") } }
    var insightsEnabled: Bool { didSet { defaults.set(insightsEnabled, forKey: "flag.insights") } }

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

    // MARK: Notifications
    var remindersEnabled: Bool { didSet { defaults.set(remindersEnabled, forKey: "notif.reminders") } }
    var medicationRemindersEnabled: Bool { didSet { defaults.set(medicationRemindersEnabled, forKey: "notif.medication") } }
    var habitRemindersEnabled: Bool { didSet { defaults.set(habitRemindersEnabled, forKey: "notif.habits") } }
    var taskRemindersEnabled: Bool { didSet { defaults.set(taskRemindersEnabled, forKey: "notif.tasks") } }
    var focusLiveActivityEnabled: Bool { didSet { defaults.set(focusLiveActivityEnabled, forKey: "notif.focusLiveActivity") } }
    var quietHoursStartMinute: Int { didSet { defaults.set(quietHoursStartMinute, forKey: "notif.quietStart") } }
    var quietHoursEndMinute: Int { didSet { defaults.set(quietHoursEndMinute, forKey: "notif.quietEnd") } }

    // MARK: Privacy
    /// When on, the Medication tab is gated behind Face ID / Touch ID / device passcode.
    /// Android parity: the SensitiveArea.MEDICATION app-lock gate.
    var medicationLockEnabled: Bool { didSet { defaults.set(medicationLockEnabled, forKey: "privacy.medicationLock") } }
    /// When on, sleep nights are imported read-only from HealthKit (Android: Health Connect).
    var healthKitSleepEnabled: Bool { didSet { defaults.set(healthKitSleepEnabled, forKey: "privacy.healthKitSleep") } }

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

// MARK: - UserDefaults helpers (treat missing keys as the supplied default)

private extension UserDefaults {
    func boolOr(_ key: String, _ fallback: Bool) -> Bool {
        object(forKey: key) == nil ? fallback : bool(forKey: key)
    }
    func intOr(_ key: String, _ fallback: Int) -> Int {
        object(forKey: key) == nil ? fallback : integer(forKey: key)
    }
}
