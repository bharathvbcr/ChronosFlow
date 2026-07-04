import SwiftUI
import SwiftData

/// Create / edit a routine and its ordered steps (title, category, offset, duration).
struct RoutineEditorSheet: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    let existing: Routine?
    @State private var title: String
    @State private var steps: [RoutineStep]
    @State private var isActive: Bool
    @State private var newStepTitle = ""

    /// Static so callers building step drafts (RoutinesView's "Save today as routine") can clamp
    /// their categories to the picker's set.
    static let categories = ["ROUTINE", "FOCUS", "BREAK", "MEAL", "EXERCISE", "STUDY"]

    init(routine: Routine?) {
        self.existing = routine
        _title = State(initialValue: routine?.title ?? "")
        _steps = State(initialValue: routine?.steps.sorted { $0.offsetMinute < $1.offsetMinute } ?? [])
        _isActive = State(initialValue: routine?.isActive ?? true)
    }

    /// Compose a brand-new routine pre-filled from a draft (Android's saveCurrentAsTemplate flow:
    /// the editor opens in create mode with a suggested name + today's blocks so the user names it
    /// before anything is persisted).
    init(prefillTitle: String, prefillSteps: [RoutineStep]) {
        self.existing = nil
        _title = State(initialValue: prefillTitle)
        _steps = State(initialValue: prefillSteps.sorted { $0.offsetMinute < $1.offsetMinute })
        _isActive = State(initialValue: true)
    }

    var body: some View {
        CardEditorScaffold(
            kind: "routine",
            navTitle: existing == nil ? "New routine" : "Edit routine",
            chips: routineChips,
            sections: routineSections,
            footer: existing != nil ? AnyView(deleteFooter) : nil,
            saveDisabled: title.isEmpty,
            onCancel: { dismiss() },
            onSave: save
        ) {
            routineHeader
        }
    }

    // MARK: - Scaffold pieces

    @ViewBuilder private var routineHeader: some View {
        TextField("Routine name", text: $title)
        Toggle("Active", isOn: $isActive)
        Text("Inactive routines stay saved but are skipped by suggestions and won't surface in quick actions.")
            .font(.footnote)
            .foregroundStyle(.secondary)
    }

    private var routineChips: [EditorChip] {
        [
            EditorChip(
                id: "steps",
                systemImage: "list.bullet",
                title: "Steps",
                value: steps.isEmpty ? nil : "\(steps.count) step\(steps.count == 1 ? "" : "s")"
            ),
        ]
    }

    private var routineSections: [EditorSection] {
        [
            EditorSection(
                id: "steps",
                title: "Steps",
                systemImage: "list.bullet",
                badge: steps.isEmpty ? nil : "\(steps.count)",
                alwaysPrimary: true,
                hasValue: !steps.isEmpty
            ) {
                stepsRows
            },
        ]
    }

    @ViewBuilder private var stepsRows: some View {
        ForEach($steps) { $step in
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                TextField("Step", text: $step.title)
                Picker("Category", selection: $step.category) {
                    ForEach(Self.categories, id: \.self) { Text($0.capitalized).tag($0) }
                }
                Stepper("Offset: +\(step.offsetMinute)m", value: $step.offsetMinute, in: 0...1435, step: 5)
                Stepper("Duration: \(step.durationMinutes)m", value: $step.durationMinutes, in: 5...240, step: 5)
                Picker("Energy", selection: $step.energyLevel) {
                    ForEach(EnergyIntensity.allCases, id: \.rawValue) { e in
                        Text(e.label).tag(e.rawValue)
                    }
                }
            }
            .padding(.vertical, ChronosSpacing.micro)
        }
        .onDelete { steps.remove(atOffsets: $0) }
        .onMove { steps.move(fromOffsets: $0, toOffset: $1) }

        HStack {
            TextField("Add step", text: $newStepTitle)
            Button("Add") { addStep() }.disabled(newStepTitle.isEmpty)
        }
    }

    @ViewBuilder private var deleteFooter: some View {
        if let existing {
            Button("Delete routine", role: .destructive) {
                context.delete(existing)
                try? context.save()
                dismiss()
            }
        }
    }

    private func addStep() {
        let nextOffset = steps.map { $0.offsetMinute + $0.durationMinutes }.max() ?? 0
        steps.append(RoutineStep(title: newStepTitle, offsetMinute: nextOffset, durationMinutes: 15))
        newStepTitle = ""
    }

    private func save() {
        if let existing {
            existing.title = title
            existing.steps = steps
            existing.isActive = isActive
        } else {
            context.insert(Routine(title: title, isActive: isActive, steps: steps))
        }
        try? context.save()
        dismiss()
    }
}
