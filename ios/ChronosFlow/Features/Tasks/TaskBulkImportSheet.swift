import SwiftUI
import SwiftData
import ChronosCore

/// Reviews a batch of task titles shared / imported from another app before saving. Every candidate
/// is checked by default; the user can deselect any line, then import the rest in one tap. This is
/// the iOS port of the Android `TaskBulkImportSheet` (feature/tasks/TaskBulkImportSheet.kt) — same
/// checklist-of-candidates UX, same "Import N tasks" confirm. The candidate list is produced by
/// `ChronosCore.splitSharedTaskLines` (multi-line text / .txt / .ics VTODO) and routed here by
/// ShareViewController whenever 2+ titles are shared (a single title pre-fills the add-task sheet).
struct TaskBulkImportSheet: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    let candidates: [String]
    /// Called after the selected titles have been inserted, so the caller can clear pending state.
    var onImported: (() -> Void)? = nil

    /// Per-candidate selection, all on by default (mirrors Android `checked = candidates.map { true }`).
    @State private var selected: [Bool]
    /// Drives a success haptic when the import commits (parity with SleepView's syncFeedback pattern).
    @State private var importedTick = 0

    init(candidates: [String], onImported: (() -> Void)? = nil) {
        self.candidates = candidates
        self.onImported = onImported
        _selected = State(initialValue: Array(repeating: true, count: candidates.count))
    }

    private var selectedCount: Int { selected.filter { $0 }.count }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("\(candidates.count) item\(candidates.count == 1 ? "" : "s") shared from another app. Pick which to add.")
                        .font(.chronosCaption)
                        .foregroundStyle(.secondary)
                        .listRowSeparator(.hidden)
                }
                Section {
                    ForEach(Array(candidates.enumerated()), id: \.offset) { index, title in
                        Button {
                            withAnimation(ChronosMotion.snappy) { toggle(index) }
                        } label: {
                            HStack(spacing: ChronosSpacing.compact) {
                                Image(systemName: selected[index] ? "checkmark.circle.fill" : "circle")
                                    .font(.title3)
                                    .foregroundStyle(selected[index] ? ChronosColors.brandSecondary : .secondary)
                                Text(title)
                                    .foregroundStyle(.primary)
                                    .lineLimit(2)
                                Spacer(minLength: 0)
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(selected[index] ? "Exclude \(title)" : "Include \(title)")
                        .accessibilityAddTraits(.isButton)
                    }
                }
                if selectedCount == 0 {
                    Section {
                        Text("Select at least one item to import.")
                            .font(.chronosCaption)
                            .foregroundStyle(.secondary)
                            .listRowSeparator(.hidden)
                    }
                }
            }
            .navigationTitle("Import tasks")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(importLabel) { importSelected() }
                        .disabled(selectedCount == 0)
                        .fontWeight(.semibold)
                }
            }
            .sensoryFeedback(.success, trigger: importedTick)
        }
    }

    private var importLabel: String {
        selectedCount == 1 ? "Import 1 task" : "Import \(selectedCount) tasks"
    }

    private func toggle(_ index: Int) {
        guard selected.indices.contains(index) else { return }
        selected[index].toggle()
    }

    /// Insert one TaskItem per checked candidate, then dismiss. Each shared title is run through the
    /// offline smart-fill parser so a line like "Email Sam tomorrow 3pm" arrives with its due date /
    /// priority / recurrence already filled — matching the single-line add-sheet path.
    private func importSelected() {
        let titles = candidates.enumerated()
            .filter { selected.indices.contains($0.offset) && selected[$0.offset] }
            .map { $0.element }
        guard !titles.isEmpty else { return }

        for raw in titles {
            let fill = parseSmartFill(raw, now: .now)
            let cleanTitle = fill.cleanedTitle.trimmingCharacters(in: .whitespacesAndNewlines)
            let title = cleanTitle.isEmpty ? raw : cleanTitle
            let due = resolvedDueDate(fill)
            context.insert(TaskItem(
                title: title,
                priority: fill.priority.map(priorityInt) ?? 0,
                dueDate: due,
                targetDate: due,
                recurrence: fill.recurrence.map(mapRecurrence)))
        }
        try? context.save()
        onImported?()
        importedTick += 1
        dismiss()
    }

    private func resolvedDueDate(_ fill: SmartFillResult) -> Date? {
        if let due = fill.dueDate { return due }
        if let minute = fill.timeMinuteOfDay {
            return Calendar.current.date(
                bySettingHour: minute / 60, minute: minute % 60, second: 0,
                of: Calendar.current.startOfDay(for: .now))
        }
        return nil
    }

    private func priorityInt(_ p: TaskPriorityHint) -> Int {
        switch p { case .high: 3; case .medium: 2; case .low: 1 }
    }

    private func mapRecurrence(_ rule: RecurrenceRule) -> RecurrenceSpec {
        let frequency: RecurrenceSpec.Frequency = switch rule.frequency {
            case .daily: .daily; case .weekly: .weekly; case .monthly: .monthly
        }
        return RecurrenceSpec(
            frequency: frequency,
            interval: max(rule.interval, 1),
            weekdays: rule.frequency == .weekly ? rule.weekdays : [])
    }
}

#Preview {
    TaskBulkImportSheet(candidates: ["Buy milk", "Call dentist tomorrow 3pm", "Finish report (high)"])
        .modelContainer(ChronosStore.previewContainer())
}
