import Foundation
import Observation

// MARK: - Task templates
//
// The iOS port of the Android add-task modal's reusable templates (feature/tasks TaskTemplate.kt +
// TaskFormSheet's "Save as template" / template menu). A template captures the core authoring fields
// of a frequently-created task (NOT one-off context like a specific contact or absolute date) so the
// user can spin up a familiar task in one tap.
//
// Persistence mirrors the Android approach: a compact JSON blob in the existing settings store
// (App-Group `UserDefaults`) under a single key — NO SwiftData model / migration. This keeps
// templates out of the synced task graph (they're authoring conveniences, not user data).

/// A reusable shape for a frequently-created task.
struct TaskTemplate: Codable, Hashable, Identifiable, Sendable {
    var id: String = UUID().uuidString
    /// Display name + the upsert key (case-insensitive). Usually the task title.
    var name: String
    var detail: String?
    /// 0 = none, 1 = low, 2 = medium, 3 = high (matches `TaskItem.priority`).
    var priority: Int = 0
    var durationMinutes: Int?
    /// Checklist step labels (un-checked when applied).
    var checklist: [String] = []
    /// Optional recurrence carried verbatim (incl. a weekly weekday set).
    var recurrence: RecurrenceSpec?

    /// Build a template from the current editor draft.
    static func from(
        name: String,
        detail: String?,
        priority: Int,
        durationMinutes: Int?,
        checklist: [ChecklistItem],
        recurrence: RecurrenceSpec?
    ) -> TaskTemplate {
        TaskTemplate(
            name: name.trimmingCharacters(in: .whitespacesAndNewlines),
            detail: detail?.isEmpty == true ? nil : detail,
            priority: priority,
            durationMinutes: durationMinutes,
            checklist: checklist.map(\.text).filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty },
            recurrence: recurrence
        )
    }
}

/// Observable store backing the templates menu. Loads/saves a JSON array to App-Group
/// `UserDefaults`; upserts by case-insensitive name, newest last, capped at `limit`.
@Observable
final class TaskTemplateStore {
    /// App-Group key. Kept alongside the other settings keys (`task.templates` mirrors Android's
    /// DataStore key) so a single store owns it.
    static let storageKey = "task.templates"
    static let limit = 12

    private(set) var templates: [TaskTemplate]

    @ObservationIgnored private let defaults: UserDefaults

    init(defaults: UserDefaults? = nil) {
        self.defaults = defaults ?? UserDefaults(suiteName: ChronosStore.appGroup) ?? .standard
        self.templates = Self.decode(self.defaults.data(forKey: Self.storageKey))
    }

    /// Upsert by case-insensitive name (newest last), cap at `limit`, and persist.
    func save(_ template: TaskTemplate) {
        let key = template.name.trimmingCharacters(in: .whitespaces).lowercased()
        guard !key.isEmpty else { return }
        var next = templates.filter {
            $0.name.trimmingCharacters(in: .whitespaces).lowercased() != key
        }
        next.append(template)
        if next.count > Self.limit { next = Array(next.suffix(Self.limit)) }
        templates = next
        persist()
    }

    func delete(_ template: TaskTemplate) {
        templates.removeAll { $0.id == template.id }
        persist()
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(templates) {
            defaults.set(data, forKey: Self.storageKey)
        }
    }

    private static func decode(_ data: Data?) -> [TaskTemplate] {
        guard let data, let decoded = try? JSONDecoder().decode([TaskTemplate].self, from: data) else {
            return []
        }
        return decoded
    }
}
