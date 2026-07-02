import SwiftUI
import SwiftData

/// The Routines tab: build reusable bundles of steps and apply them to any day, instantiating each
/// step as a TimeBlock at its offset past a chosen start. Ports the routine layer.
struct RoutinesView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \Routine.title) private var routines: [Routine]
    /// Today's blocks, used to derive each routine's "X/Y blocks done today" completion summary
    /// (mirrors Android's deriveRoutineCompletions over the selected day's TimeBlocks).
    @Query private var todaysBlocks: [TimeBlock]
    @State private var activeSheet: RoutineSheet?

    init() {
        let start = Calendar.current.startOfDay(for: .now)
        let end = Calendar.current.date(byAdding: .day, value: 1, to: start) ?? start
        _todaysBlocks = Query(filter: #Predicate<TimeBlock> { $0.date >= start && $0.date < end })
    }

    private enum RoutineSheet: Identifiable {
        case edit(Routine), new, apply(Routine), saveToday
        var id: String {
            switch self {
            case .edit(let r): "edit-\(r.id)"
            case .new: "new"
            case .apply(let r): "apply-\(r.id)"
            case .saveToday: "save-today"
            }
        }
    }

    /// Per-routine "X/Y blocks done today" summary, grouped by routineID. Mirrors Android's
    /// `deriveRoutineCompletions(blocks)` (RoutineTemplateConverters.kt): every block carrying a
    /// routineID counts toward the total; blocks with a recorded actualEndMinuteOfDay count as done.
    private var completions: [String: RoutineCompletionSummary] {
        var result: [String: RoutineCompletionSummary] = [:]
        for block in todaysBlocks {
            guard let rid = block.routineID else { continue }
            var summary = result[rid] ?? RoutineCompletionSummary(doneCount: 0, totalCount: 0)
            summary.totalCount += 1
            if block.actualEndMinuteOfDay != nil { summary.doneCount += 1 }
            result[rid] = summary
        }
        return result
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: ChronosSpacing.compact) {
                    ForEach(routines) { routine in
                        RoutineCard(routine: routine,
                                    completion: completions[routine.id],
                                    onApply: { activeSheet = .apply(routine) },
                                    onSeed: { seedDay(routine) },
                                    onEdit: { activeSheet = .edit(routine) },
                                    onCopy: { copy(routine) },
                                    onComplete: { markComplete(routine) })
                    }
                    // "Save current day as routine" (Android SidebarPageContent.kt 535): snapshot
                    // today's blocks into a routine draft and open the editor for naming before save.
                    Button {
                        activeSheet = .saveToday
                    } label: {
                        Label("Save today as routine", systemImage: "square.and.arrow.down")
                            .font(.chronosLabel)
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(ChronosColors.brandPrimary)
                    .disabled(todaysBlocks.isEmpty)
                }
                .padding(ChronosSpacing.standard)
            }
            .background { ChronosBackdrop() }
            .navigationTitle("Routines")
            .chronosScrollMinimizedBar()
            .overlay {
                if routines.isEmpty {
                    ContentUnavailableView {
                        Label("No routines", systemImage: "repeat")
                    } description: {
                        Text("Bundle steps you do together — a morning or wind-down routine")
                    } actions: {
                        Button("New routine") { activeSheet = .new }
                            .buttonStyle(.borderedProminent)
                            .tint(ChronosColors.brandPrimary)
                    }
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { activeSheet = .new } label: { Image(systemName: "plus") }
                        .accessibilityLabel("New routine")
                }
            }
            .sheet(item: $activeSheet) { sheet in
                switch sheet {
                case .edit(let r): RoutineEditorSheet(routine: r)
                case .new: RoutineEditorSheet(routine: nil)
                case .apply(let r): ApplyRoutineSheet(routine: r)
                case .saveToday:
                    RoutineEditorSheet(prefillTitle: suggestedRoutineName(),
                                       prefillSteps: stepsFromToday())
                }
            }
        }
    }

    /// Mark a routine done for today without re-instantiating its blocks — the iOS analogue of
    /// CompleteRoutineForDateUseCase (markCompleted). Separate from Apply, which only creates blocks.
    private func markComplete(_ routine: Routine) {
        withAnimation(ChronosMotion.snappy) {
            routine.lastCompletedDate = Calendar.current.startOfDay(for: .now)
            try? context.save()
        }
    }

    /// "Seed day": instantly populate today with the routine's blocks at their default offsets,
    /// with no start-time sheet. Mirrors Android's "Seed day" action (SidebarPageContent.kt 514),
    /// which calls ApplyRoutineToDateUseCase with the template anchor minute 0. Each step's
    /// offsetMinute is therefore treated as its absolute minute-of-day.
    private func seedDay(_ routine: Routine) {
        let today = Calendar.current.startOfDay(for: .now)
        for step in routine.steps.sorted(by: { $0.offsetMinute < $1.offsetMinute }) {
            let start = ((step.offsetMinute % 1440) + 1440) % 1440
            context.insert(TimeBlock(
                date: today, title: step.title, category: step.category,
                startMinuteOfDay: start, durationMinutes: min(max(step.durationMinutes, 1), 1440),
                provenance: .routine, energyLevel: EnergyIntensity.fromLevel(step.energyLevel),
                routineID: routine.id))
        }
        try? context.save()
    }

    /// Today's blocks as routine steps, sorted by start. Mirrors Android's `saveCurrentAsTemplate`
    /// draft mapping (DayDialTemplateState.kt 278–295): each block's start becomes the step offset
    /// (seed/apply anchor 0 treats offsets as absolute minute-of-day) with its duration and title.
    /// Android drafts every block including calendar imports, so no source is skipped here either.
    /// Categories outside the editor's picker collapse to ROUTINE so the picker stays valid.
    private func stepsFromToday() -> [RoutineStep] {
        todaysBlocks.sorted { $0.startMinuteOfDay < $1.startMinuteOfDay }.map { block in
            let category = block.category.uppercased()
            return RoutineStep(
                title: block.title.isEmpty ? "Focus block" : block.title,
                category: RoutineEditorSheet.categories.contains(category) ? category : "ROUTINE",
                offsetMinute: block.startMinuteOfDay,
                durationMinutes: min(max(block.durationMinutes, 1), 1440),
                energyLevel: block.energyLevel.rawValue)
        }
    }

    /// "My Day Routine", suffixed until unique against existing titles — mirrors Android's
    /// `suggestDayDialTemplateName` ("My Day Template" 2, 3, …).
    private func suggestedRoutineName() -> String {
        let base = "My Day Routine"
        let existing = Set(routines.map { $0.title.trimmingCharacters(in: .whitespaces).lowercased() })
        if !existing.contains(base.lowercased()) { return base }
        var suffix = 2
        while existing.contains("\(base) \(suffix)".lowercased()) { suffix += 1 }
        return "\(base) \(suffix)"
    }

    /// "Copy": duplicate a routine into a new, independent blueprint (fresh ids, "Copy" suffix).
    /// Mirrors Android's onDuplicateTemplate action (SidebarPageContent.kt 525).
    private func copy(_ routine: Routine) {
        let steps = routine.steps.map {
            RoutineStep(title: $0.title, category: $0.category,
                        offsetMinute: $0.offsetMinute, durationMinutes: $0.durationMinutes,
                        energyLevel: $0.energyLevel)
        }
        context.insert(Routine(title: "\(routine.title) Copy", isActive: routine.isActive, steps: steps))
        try? context.save()
    }
}

