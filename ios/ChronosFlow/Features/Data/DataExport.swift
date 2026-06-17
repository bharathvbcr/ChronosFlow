import Foundation
import SwiftData
import UniformTypeIdentifiers
import SwiftUI

// Backup / restore — the iOS analogue of the Android full-export importer + restore UI. Serializes
// the whole SwiftData store to a single JSON document and restores it back, so users can move their
// data between devices without relying on iCloud.

/// Versioned snapshot of every entity. New fields should stay optional for forward compatibility.
struct ChronosBackup: Codable {
    var version = 2
    var exportedAt = Date()
    var blocks: [BlockDTO] = []
    var tasks: [TaskDTO] = []
    var habits: [HabitDTO] = []
    var goals: [GoalDTO] = []
    var medications: [MedicationDTO] = []
    var journal: [JournalDTO] = []
    var sleep: [SleepDTO] = []
    var checkIns: [CheckInDTO] = []
    var routines: [RoutineDTO] = []

    struct BlockDTO: Codable {
        var id: String; var date: Date; var title: String; var category: String
        var start: Int; var duration: Int; var provenance: BlockProvenance; var flexibility: BlockFlexibility
        var energy: Int; var isLocked: Bool; var taskID: String?; var habitID: String?; var goalID: String?
    }
    struct TaskDTO: Codable {
        var id: String; var title: String; var detail: String?; var isCompleted: Bool; var priority: Int
        var dueDate: Date?; var targetDate: Date?; var goalID: String?; var recurrence: RecurrenceSpec?
        var checklist: [ChecklistItem]
    }
    struct HabitDTO: Codable {
        var id: String; var title: String; var cadence: String; var windowStart: Int; var windowEnd: Int
        var difficulty: Int; var streak: Int; var isActive: Bool; var goalID: String?; var completions: [Date]
    }
    struct GoalDTO: Codable {
        var id: String; var title: String; var detail: String?; var category: String; var target: Int
        var startDate: Date; var targetDate: Date?; var progress: Int; var isCompleted: Bool
    }
    struct MedicationDTO: Codable {
        var id: String; var name: String; var dosage: String; var unit: String; var notes: String?
        var reminderMinutes: [Int]; var takeWithFood: Bool; var isActive: Bool; var takenAt: [Date]
        var refillNeededAfterDoses: Int?; var remainingDoses: Int?
    }
    struct JournalDTO: Codable { var id: String; var date: Date; var body: String; var isPrimary: Bool }
    struct SleepDTO: Codable {
        var id: String; var date: Date; var actualStart: Int?; var actualEnd: Int?; var quality: Int
        var interrupted: Int; var source: SleepSource
    }
    struct CheckInDTO: Codable {
        var id: String; var mood: Int; var stress: Int; var energy: Int; var focus: Int
        var notes: String?; var recordedAt: Date; var date: Date
    }
    struct RoutineDTO: Codable { var id: String; var title: String; var isActive: Bool; var steps: [RoutineStep] }
}

enum ChronosBackupService {
    @MainActor
    static func export(from context: ModelContext) -> ChronosBackup {
        var backup = ChronosBackup()
        backup.blocks = (try? context.fetch(FetchDescriptor<TimeBlock>()))?.map {
            .init(id: $0.id, date: $0.date, title: $0.title, category: $0.category,
                  start: $0.startMinuteOfDay, duration: $0.durationMinutes, provenance: $0.provenance,
                  flexibility: $0.flexibility, energy: $0.energyLevel.rawValue, isLocked: $0.isLocked,
                  taskID: $0.taskID, habitID: $0.habitID, goalID: $0.goalID)
        } ?? []
        backup.tasks = (try? context.fetch(FetchDescriptor<TaskItem>()))?.map {
            .init(id: $0.id, title: $0.title, detail: $0.detail, isCompleted: $0.isCompleted,
                  priority: $0.priority, dueDate: $0.dueDate, targetDate: $0.targetDate, goalID: $0.goalID,
                  recurrence: $0.recurrence, checklist: $0.checklist)
        } ?? []
        backup.habits = (try? context.fetch(FetchDescriptor<Habit>()))?.map {
            .init(id: $0.id, title: $0.title, cadence: $0.cadence, windowStart: $0.windowStartMinute,
                  windowEnd: $0.windowEndMinute, difficulty: $0.difficulty, streak: $0.streakCount,
                  isActive: $0.isActive, goalID: $0.goalID, completions: $0.completionDates)
        } ?? []
        backup.goals = (try? context.fetch(FetchDescriptor<Goal>()))?.map {
            .init(id: $0.id, title: $0.title, detail: $0.detail, category: $0.category, target: $0.targetValue,
                  startDate: $0.startDate, targetDate: $0.targetDate, progress: $0.progressValue, isCompleted: $0.isCompleted)
        } ?? []
        backup.medications = (try? context.fetch(FetchDescriptor<MedicationPlan>()))?.map {
            .init(id: $0.id, name: $0.name, dosage: $0.dosage, unit: $0.unit, notes: $0.notes,
                  reminderMinutes: $0.reminderMinutes, takeWithFood: $0.takeWithFood, isActive: $0.isActive,
                  takenAt: $0.takenAt, refillNeededAfterDoses: $0.refillNeededAfterDoses, remainingDoses: $0.remainingDoses)
        } ?? []
        backup.journal = (try? context.fetch(FetchDescriptor<JournalEntry>()))?.map {
            .init(id: $0.id, date: $0.entryDate, body: $0.body, isPrimary: $0.isPrimary)
        } ?? []
        backup.sleep = (try? context.fetch(FetchDescriptor<SleepTrack>()))?.map {
            .init(id: $0.id, date: $0.date, actualStart: $0.actualStartMinute, actualEnd: $0.actualEndMinute,
                  quality: $0.sleepQuality, interrupted: $0.interruptedCount, source: $0.source)
        } ?? []
        backup.checkIns = (try? context.fetch(FetchDescriptor<MoodEnergyCheckIn>()))?.map {
            .init(id: $0.id, mood: $0.moodScore, stress: $0.stressScore, energy: $0.energyScore,
                  focus: $0.focusScore, notes: $0.notes, recordedAt: $0.recordedAt, date: $0.checkInDate)
        } ?? []
        backup.routines = (try? context.fetch(FetchDescriptor<Routine>()))?.map {
            .init(id: $0.id, title: $0.title, isActive: $0.isActive, steps: $0.steps)
        } ?? []
        return backup
    }

