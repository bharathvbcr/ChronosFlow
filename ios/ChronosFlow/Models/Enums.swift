import Foundation

// MARK: - Domain enums ported from core/domain/model
//
// These mirror the Kotlin enums one-for-one so behaviour stays faithful across the
// Android and iOS ports. They are `Codable`/`Int`- or `String`-backed so they persist
// cleanly inside SwiftData @Model properties.

/// How free a block is to be moved by the planner. Mirrors `BlockFlexibility.kt`.
enum BlockFlexibility: String, Codable, CaseIterable, Sendable {
    case fixed
    case movable
    case resizable
    case optional
}

/// Coarse energy demand of a block. Mirrors `EnergyIntensity.kt` including its 1..5 levels.
enum EnergyIntensity: Int, Codable, CaseIterable, Sendable {
    case low = 1
    case moderate = 2
    case high = 3
    case intense = 4
    case max = 5

    static func fromLevel(_ level: Int?) -> EnergyIntensity {
        EnergyIntensity(rawValue: level ?? 2) ?? .moderate
    }

    var label: String {
        switch self {
        case .low: "Low"
        case .moderate: "Moderate"
        case .high: "High"
        case .intense: "Intense"
        case .max: "Max"
        }
    }
}

/// Where a block came from. Mirrors `BlockProvenance.kt`.
enum BlockProvenance: String, Codable, CaseIterable, Sendable {
    case manual          // user-created
    case routine         // instantiated from a Routine
    case task            // materialized from a Task
    case habit           // materialized from a Habit
    case medication      // materialized from a MedicationPlan
    case calendar        // imported from a calendar event
    case ai              // suggested by the AI planner
}

/// Lifecycle of a day's plan. Mirrors `DayPlanStatus.kt`.
enum DayPlanStatus: String, Codable, CaseIterable, Sendable {
    case draft
    case planned
    case inProgress
    case reviewing
    case completed
    case archived
}

/// Where a logged sleep night came from. Mirrors `SleepSource.kt`.
enum SleepSource: String, Codable, CaseIterable, Sendable {
    case manual
    case healthKit       // iOS-native equivalent of Android's Health Connect import
    case derived
}

/// Coarse next-day readiness derived from the most recently logged night.
/// Mirrors `SleepReadiness.kt`. `unknown` is the neutral default that no-ops the planner.
enum SleepReadiness: String, Codable, Sendable {
    case unknown
    case depleted
    case normal
    case rested
}

/// Generation tuning for the on-device language model, mirroring the Android
/// `GenerationProfile` (DETERMINISTIC/BALANCED/CREATIVE) noted in local-AI-optimization.
enum GenerationProfile: String, Codable, Sendable {
    case deterministic   // JSON planning — low temperature
    case balanced
    case creative        // chat / rewriting — higher temperature

    var temperature: Double {
        switch self {
        case .deterministic: 0.1
        case .balanced: 0.6
        case .creative: 0.9
        }
    }
}

// Journal mood (big emoji face picker, matches Android JournalMood enum)
enum JournalMood: Int, Codable, CaseIterable, Sendable {
    case unset = 0
    case awful = 1
    case sad = 2
    case neutral = 3
    case happy = 4
    case ecstatic = 5

    var emoji: String {
        switch self {
        case .unset: return ""
        case .awful: return "😞"
        case .sad: return "😕"
        case .neutral: return "😐"
        case .happy: return "😊"
        case .ecstatic: return "🤩"
        }
    }
    var label: String {
        switch self {
        case .unset: return "How are you feeling?"
        case .awful: return "Awful"
        case .sad: return "Sad"
        case .neutral: return "Neutral"
        case .happy: return "Happy"
        case .ecstatic: return "Ecstatic"
        }
    }
}

/// How the assistant is allowed to run, mirroring Android `PrivacyMode`
/// (ON_DEVICE_ONLY / CLOUD_ALLOWED / DISABLED).
///
/// On iOS the only implemented backend is on-device Foundation Models; `.cloud`
/// is a structural placeholder for a future provider layer (see N01) and is
/// treated as on-device until that path lands. `.disabled` turns the assistant off.
enum PrivacyMode: String, Codable, CaseIterable, Sendable {
    case onDeviceOnly    // ON_DEVICE_ONLY — Foundation Models only, never leaves device
    case cloudAllowed    // CLOUD_ALLOWED — structural placeholder; cloud provider is TODO
    case disabled        // DISABLED — no AI generation at all

    /// Whether any on-device generation may run for this mode. Cloud falls back
    /// to on-device until a cloud provider exists, so both allow on-device work.
    var allowsOnDeviceGeneration: Bool {
        switch self {
        case .onDeviceOnly, .cloudAllowed: true
        case .disabled: false
        }
    }

    var label: String {
        switch self {
        case .onDeviceOnly: "On-device only"
        case .cloudAllowed: "Cloud allowed"
        case .disabled: "Disabled"
        }
    }
}

// Insights section filter (mirrors Android InsightsSection enum for section filter pills)
enum InsightsSection: String, CaseIterable, Identifiable, Sendable {
    case execution, habits, sleep, mood, medications
    var id: String { rawValue }
    var label: String {
        switch self {
        case .execution: return "Execution"
        case .habits: return "Habits"
        case .sleep: return "Sleep"
        case .mood: return "Mood"
        case .medications: return "Medications"
        }
    }
    var systemImage: String {
        switch self {
        case .execution: return "chart.bar.fill"
        case .habits: return "heart.fill"
        case .sleep: return "moon.zzz.fill"
        case .mood: return "face.smiling"
        case .medications: return "pills.fill"
        }
    }
}