/// Per-routine completion tally for the viewed day. Ports `RoutineCompletionSummary`.
private struct RoutineCompletionSummary {
    var doneCount: Int
    var totalCount: Int
    /// "X/Y blocks done today" — mirrors Android's routineCompletionLabel.
    var label: String { "\(doneCount)/\(totalCount) blocks done today" }
}

private struct RoutineCard: View {
    let routine: Routine
    let completion: RoutineCompletionSummary?
    let onApply: () -> Void
    let onSeed: () -> Void
    let onEdit: () -> Void
    let onCopy: () -> Void
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
                // "X/Y blocks done today", derived from today's TimeBlocks carrying this routineID.
                // Mirrors Android's routineCompletionLabel (SidebarPageContent.kt 485–492).
                if let completion, completion.totalCount > 0 {
                    Text(completion.label)
                        .font(.chronosCaption.weight(.semibold))
                        .foregroundStyle(ChronosColors.brandPrimary)
                }
                // Action row mirrors Android's horizontally-scrolling template actions
                // (Apply / Seed day / Edit / Copy) plus iOS's deliberate "Mark complete".
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: ChronosSpacing.small) {
                        Button("Apply to day", action: onApply)
                            .buttonStyle(.borderedProminent)
                        Button("Seed day", action: onSeed)
                            .buttonStyle(.bordered)
                        Button("Edit", action: onEdit)
                            .buttonStyle(.bordered)
                        Button("Copy", action: onCopy)
                            .buttonStyle(.bordered)
                        Button(action: onComplete) {
                            Label("Mark complete", systemImage: "checkmark")
                        }
                        .buttonStyle(.bordered)
                        .disabled(completedToday)
                    }
                    .buttonBorderShape(.capsule)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .contentShape(Rectangle())
        .pressable()
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
        /// Whole-day offset from the apply date for steps whose start + offset rolls past midnight, so
        /// they land on the correct following day (Android `ApplyRoutineToDateUseCase` day rollover).
        let dayOffset: Int
        let durationMinutes: Int
        let energy: Int
    }

    /// Mirror of ChronosCore.instantiateRoutine — each step at start+offset; minute-of-day is wrapped
    /// to 0..<1440 and the overflow becomes a whole-day offset so a step crossing midnight is placed on
    /// the next day rather than back at the same day's small hours.
    private func instantiated() -> [InstantiatedStep] {
        let base = startMinute()
        return routine.steps.sorted { $0.offsetMinute < $1.offsetMinute }.map { step in
            let absolute = base + step.offsetMinute
            let start = ((absolute % 1440) + 1440) % 1440
            let dayOffset = Int(floor(Double(absolute) / 1440.0))
            return InstantiatedStep(title: step.title, category: step.category, startMinuteOfDay: start,
                                    dayOffset: dayOffset,
                                    durationMinutes: min(max(step.durationMinutes, 1), 1440), energy: step.energyLevel)
        }
    }

    /// Apply ≠ complete (mirrors Android's ApplyRoutineToDateUseCase vs CompleteRoutineForDateUseCase):
    /// applying only instantiates the steps as blocks. Completion is a separate, deliberate action
    /// ("Mark complete") so a routine planned ahead isn't prematurely stamped as done.
    private func apply() {
        let cal = Calendar.current
        for block in instantiated() {
            let blockDate = cal.date(byAdding: .day, value: block.dayOffset, to: date) ?? date
            context.insert(TimeBlock(
                date: blockDate, title: block.title, category: block.category,
                startMinuteOfDay: block.startMinuteOfDay, durationMinutes: block.durationMinutes,
                provenance: .routine, energyLevel: EnergyIntensity.fromLevel(block.energy),
                routineID: routine.id))
        }
        try? context.save()
        dismiss()
    }
}

#Preview { RoutinesView().modelContainer(ChronosStore.previewContainer()) }