    /// Restore replaces the store with the backup's contents (deletes existing first).
    @MainActor
    static func restore(_ backup: ChronosBackup, into context: ModelContext) {
        for block in (try? context.fetch(FetchDescriptor<TimeBlock>())) ?? [] { context.delete(block) }
        for t in (try? context.fetch(FetchDescriptor<TaskItem>())) ?? [] { context.delete(t) }
        for h in (try? context.fetch(FetchDescriptor<Habit>())) ?? [] { context.delete(h) }
        for g in (try? context.fetch(FetchDescriptor<Goal>())) ?? [] { context.delete(g) }
        for m in (try? context.fetch(FetchDescriptor<MedicationPlan>())) ?? [] { context.delete(m) }
        for j in (try? context.fetch(FetchDescriptor<JournalEntry>())) ?? [] { context.delete(j) }
        for s in (try? context.fetch(FetchDescriptor<SleepTrack>())) ?? [] { context.delete(s) }
        for c in (try? context.fetch(FetchDescriptor<MoodEnergyCheckIn>())) ?? [] { context.delete(c) }
        for r in (try? context.fetch(FetchDescriptor<Routine>())) ?? [] { context.delete(r) }

        backup.blocks.forEach {
            context.insert(TimeBlock(id: $0.id, date: $0.date, title: $0.title, category: $0.category,
                startMinuteOfDay: $0.start, durationMinutes: $0.duration, provenance: $0.provenance,
                flexibility: $0.flexibility, energyLevel: EnergyIntensity(rawValue: $0.energy) ?? .moderate,
                taskID: $0.taskID, habitID: $0.habitID, goalID: $0.goalID, isLocked: $0.isLocked))
        }
        backup.tasks.forEach {
            context.insert(TaskItem(id: $0.id, title: $0.title, detail: $0.detail, isCompleted: $0.isCompleted,
                priority: $0.priority, dueDate: $0.dueDate, targetDate: $0.targetDate, goalID: $0.goalID,
                recurrence: $0.recurrence, checklist: $0.checklist))
        }
        backup.habits.forEach {
            context.insert(Habit(id: $0.id, title: $0.title, cadence: $0.cadence, windowStartMinute: $0.windowStart,
                windowEndMinute: $0.windowEnd, difficulty: $0.difficulty, streakCount: $0.streak,
                isActive: $0.isActive, goalID: $0.goalID, completionDates: $0.completions))
        }
        backup.goals.forEach {
            context.insert(Goal(id: $0.id, title: $0.title, detail: $0.detail, category: $0.category,
                targetValue: $0.target, startDate: $0.startDate, targetDate: $0.targetDate,
                progressValue: $0.progress, isCompleted: $0.isCompleted))
        }
        backup.medications.forEach {
            context.insert(MedicationPlan(id: $0.id, name: $0.name, dosage: $0.dosage, unit: $0.unit,
                notes: $0.notes, reminderMinuteOfDay: $0.reminderMinutes.first ?? 8 * 60,
                takeWithFood: $0.takeWithFood, refillNeededAfterDoses: $0.refillNeededAfterDoses,
                remainingDoses: $0.remainingDoses, isActive: $0.isActive,
                reminderMinutes: $0.reminderMinutes, takenAt: $0.takenAt))
        }
        backup.journal.forEach {
            context.insert(JournalEntry(id: $0.id, entryDate: $0.date, body: $0.body, isPrimary: $0.isPrimary))
        }
        backup.sleep.forEach {
            context.insert(SleepTrack(id: $0.id, date: $0.date, actualStartMinute: $0.actualStart,
                actualEndMinute: $0.actualEnd, sleepQuality: $0.quality, interruptedCount: $0.interrupted,
                source: $0.source))
        }
        backup.checkIns.forEach {
            context.insert(MoodEnergyCheckIn(id: $0.id, moodScore: $0.mood, stressScore: $0.stress,
                energyScore: $0.energy, focusScore: $0.focus, notes: $0.notes,
                recordedAt: $0.recordedAt, checkInDate: $0.date))
        }
        backup.routines.forEach {
            context.insert(Routine(id: $0.id, title: $0.title, isActive: $0.isActive, steps: $0.steps))
        }
        try? context.save()
    }
}

/// A JSON document for `fileExporter` / `fileImporter`.
struct ChronosBackupDocument: FileDocument {
    static let readableContentTypes: [UTType] = [.json]
    var backup: ChronosBackup

    init(backup: ChronosBackup) { self.backup = backup }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents else { throw CocoaError(.fileReadCorruptFile) }
        let decoder = JSONDecoder(); decoder.dateDecodingStrategy = .iso8601
        backup = try decoder.decode(ChronosBackup.self, from: data)
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        let encoder = JSONEncoder(); encoder.dateEncodingStrategy = .iso8601; encoder.outputFormatting = [.prettyPrinted]
        return FileWrapper(regularFileWithContents: try encoder.encode(backup))
    }
}
