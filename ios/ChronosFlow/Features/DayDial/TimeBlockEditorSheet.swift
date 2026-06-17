import SwiftUI
import SwiftData

/// Create / edit a time block. Used both from a dial tap (create) and a block tap (edit).
struct TimeBlockEditorSheet: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    let existing: TimeBlock?
    @State private var title: String
    @State private var category: String
    @State private var startMinute: Double
    @State private var duration: Double
    @State private var flexibility: BlockFlexibility
    @State private var energy: EnergyIntensity
    @State private var isLocked: Bool
    private let date: Date

    private let categories = ["FOCUS", "WORK", "STUDY", "BREAK", "MEAL", "ROUTINE", "EXERCISE", "SLEEP"]

    init(block: TimeBlock?, date: Date = .now, startMinute: Int = 9 * 60) {
        self.existing = block
        self.date = block?.date ?? date
        _title = State(initialValue: block?.title ?? "")
        _category = State(initialValue: block?.category ?? "FOCUS")
        _startMinute = State(initialValue: Double(block?.startMinuteOfDay ?? startMinute))
        _duration = State(initialValue: Double(block?.durationMinutes ?? 60))
        _flexibility = State(initialValue: block?.flexibility ?? .movable)
        _energy = State(initialValue: block?.energyLevel ?? .moderate)
        _isLocked = State(initialValue: block?.isLocked ?? false)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Title", text: $title)
                    Picker("Category", selection: $category) {
                        ForEach(categories, id: \.self) { c in
                            Label(c.capitalized, systemImage: "circle.fill")
                                .foregroundStyle(ChronosColors.category(c))
                                .tag(c)
                        }
                    }
                }
                Section("Time") {
                    LabeledContent("Start", value: Int(startMinute).clockTime)
                    Slider(value: $startMinute, in: 0...1425, step: 15)
                    LabeledContent("Duration", value: "\(Int(duration)) min")
                    Slider(value: $duration, in: 15...720, step: 15)
                    LabeledContent("Ends", value: ((Int(startMinute) + Int(duration)) % 1440).clockTime)
                }
                Section("Planner") {
                    Picker("Flexibility", selection: $flexibility) {
                        ForEach(BlockFlexibility.allCases, id: \.self) {
                            Text($0.rawValue.capitalized).tag($0)
                        }
                    }
                    Picker("Energy", selection: $energy) {
                        ForEach(EnergyIntensity.allCases, id: \.self) {
                            Text($0.label).tag($0)
                        }
                    }
                    Toggle("Lock (protect from AI)", isOn: $isLocked)
                }
                if let existing {
                    Section {
                        Button("Delete block", role: .destructive) {
                            context.delete(existing)
                            try? context.save()
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle(existing == nil ? "New block" : "Edit block")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", action: save).disabled(title.isEmpty)
                }
            }
        }
        .presentationDetents([.large])
        .presentationBackground(.thinMaterial)
    }

    private func save() {
        if let existing {
            existing.title = title
            existing.category = category
            existing.updateStart(Int(startMinute))
            existing.updateDuration(Int(duration))
            existing.flexibility = flexibility
            existing.energyLevel = energy
            existing.isLocked = isLocked
        } else {
            let block = TimeBlock(
                date: date, title: title, category: category,
                startMinuteOfDay: Int(startMinute), durationMinutes: Int(duration),
                flexibility: flexibility, energyLevel: energy, isLocked: isLocked
            )
            context.insert(block)
        }
        try? context.save()
        dismiss()
    }
}
