import Foundation
import SwiftData

/// A completed or active focus (Pomodoro) session. Ported from `FocusSession.kt`.
@Model
final class FocusSession {
    @Attribute(.unique) var id: String
    var blockID: String?
    var date: Date
    var plannedDurationMinutes: Int
    var actualDurationMinutes: Int?
    var interruptions: Int
    var startedAt: Date?
    var completedAt: Date?
    var notes: String?
    var isCompleted: Bool

    init(
        id: String = UUID().uuidString,
        blockID: String? = nil,
        date: Date = .now,
        plannedDurationMinutes: Int = 25,
        actualDurationMinutes: Int? = nil,
        interruptions: Int = 0,
        startedAt: Date? = nil,
        completedAt: Date? = nil,
        notes: String? = nil,
        isCompleted: Bool = false
    ) {
        self.id = id
        self.blockID = blockID
        self.date = Calendar.current.startOfDay(for: date)
        self.plannedDurationMinutes = plannedDurationMinutes
        self.actualDurationMinutes = actualDurationMinutes
        self.interruptions = interruptions
        self.startedAt = startedAt
        self.completedAt = completedAt
        self.notes = notes
        self.isCompleted = isCompleted
    }
}

/// One daily reflection entry. Ported from `JournalEntry.kt`.
@Model
final class JournalEntry {
    @Attribute(.unique) var id: String
    var entryDate: Date
    var createdAt: Date
    var updatedAt: Date
    var body: String
    var promptType: String?
    var moodCheckInID: String?
    var isPrimary: Bool
    /// 0 = unset; 1=awful 2=sad 3=neutral 4=happy 5=ecstatic. Mirrors Android `moodRating`.
    var moodRating: Int
    /// Local file URLs for photos attached to this entry. Mirrors Android `photoUris`.
    var photoUris: [String]
    /// Which rotating prompt key is active for this entry. Mirrors Android `shuffledPromptKey`.
    var shuffledPromptKey: String?
    /// True when this entry was imported from HealthKit/workout data. Mirrors Android `isWorkoutEntry`.
    var isWorkoutEntry: Bool
    /// The user's journal streak on this day, populated at save time. Mirrors Android `streakDay`.
    var streakDay: Int

    init(
        id: String = UUID().uuidString,
        entryDate: Date = .now,
        createdAt: Date = .now,
        updatedAt: Date = .now,
        body: String,
        promptType: String? = nil,
        moodCheckInID: String? = nil,
        isPrimary: Bool = true,
        moodRating: Int = 0,
        photoUris: [String] = [],
        shuffledPromptKey: String? = nil,
        isWorkoutEntry: Bool = false,
        streakDay: Int = 0
    ) {
        self.id = id
        self.entryDate = Calendar.current.startOfDay(for: entryDate)
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.body = body
        self.promptType = promptType
        self.moodCheckInID = moodCheckInID
        self.isPrimary = isPrimary
        self.moodRating = moodRating
        self.photoUris = photoUris
        self.shuffledPromptKey = shuffledPromptKey
        self.isWorkoutEntry = isWorkoutEntry
        self.streakDay = streakDay
    }
}

/// A logged sleep night. Ported from `SleepTrack.kt`.
@Model
final class SleepTrack {
    @Attribute(.unique) var id: String
    var date: Date
    var plannedStartMinute: Int?
    var plannedEndMinute: Int?
    var actualStartMinute: Int?
    var actualEndMinute: Int?
    /// 0 = unrated, 1..5 otherwise.
    var sleepQuality: Int
    var windDownNotes: String?
    var interruptedCount: Int
    var source: SleepSource

    init(
        id: String = UUID().uuidString,
        date: Date = .now,
        plannedStartMinute: Int? = nil,
        plannedEndMinute: Int? = nil,
        actualStartMinute: Int? = nil,
        actualEndMinute: Int? = nil,
        sleepQuality: Int = 0,
        windDownNotes: String? = nil,
        interruptedCount: Int = 0,
        source: SleepSource = .manual
    ) {
        self.id = id
        self.date = Calendar.current.startOfDay(for: date)
        self.plannedStartMinute = plannedStartMinute
        self.plannedEndMinute = plannedEndMinute
        self.actualStartMinute = actualStartMinute
        self.actualEndMinute = actualEndMinute
        self.sleepQuality = sleepQuality
        self.windDownNotes = windDownNotes
        self.interruptedCount = interruptedCount
        self.source = source
    }

    /// Measured sleep length in minutes, wrapping past midnight. Mirrors `sleepDurationMinutes`.
    var durationMinutes: Int? {
        guard let start = actualStartMinute, let end = actualEndMinute else { return nil }
        let span = end - start
        return span >= 0 ? span : span + 1440
    }
}

/// A quick mood / energy / stress / focus check-in. Ported from `MoodEnergyCheckIn.kt`.
@Model
final class MoodEnergyCheckIn {
    @Attribute(.unique) var id: String
    var blockID: String?
    var moodScore: Int
    var stressScore: Int
    var energyScore: Int
    var focusScore: Int
    var notes: String?
    var recordedAt: Date
    var checkInDate: Date

    init(
        id: String = UUID().uuidString,
        blockID: String? = nil,
        moodScore: Int = 3,
        stressScore: Int = 3,
        energyScore: Int = 3,
        focusScore: Int = 3,
        notes: String? = nil,
        recordedAt: Date = .now,
        checkInDate: Date = .now
    ) {
        self.id = id
        self.blockID = blockID
        self.moodScore = moodScore
        self.stressScore = stressScore
        self.energyScore = energyScore
        self.focusScore = focusScore
        self.notes = notes
        self.recordedAt = recordedAt
        self.checkInDate = Calendar.current.startOfDay(for: checkInDate)
    }
}

/// A reusable bundle of block definitions applied to any date. Ported from `Routine.kt`.
@Model
final class Routine {
    @Attribute(.unique) var id: String
    var title: String
    var isActive: Bool
    var lastCompletedDate: Date?
    var steps: [RoutineStep]

    init(
        id: String = UUID().uuidString,
        title: String,
        isActive: Bool = true,
        lastCompletedDate: Date? = nil,
        steps: [RoutineStep] = []
    ) {
        self.id = id
        self.title = title
        self.isActive = isActive
        self.lastCompletedDate = lastCompletedDate
        self.steps = steps
    }
}

/// One step of a routine, instantiated as a TimeBlock at `offsetMinute` past the chosen start.
/// Mirrors `RoutineStep`.
struct RoutineStep: Codable, Hashable, Identifiable, Sendable {
    var id: String = UUID().uuidString
    var title: String
    var category: String = "ROUTINE"
    var offsetMinute: Int
    var durationMinutes: Int
    var energyLevel: Int = 2
}
