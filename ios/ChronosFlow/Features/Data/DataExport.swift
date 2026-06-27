import Foundation
import SwiftData
import UniformTypeIdentifiers
import SwiftUI
import ChronosCore

// Backup / restore — the iOS analogue of the Android full-export importer + restore UI. Serializes
// the whole SwiftData store to a single JSON document and restores it back, so users can move their
// data between devices without relying on iCloud.
//
// Format alignment (BK01): every export carries Android's `exportKind` / `formatVersion` markers
// (BackupFormat.fullDataKind / .currentVersion) plus a `schema` map of table → column names so the
// shared ChronosCore `BackupImportValidator` can reason about an export written by EITHER platform.
// The typed entity arrays remain the canonical iOS payload; the schema/markers make the document
// self-describing and forward/backward tolerant. Restore is non-destructive by default (BK04):
// per-entity empty guards via `BackupImportValidator.shouldFill`, so a device-transfer never clobbers
// live data.

/// Versioned snapshot of every entity. New fields should stay optional for forward compatibility.
struct ChronosBackup: Codable {
    /// Android `exportKind` marker — identifies this as a ChronosFlow full-data export so the shared
    /// validator (and a future Android importer) recognises it. Defaults to the cross-platform constant.
    var formatKind: String = BackupFormat.fullDataKind
    /// Android `formatVersion` (currently 2). Tolerated range is `BackupFormat.supportedVersions`.
    var formatVersion: Int = BackupFormat.currentVersion
    /// Legacy iOS app-format field, kept for backward compatibility with pre-BK01 exports.
    var version = 2
    var exportedAt = Date()
    /// Self-describing schema: table name → its column names (Android `tableSchemas` analogue). Lets
    /// `BackupImportValidator` perform column-intersection matching and per-table empty guards without
    /// hard-coding the entity list. Optional so a pre-BK01 export still decodes.
    var schema: [String: [String]]? = ChronosBackup.currentSchema
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
        /// Task attachments (BK03). Mirrors Android `Task.attachments`. Optional + `decodeIfPresent`
        /// so a pre-BK03 export (no key) decodes as `nil` (treated as empty), and a cross-platform
        /// export decodes the shared `AttachmentDTO` unchanged.
        var attachments: [AttachmentDTO]? = nil
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

    // MARK: - Table names (BK01 schema map / validator keys)

    /// Stable table names keyed by the schema map and used by the shared `BackupImportValidator`
    /// `LiveSchema`. These are the iOS contract names (one per @Model entity array); they only need to
    /// be internally consistent between the schema we export and the `LiveSchema` we build at restore.
    enum Table {
        static let blocks = "blocks"
        static let tasks = "tasks"
        static let habits = "habits"
        static let goals = "goals"
        static let medications = "medications"
        static let journal = "journal"
        static let sleep = "sleep"
        static let checkIns = "checkIns"
        static let routines = "routines"
    }

    /// Column names per table, derived from the DTO field names. Emitted as the export `schema` so the
    /// document is self-describing (Android `tableSchemas` analogue) and `BackupImportValidator` can do
    /// column-intersection matching. Hand-maintained alongside the DTOs (no runtime reflection).
    static let currentSchema: [String: [String]] = [
        Table.blocks: ["id", "date", "title", "category", "start", "duration", "provenance",
                       "flexibility", "energy", "isLocked", "taskID", "habitID", "goalID"],
        Table.tasks: ["id", "title", "detail", "isCompleted", "priority", "dueDate", "targetDate",
                      "goalID", "recurrence", "checklist", "attachments"],
        Table.habits: ["id", "title", "cadence", "windowStart", "windowEnd", "difficulty", "streak",
                       "isActive", "goalID", "completions"],
        Table.goals: ["id", "title", "detail", "category", "target", "startDate", "targetDate",
                      "progress", "isCompleted"],
        Table.medications: ["id", "name", "dosage", "unit", "notes", "reminderMinutes", "takeWithFood",
                            "isActive", "takenAt", "refillNeededAfterDoses", "remainingDoses"],
        Table.journal: ["id", "date", "body", "isPrimary"],
        Table.sleep: ["id", "date", "actualStart", "actualEnd", "quality", "interrupted", "source"],
        Table.checkIns: ["id", "mood", "stress", "energy", "focus", "notes", "recordedAt", "date"],
        Table.routines: ["id", "title", "isActive", "steps"],
    ]

    /// Per-table row counts in this snapshot (for nothing-to-import / merge decisions).
    var rowCountByTable: [String: Int] {
        [
            Table.blocks: blocks.count, Table.tasks: tasks.count, Table.habits: habits.count,
            Table.goals: goals.count, Table.medications: medications.count, Table.journal: journal.count,
            Table.sleep: sleep.count, Table.checkIns: checkIns.count, Table.routines: routines.count,
        ]
    }

    init() {}

    private enum CodingKeys: String, CodingKey {
        case formatKind, formatVersion, version, exportedAt, schema
        case blocks, tasks, habits, goals, medications, journal, sleep, checkIns, routines
    }

