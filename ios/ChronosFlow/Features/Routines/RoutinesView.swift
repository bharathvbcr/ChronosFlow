import SwiftUI
import SwiftData

/// The Routines tab: build reusable bundles of steps and apply them to any day, instantiating each
/// step as a TimeBlock at its offset past a chosen start. Ports the routine layer.
struct RoutinesView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \Routine.title) private var routines: [Routine]
    @State private var activeSheet: RoutineSheet?

    private enum RoutineSheet: Identifiable {
        case edit(Routine), new, apply(Routine)
        var id: String {
            switch self {
            case .edit(let r): "edit-\(r.id)"
            case .new: "new"
            case .apply(let r): "apply-\(r.id)"
            }
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: ChronosSpacing.compact) {
                    ForEach(routines) { routine in
                        RoutineCard(routine: routine,
                                    onApply: { activeSheet = .apply(routine) },
                                    onEdit: { activeSheet = .edit(routine) },
                                    onComplete: { markComplete(routine) })
                    }
                }
                .padding(ChronosSpacing.standard)
            }
            .background { ChronosBackdrop() }
            .navigationTitle("Routines")
            .chronosScrollMinimizedBar()
            .overlay {
                if routines.isEmpty {
                    ContentUnavailableView("No routines", systemImage: "repeat",
                                           description: Text("Bundle steps you do together — a morning or wind-down routine"))
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { activeSheet = .new } label: { Image(systemName: "plus") }
                }
            }
            .sheet(item: $activeSheet) { sheet in
                switch sheet {
                case .edit(let r): RoutineEditorSheet(routine: r)
                case .new: RoutineEditorSheet(routine: nil)
                case .apply(let r): ApplyRoutineSheet(routine: r)
                }
            }
        }
    }

    /// Mark a routine done for today without re-instantiating its blocks — the iOS analogue of
    /// CompleteRoutineForDateUseCase (markCompleted). Separate from Apply, which only creates blocks.
    private func markComplete(_ routine: Routine) {
        routine.lastCompletedDate = Calendar.current.startOfDay(for: .now)
        try? context.save()
    }
}

private struct RoutineCard: View {
    let routine: Routine
    let onApply: () -> Void
    let onEdit: () -> Void
    let onComplete: () -> Void

    private var completedToday: Bool {
        guard let last = routine.lastCompletedDate else { return false }
        return Calendar.current.isDateInToday(last)
    }

    var body: some View {
        ChronosGlassCard(tint: ChronosColors.brandSecondary) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                HStack {
                    Text(routine.title).font(.chronosHeadline)
                    if !routine.isActive {
                        Text("Inactive")
                            .font(.chronosCaption)
                            .padding(.horizontal, 8).padding(.vertical, 2)
                            .background(.thinMaterial, in: Capsule())
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Text("\(routine.steps.count) steps").font(.chronosCaption).foregroundStyle(.secondary)
                }
                if !routine.steps.isEmpty {
                    Text(routine.steps.sorted { $0.offsetMinute < $1.offsetMinute }
                            .map(\.title).joined(separator: " · "))
                        .font(.chronosCaption).foregroundStyle(.secondary).lineLimit(2)
                }
                if let last = routine.lastCompletedDate {
                    Label(completedToday
                          ? "Completed today"
                          : "Last completed \(last.formatted(.relative(presentation: .named)))",
                          systemImage: completedToday ? "checkmark.seal.fill" : "checkmark.seal")
                        .font(.chronosCaption)
                        .foregroundStyle(completedToday ? ChronosColors.brandSecondary : .secondary)
                }
                HStack {
                    Button("Edit", action: onEdit).buttonStyle(.bordered).buttonBorderShape(.capsule)
                    Spacer()
                    Button(action: onComplete) {
                        Label("Mark complete", systemImage: "checkmark")
                    }
                    .buttonStyle(.bordered).buttonBorderShape(.capsule)
                    .disabled(completedToday)
                    Button("Apply to day", action: onApply)
                        .buttonStyle(.borderedProminent).buttonBorderShape(.capsule)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// Pick a start time and date, then instantiate the routine's steps as blocks.
struct ApplyRoutineSheet: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    let routine: Routine
    @State private var date = Date.now
    @State private var startTime = Calendar.current.date(bySettingHour: 6, minute: 0, second: 0, of: .now) ?? .now

    var body: some View {
        NavigationStack {
            Form {
                DatePicker("Day", selection: $date, displayedComponents: .date)
                DatePicker("Start at", selection: $startTime, displayedComponents: .hourAndMinute)
                Section("Preview") {
                    ForEach(instantiated()) { block in
                        HStack {
                            Text(block.title)
                            Spacer()
                            Text(block.startMinuteOfDay.clockTime).foregroundStyle(.secondary)
                        }
                        .font(.chronosCaption)
                    }
                }
            }
            .navigationTitle("Apply \(routine.title)")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Apply", action: apply) }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func startMinute() -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: startTime)
        return (c.hour ?? 6) * 60 + (c.minute ?? 0)
    }

    struct InstantiatedStep: Identifiable {
        let id = UUID()
        let title: String
        let category: String
        let startMinuteOfDay: Int
        let durationMinutes: Int
        let energy: Int
    }

    /// Mirror of ChronosCore.instantiateRoutine — each step at start+offset, wrapped to the day.
    private func instantiated() -> [InstantiatedStep] {
        let base = startMinute()
        return routine.steps.sorted { $0.offsetMinute < $1.offsetMinute }.map { step in
            let start = ((base + step.offsetMinute) % 1440 + 1440) % 1440
            return InstantiatedStep(title: step.title, category: step.category, startMinuteOfDay: start,
                                    durationMinutes: min(max(step.durationMinutes, 1), 1440), energy: step.energyLevel)
        }
    }

    /// Apply ≠ complete (mirrors Android's ApplyRoutineToDateUseCase vs CompleteRoutineForDateUseCase):
    /// applying only instantiates the steps as blocks. Completion is a separate, deliberate action
    /// ("Mark complete") so a routine planned ahead isn't prematurely stamped as done.
    private func apply() {
        for block in instantiated() {
            context.insert(TimeBlock(
                date: date, title: block.title, category: block.category,
                startMinuteOfDay: block.startMinuteOfDay, durationMinutes: block.durationMinutes,
                provenance: .routine, energyLevel: EnergyIntensity.fromLevel(block.energy),
                routineID: routine.id))
        }
        try? context.save()
        dismiss()
    }
}

#Preview { RoutinesView().modelContainer(ChronosStore.previewContainer()) }
