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

    private let categories = ["ROUTINE", "FOCUS", "BREAK", "MEAL", "EXERCISE", "STUDY"]

    init(routine: Routine?) {
        self.existing = routine
        _title = State(initialValue: routine?.title ?? "")
        _steps = State(initialValue: routine?.steps.sorted { $0.offsetMinute < $1.offsetMinute } ?? [])
        _isActive = State(initialValue: routine?.isActive ?? true)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Routine name", text: $title)
                    Toggle("Active", isOn: $isActive)
                } footer: {
                    Text("Inactive routines stay saved but are skipped by suggestions and won't surface in quick actions.")
                }
                Section("Steps") {
                    ForEach($steps) { $step in
                        VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                            TextField("Step", text: $step.title)
                            Picker("Category", selection: $step.category) {
                                ForEach(categories, id: \.self) { Text($0.capitalized).tag($0) }
                            }
                            Stepper("Offset: +\(step.offsetMinute)m", value: $step.offsetMinute, in: 0...720, step: 5)
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
                if let existing {
                    Section {
                        Button("Delete routine", role: .destructive) {
                            context.delete(existing); try? context.save(); dismiss()
                        }
                    }
                }
            }
            .navigationTitle(existing == nil ? "New routine" : "Edit routine")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", action: save).disabled(title.isEmpty)
                }
            }
        }
    }

    private func addStep() {
        // Default the new step's offset to right after the last step.
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