    /// Tolerant decode: pre-BK01 exports lack `formatKind` / `formatVersion` / `schema`, and any entity
    /// array may be absent. Missing keys fall back to defaults so older backups still restore.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        formatKind = try c.decodeIfPresent(String.self, forKey: .formatKind) ?? BackupFormat.fullDataKind
        formatVersion = try c.decodeIfPresent(Int.self, forKey: .formatVersion) ?? BackupFormat.currentVersion
        version = try c.decodeIfPresent(Int.self, forKey: .version) ?? 2
        exportedAt = try c.decodeIfPresent(Date.self, forKey: .exportedAt) ?? Date()
        schema = try c.decodeIfPresent([String: [String]].self, forKey: .schema)
        blocks = try c.decodeIfPresent([BlockDTO].self, forKey: .blocks) ?? []
        tasks = try c.decodeIfPresent([TaskDTO].self, forKey: .tasks) ?? []
        habits = try c.decodeIfPresent([HabitDTO].self, forKey: .habits) ?? []
        goals = try c.decodeIfPresent([GoalDTO].self, forKey: .goals) ?? []
        medications = try c.decodeIfPresent([MedicationDTO].self, forKey: .medications) ?? []
        journal = try c.decodeIfPresent([JournalDTO].self, forKey: .journal) ?? []
        sleep = try c.decodeIfPresent([SleepDTO].self, forKey: .sleep) ?? []
        checkIns = try c.decodeIfPresent([CheckInDTO].self, forKey: .checkIns) ?? []
        routines = try c.decodeIfPresent([RoutineDTO].self, forKey: .routines) ?? []
    }
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
                  recurrence: $0.recurrence, checklist: $0.checklist,
                  // BK03: round-trip task attachments. `nil` (no attachments) stays nil so the key is
                  // omitted, matching Android's default-empty-list semantics.
                  attachments: $0.attachments)
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

    /// Outcome of a restore: which entity tables were filled, how many rows, and which were left alone
    /// because they already held data (only ever populated in `.mergeIfEmpty` mode).
    struct RestoreSummary {
        var filledTables: [String] = []
        var insertedRows = 0
        var skippedNonEmptyTables: [String] = []
        var nothingRestored: Bool { insertedRows == 0 }
    }

    /// Build the live `LiveSchema` for the validator from the current store: every iOS table is "known"
    /// (its columns come from `currentSchema`), and a table is "non-empty" when it currently holds any
    /// row. This lets the shared `BackupImportValidator.shouldFill` make the same per-table empty-guard
    /// decision the Android importer makes.
    @MainActor
    static func liveSchema(in context: ModelContext) -> LiveSchema {
        func has<T: PersistentModel>(_ type: T.Type) -> Bool {
            var desc = FetchDescriptor<T>(); desc.fetchLimit = 1
            return ((try? context.fetch(desc))?.isEmpty == false)
        }
        var nonEmpty = Set<String>()
        if has(TimeBlock.self)          { nonEmpty.insert(ChronosBackup.Table.blocks) }
        if has(TaskItem.self)           { nonEmpty.insert(ChronosBackup.Table.tasks) }
        if has(Habit.self)              { nonEmpty.insert(ChronosBackup.Table.habits) }
        if has(Goal.self)               { nonEmpty.insert(ChronosBackup.Table.goals) }
        if has(MedicationPlan.self)     { nonEmpty.insert(ChronosBackup.Table.medications) }
        if has(JournalEntry.self)       { nonEmpty.insert(ChronosBackup.Table.journal) }
        if has(SleepTrack.self)         { nonEmpty.insert(ChronosBackup.Table.sleep) }
        if has(MoodEnergyCheckIn.self)  { nonEmpty.insert(ChronosBackup.Table.checkIns) }
        if has(Routine.self)            { nonEmpty.insert(ChronosBackup.Table.routines) }
        let columns = ChronosBackup.currentSchema.mapValues { Set($0) }
        return LiveSchema(columnsByTable: columns, nonEmptyTables: nonEmpty)
    }

    /// Restore a backup into the store.
    ///
    /// - `.destructive` (default): wipe every entity then insert the backup — the explicit
    ///   "replace all data" flow the Data screen confirms first.
    /// - `.mergeIfEmpty`: per-entity empty guard — only fill tables that are currently empty, never
    ///   overwrite live data. Used by device-transfer / startup restore. Each entity's fill decision
    ///   comes from the shared `BackupImportValidator.shouldFill`, mirroring Android
    ///   `ChronosDataImportRepository.importIntoEmptyTables`.
    @MainActor
    @discardableResult
    static func restore(
        _ backup: ChronosBackup,
        into context: ModelContext,
        mode: RestoreMode = .destructive
    ) -> RestoreSummary {
        let schema = liveSchema(in: context)
        var summary = RestoreSummary()

        // Per-table fill decision from the shared validator: always true in `.destructive`, true only
        // for currently-empty known tables in `.mergeIfEmpty`.
        func fills(_ table: String) -> Bool {
            BackupImportValidator.shouldFill(table: table, mode: mode, schema: schema)
        }

        // In destructive mode, wipe everything first (shouldFill is always true). In mergeIfEmpty we
        // never delete; non-empty tables are simply not refilled.
        if mode == .destructive {
            for block in (try? context.fetch(FetchDescriptor<TimeBlock>())) ?? [] { context.delete(block) }
            for t in (try? context.fetch(FetchDescriptor<TaskItem>())) ?? [] { context.delete(t) }
            for h in (try? context.fetch(FetchDescriptor<Habit>())) ?? [] { context.delete(h) }
            for g in (try? context.fetch(FetchDescriptor<Goal>())) ?? [] { context.delete(g) }
            for m in (try? context.fetch(FetchDescriptor<MedicationPlan>())) ?? [] { context.delete(m) }
            for j in (try? context.fetch(FetchDescriptor<JournalEntry>())) ?? [] { context.delete(j) }
            for s in (try? context.fetch(FetchDescriptor<SleepTrack>())) ?? [] { context.delete(s) }
            for c in (try? context.fetch(FetchDescriptor<MoodEnergyCheckIn>())) ?? [] { context.delete(c) }
            for r in (try? context.fetch(FetchDescriptor<Routine>())) ?? [] { context.delete(r) }
        }

        // Helper to apply one table's rows under the fill guard, recording the summary.
        func applyTable<Row>(_ name: String, _ rows: [Row], _ insert: (Row) -> Void) {
            guard fills(name) else {
                if !rows.isEmpty { summary.skippedNonEmptyTables.append(name) }
                return
            }
            guard !rows.isEmpty else { return }
            rows.forEach(insert)
            summary.filledTables.append(name)
            summary.insertedRows += rows.count
        }

        applyTable(ChronosBackup.Table.blocks, backup.blocks) {
            context.insert(TimeBlock(id: $0.id, date: $0.date, title: $0.title, category: $0.category,
                startMinuteOfDay: $0.start, durationMinutes: $0.duration, provenance: $0.provenance,
                flexibility: $0.flexibility, energyLevel: EnergyIntensity(rawValue: $0.energy) ?? .moderate,
                taskID: $0.taskID, habitID: $0.habitID, goalID: $0.goalID, isLocked: $0.isLocked))
        }
        applyTable(ChronosBackup.Table.tasks, backup.tasks) {
            context.insert(TaskItem(id: $0.id, title: $0.title, detail: $0.detail, isCompleted: $0.isCompleted,
                priority: $0.priority, dueDate: $0.dueDate, targetDate: $0.targetDate, goalID: $0.goalID,
                recurrence: $0.recurrence, checklist: $0.checklist,
                // BK03: restore task attachments (nil/empty stays empty, matching Android default).
                attachments: $0.attachments))
        }
        applyTable(ChronosBackup.Table.habits, backup.habits) {
            context.insert(Habit(id: $0.id, title: $0.title, cadence: $0.cadence, windowStartMinute: $0.windowStart,
                windowEndMinute: $0.windowEnd, difficulty: $0.difficulty, streakCount: $0.streak,
                isActive: $0.isActive, goalID: $0.goalID, completionDates: $0.completions))
        }
        applyTable(ChronosBackup.Table.goals, backup.goals) {
            context.insert(Goal(id: $0.id, title: $0.title, detail: $0.detail, category: $0.category,
                targetValue: $0.target, startDate: $0.startDate, targetDate: $0.targetDate,
                progressValue: $0.progress, isCompleted: $0.isCompleted))
        }
        applyTable(ChronosBackup.Table.medications, backup.medications) {
            context.insert(MedicationPlan(id: $0.id, name: $0.name, dosage: $0.dosage, unit: $0.unit,
                notes: $0.notes, reminderMinuteOfDay: $0.reminderMinutes.first ?? 8 * 60,
                takeWithFood: $0.takeWithFood, refillNeededAfterDoses: $0.refillNeededAfterDoses,
                remainingDoses: $0.remainingDoses, isActive: $0.isActive,
                reminderMinutes: $0.reminderMinutes,
                doseEvents: $0.takenAt.map { DoseEvent(date: $0, status: .taken) }))
        }
        applyTable(ChronosBackup.Table.journal, backup.journal) {
            context.insert(JournalEntry(id: $0.id, entryDate: $0.date, body: $0.body, isPrimary: $0.isPrimary))
        }
        applyTable(ChronosBackup.Table.sleep, backup.sleep) {
            context.insert(SleepTrack(id: $0.id, date: $0.date, actualStartMinute: $0.actualStart,
                actualEndMinute: $0.actualEnd, sleepQuality: $0.quality, interruptedCount: $0.interrupted,
                source: $0.source))
        }
        applyTable(ChronosBackup.Table.checkIns, backup.checkIns) {
            context.insert(MoodEnergyCheckIn(id: $0.id, moodScore: $0.mood, stressScore: $0.stress,
                energyScore: $0.energy, focusScore: $0.focus, notes: $0.notes,
                recordedAt: $0.recordedAt, checkInDate: $0.date))
        }
        applyTable(ChronosBackup.Table.routines, backup.routines) {
            context.insert(Routine(id: $0.id, title: $0.title, isActive: $0.isActive, steps: $0.steps))
        }
        try? context.save()
        return summary
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
