import SwiftUI
import SwiftData
import ChronosCore

/// The Plan tab: the Chronos Dial over the day's blocks, plus a chronological list, conflict
/// banner, free-window chips, and the AI day-planner entry point. Ports `feature/daydial`.
struct DayDialScreen: View {
    @Environment(\.modelContext) private var context
    /// Shared browsed day. Reads from the shell (so Plan/Today/Review agree and the bottom-bar
    /// off-today badge + double-tap reset work), falling back to local state when no shell is in the
    /// environment (e.g. SwiftUI previews). Mirrors Android's single `DayDialViewModel.selectedDate`.
    @Environment(ShellState.self) private var shell: ShellState?
    @State private var localDate = Calendar.current.startOfDay(for: .now)
    private var selectedDate: Date { shell?.selectedDate ?? localDate }
    private var selectedDateBinding: Binding<Date> {
        Binding(
            get: { shell?.selectedDate ?? localDate },
            set: { newValue in
                if let shell { shell.selectedDate = newValue } else { localDate = newValue }
            }
        )
    }
    @State private var activeSheet: DialSheet?
    @State private var showCalendar = false
    @State private var overlayEvents: [CalendarOverlayEvent] = []
    /// The block whose drag handles/live preview the dial shows. Selected by starting a drag on it;
    /// a plain tap still opens the editor (see `onTapBlock`).
    @State private var selectedBlockID: String?
    /// Value-typed undo/redo stack mirroring Android's PlannerCommandHistory. Held in @State so each
    /// mutation yields a new value and the toolbar re-derives canUndo/canRedo. Cleared on date change.
    @State private var history = PlannerCommandHistory()
    /// Transient toast for duplicate success/failure (mirrors Android's PlannerOperationResult toast).
    @State private var toast: String?
    /// Drives Confirm/Reject haptics on toast events (mirrors SleepView's syncFeedback idiom).
    private enum ToastOutcome: Equatable { case success, failure }
    @State private var toastFeedback: ToastOutcome?
    private let calendarProvider = CalendarOverlayProvider()

    @Query private var allBlocks: [TimeBlock]

    /// One enum-driven sheet — stacking multiple `.sheet` modifiers on one view is unreliable.
    private enum DialSheet: Identifiable {
        case edit(TimeBlock)
        case create(minute: Int)
        case ai
        case gapFill
        case resolve
        case repairAI
        var id: String {
            switch self {
            case .edit(let b): "edit-\(b.id)"
            case .create(let m): "create-\(m)"
            case .ai: "ai"
            case .gapFill: "gapfill"
            case .resolve: "resolve"
            case .repairAI: "repairAI"
            }
        }
    }

    private var dayBlocks: [TimeBlock] {
        allBlocks
            .filter { Calendar.current.isDate($0.date, inSameDayAs: selectedDate) }
            .sorted { $0.startMinuteOfDay < $1.startMinuteOfDay }
    }

    // App-side `PlannerMath`/`FreeWindow` are qualified because importing ChronosCore brings the
    // same type names into scope; the bare names would be ambiguous.
    private var conflicts: [ScheduleConflict] { ChronosFlow.PlannerMath.conflicts(in: dayBlocks) }
    private var freeWindows: [ChronosFlow.FreeWindow] { ChronosFlow.PlannerMath.freeWindows(in: dayBlocks) }

    /// Every block id involved in any conflict — drawn as a dashed error overlay on the dial.
    private var conflictBlockIDs: Set<String> {
        Set(conflicts.flatMap { [$0.firstID, $0.secondID] })
    }

    private var nowMinute: Int {
        let comps = Calendar.current.dateComponents([.hour, .minute], from: .now)
        return (comps.hour ?? 0) * 60 + (comps.minute ?? 0)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                ChronosBackdrop()
                ScrollView {
                    VStack(spacing: ChronosSpacing.medium) {
                        ChronosDialCanvas(
                            blocks: dayBlocks,
                            nowMinute: nowMinute,
                            selectedBlockID: selectedBlockID,
                            overlayEvents: showCalendar ? overlayEvents : [],
                            conflictBlockIDs: conflictBlockIDs,
                            showNowHand: Calendar.current.isDateInToday(selectedDate),
                            onTapBlock: { activeSheet = .edit($0) },
                            onSelectBlock: { selectedBlockID = $0 },
                            onCreate: { activeSheet = .create(minute: $0) },
                            onAdjustBlock: { block, newStart, newDuration in
                                adjustBlock(block, newStart: newStart, newDuration: newDuration)
                            }
                        )
                        .frame(maxWidth: 380)
                        .padding(.horizontal, ChronosSpacing.standard)

                        if !conflicts.isEmpty { conflictBanner }
                        if !freeWindows.isEmpty { freeWindowStrip }
                        blockList
                    }
                    .padding(.vertical, ChronosSpacing.standard)
                }
            }
            .navigationTitle("Plan")
            // iOS 27: collapse the nav bar as the day's block list scrolls up, giving the dial room.
            // Routed through the single helper so the new-API signature has one fix point.
            .chronosScrollMinimizedBar()
            .chronosCommandPaletteToolbar()
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    DatePicker("Day", selection: selectedDateBinding, displayedComponents: .date)
                        .labelsHidden()
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    // Undo/redo appear only when actionable. Permanently-present (greyed) buttons here
                    // overflowed the iPhone bar and pushed the leading DatePicker into the system "…"
                    // menu; surfacing them on demand keeps the day selector visible.
                    if history.canUndo {
                        Button { undo() } label: { Image(systemName: "arrow.uturn.backward") }
                            .accessibilityLabel("Undo")
                    }
                    if history.canRedo {
                        Button { redo() } label: { Image(systemName: "arrow.uturn.forward") }
                            .accessibilityLabel("Redo")
                    }
                    Menu {
                        Button { activeSheet = .ai } label: { Label("AI day plan", systemImage: "sparkles") }
                        Button { activeSheet = .gapFill } label: { Label("Fill free time", systemImage: "rectangle.compress.vertical") }
                        Divider()
                        // Calendar overlay toggle lives in the menu (was a standalone bar button that
                        // contributed to the overflow); the filled icon still signals the on state.
                        Button {
                            showCalendar.toggle()
                            if showCalendar { Task { await loadOverlay() } }
                        } label: {
                            Label(showCalendar ? "Hide calendar overlay" : "Show calendar overlay",
                                  systemImage: showCalendar ? "calendar.circle.fill" : "calendar.circle")
                        }
                        if !conflicts.isEmpty {
                            Divider()
                            // Deterministic, undoable conflict repair (Android "Fix schedule").
                            Button { activeSheet = .resolve } label: { Label("Fix schedule", systemImage: "wrench.and.screwdriver") }
                            // Hybrid: auto-resolve, then AI text for whatever can't be moved (Android "Repair with AI").
                            Button { activeSheet = .repairAI } label: { Label("Repair with AI", systemImage: "sparkles") }
                        }
                    } label: {
                        Image(systemName: "wand.and.stars")
                    }
                    .accessibilityLabel("Planning tools")
                    .accessibilityHint("AI plan, fill free time, calendar overlay, fix schedule")
                    Button { activeSheet = .create(minute: nowMinute) } label: { Image(systemName: "plus") }
                        .accessibilityLabel("Add block")
                }
            }
            .task(id: selectedDate) { if showCalendar { await loadOverlay() } }
            // A new day's edits are a fresh audit trail — drop the prior day's undo/redo stack.
            .onChange(of: selectedDate) { _, _ in history.clear() }
            .sheet(item: $activeSheet) { sheet in
                switch sheet {
                case .edit(let block):
                    TimeBlockEditorSheet(block: block)
                case .create(let minute):
                    TimeBlockEditorSheet(block: nil, date: selectedDate, startMinute: minute)
                case .ai:
                    AIPlannerSheet(date: selectedDate, existingBlocks: dayBlocks)
                case .gapFill:
                    GapFillSheet(date: selectedDate, existingBlocks: dayBlocks)
                case .resolve:
                    // Deterministic Fix-schedule: apply the moves and record one undoable batch.
                    ConflictResolveSheet(blocks: dayBlocks, mode: .deterministic) { applied in
                        recordResolveBatch(applied)
                    }
                case .repairAI:
                    // Hybrid Repair-with-AI: auto-resolve first, then hand the leftovers to the planner.
                    ConflictResolveSheet(blocks: dayBlocks, mode: .repairWithAI) { applied in
                        recordResolveBatch(applied)
                    }
                }
            }
            .overlay(alignment: .bottom) { toastView }
            // Confirm/Reject haptics on toast events, parity with SleepView's sync feedback.
            .sensoryFeedback(.success, trigger: toastFeedback) { _, new in new == .success }
            .sensoryFeedback(.error, trigger: toastFeedback) { _, new in new == .failure }
        }
    }

    @ViewBuilder private var toastView: some View {
        if let toast {
            Text(toast)
                .font(.chronosLabel)
                .padding(.horizontal, ChronosSpacing.standard)
                .padding(.vertical, ChronosSpacing.small)
                .glassEffect(.regular, in: Capsule())
                .padding(.bottom, ChronosSpacing.large)
                .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }

    private func loadOverlay() async {
        overlayEvents = await calendarProvider.events(on: selectedDate)
    }

    private var conflictBanner: some View {
        Button { activeSheet = .resolve } label: {
            ChronosGlassCard(tone: .quiet, tint: ChronosColors.brandAccent) {
                HStack {
                    Label("\(conflicts.count) overlapping block\(conflicts.count == 1 ? "" : "s")",
                          systemImage: "exclamationmark.triangle.fill")
                        .font(.chronosLabel)
                    Spacer()
                    Text("Resolve").font(.chronosLabel).foregroundStyle(ChronosColors.brandPrimary)
                    Image(systemName: "chevron.right").font(.caption).foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .buttonStyle(.plain)
        .pressable()
        .padding(.horizontal, ChronosSpacing.standard)
    }

    private var freeWindowStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: ChronosSpacing.small) {
                ForEach(freeWindows) { window in
                    Button {
                        activeSheet = .create(minute: window.startMinute)
                    } label: {
                        VStack(alignment: .leading) {
                            Text("Free").font(.chronosCaption).foregroundStyle(.secondary)
                            Text("\(window.startMinute.clockTime) · \(window.durationMinutes)m")
                                .font(.chronosLabel)
                        }
                        .padding(.horizontal, ChronosSpacing.compact)
                        .padding(.vertical, ChronosSpacing.small)
                    }
                    .glassEffect(.regular.tint(ChronosColors.brandSecondary.opacity(0.15)),
                                 in: Capsule())
                    .pressable()
                }
            }
            .padding(.horizontal, ChronosSpacing.standard)
        }
    }

    @ViewBuilder private var blockList: some View {
        if dayBlocks.isEmpty {
            ContentUnavailableView {
                Label("Nothing planned", systemImage: "calendar.badge.plus")
            } description: {
                Text("Block out your first commitment, or let AI draft the day.")
            } actions: {
                Button("Add block") { activeSheet = .create(minute: nowMinute) }
                    .buttonStyle(.borderedProminent)
                    .tint(ChronosColors.brandPrimary)
                Button("AI day plan") { activeSheet = .ai }
                    .buttonStyle(.bordered)
            }
            .padding(.horizontal, ChronosSpacing.standard)
        } else {
            VStack(spacing: ChronosSpacing.small) {
                timelineHeader
                ForEach(dayBlocks) { block in
                    Button { activeSheet = .edit(block) } label: { BlockRow(block: block) }
                        .buttonStyle(.plain)
                        // Smart duplicate (free-gap heuristics) + delete, both undoable. Mirrors the
                        // Android swipe/long-press actions on the timeline row.
                        .contextMenu {
                            // Start a focus session from a plan block (Android's block → focus
                            // action). Today-only: focus runs against the live day. Routed through
                            // the FocusCommandBridge queue, which FocusView drains on appear, so
                            // switching to the Focus tab starts the session with this block.
                            if shell != nil, Calendar.current.isDateInToday(block.date) {
                                Button { startFocus(block) } label: { Label("Start focus", systemImage: "timer") }
                            }
                            Button { duplicate(block) } label: { Label("Duplicate", systemImage: "plus.square.on.square") }
                            Button(role: .destructive) { deleteBlock(block) } label: { Label("Delete", systemImage: "trash") }
                        }
                        .swipeActions(edge: .leading, allowsFullSwipe: false) {
                            Button { duplicate(block) } label: { Label("Duplicate", systemImage: "plus.square.on.square") }
                                .tint(ChronosColors.brandSecondary)
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) { deleteBlock(block) } label: { Label("Delete", systemImage: "trash") }
                        }
                }
            }
            .padding(.horizontal, ChronosSpacing.standard)
        }
    }

    /// Section header over the day's rows: name + a "n blocks · Xh Ym planned" summary, so the
    /// list reads as a section of the screen instead of cards floating under the dial.
    private var timelineHeader: some View {
        HStack(alignment: .firstTextBaseline) {
            Text("Timeline").font(.chronosHeadline)
            Spacer()
            Text("\(dayBlocks.count) block\(dayBlocks.count == 1 ? "" : "s") · \(plannedDurationText(dayBlocks.map(\.durationMinutes).reduce(0, +))) planned")
                .font(.chronosCaption)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, ChronosSpacing.micro)
        .padding(.bottom, ChronosSpacing.micro)
    }

    // MARK: - Command-history-backed edits (mirror DayDialBlockDelegate)

    /// Move/resize commit routed through the undo stack: skip immovable blocks, push the matching
    /// command (move and/or resize carry the originals so they invert losslessly), then persist.
    private func adjustBlock(_ block: TimeBlock, newStart: Int, newDuration: Int) {
        guard !block.isLocked, block.flexibility != .fixed else { return }
        let originalStart = block.startMinuteOfDay
        let originalDuration = block.durationMinutes
        guard newStart != originalStart || newDuration != originalDuration else { return }

        if newStart != originalStart {
            history.push(.move(id: UUID().uuidString, blockId: block.id,
                               targetStartMinute: newStart, originalStartMinute: originalStart))
        }
        if newDuration != originalDuration {
            history.push(.resize(id: UUID().uuidString, blockId: block.id,
                                 targetDurationMinutes: newDuration, originalDurationMinutes: originalDuration))
        }
        block.updateStart(newStart)
        block.updateDuration(newDuration)
        try? context.save()
    }

    /// Smart duplicate: pick a free slot via the ported heuristic, materialize a standalone copy,
    /// insert it, and record a CreateTimeBlockCommand so it undoes cleanly. Surfaces a toast on the
    /// no-room case (Android's PlannerOperationResult).
    private func duplicate(_ block: TimeBlock) {
        let planBlocks = dayBlocks.map(plannerBlock(from:))
        guard let source = planBlocks.first(where: { $0.id == block.id }),
              let placement = nextDuplicatePlacement(dayBlocks: planBlocks, source: source) else {
            showToast("No free time to place a copy", outcome: .failure)
            return
        }
        let copy = makeDuplicate(of: source, placement: placement, newId: UUID().uuidString)
        let inserted = timeBlock(from: copy, on: block.date)
        context.insert(inserted)
        history.push(.create(id: UUID().uuidString, block: copy))
        try? context.save()
        showToast("Duplicated to \(copy.startMinuteOfDay.clockTime)", outcome: .success)
    }

    /// Enqueue a focus start for `block` and land on the Focus tab. FocusView's appear-time
    /// `drainFocusCommands` resolves the block and starts the timer (same path as the widget).
    private func startFocus(_ block: TimeBlock) {
        FocusCommandBridge.post(.start(blockID: block.id))
        shell?.select(.focus)
    }

    /// Delete routed through the undo stack: snapshot the block first so undo re-creates it exactly.
    private func deleteBlock(_ block: TimeBlock) {
        let snapshot = plannerBlock(from: block)
        if block.id == selectedBlockID { selectedBlockID = nil }
        history.push(.delete(id: UUID().uuidString, blockSnapshot: snapshot))
        context.delete(block)
        try? context.save()
    }

    /// Record a deterministic conflict-repair as one undoable batch (ResolveConflictsCommand).
    /// `applied` is the per-block (original → new) start change the sheet committed.
    private func recordResolveBatch(_ applied: [BlockStartChange]) {
        guard !applied.isEmpty else { return }
        history.push(.resolveConflicts(id: UUID().uuidString, changes: applied))
    }

    private func undo() {
        guard let command = history.popUndo() else { return }
        replay(command.inverse(on: currentPlannerBlocks()))
    }

    private func redo() {
        guard let command = history.popRedo() else { return }
        replay(command.apply(to: currentPlannerBlocks()))
    }

    /// The day's blocks as portable PlannerBlocks (the value type commands reduce over).
    private func currentPlannerBlocks() -> [PlannerBlock] { dayBlocks.map(plannerBlock(from:)) }

    /// Reconcile SwiftData with the command's resulting `[PlannerBlock]`: delete dropped rows,
    /// insert new ones, and mirror start/duration onto survivors. One save commits the whole step.
    private func replay(_ result: [PlannerBlock]) {
        let resultByID = Dictionary(uniqueKeysWithValues: result.map { ($0.id, $0) })
        let existingByID = Dictionary(uniqueKeysWithValues: dayBlocks.map { ($0.id, $0) })

        // Rows the command removed (e.g. undo of a create, or apply of a delete).
        for block in dayBlocks where resultByID[block.id] == nil {
            if block.id == selectedBlockID { selectedBlockID = nil }
            context.delete(block)
        }
        // Rows the command re-introduced (e.g. undo of a delete) or repositioned.
        for pb in result {
            if let existing = existingByID[pb.id] {
                if existing.startMinuteOfDay != pb.startMinuteOfDay { existing.updateStart(pb.startMinuteOfDay) }
                if existing.durationMinutes != pb.durationMinutes { existing.updateDuration(pb.durationMinutes) }
            } else {
                context.insert(timeBlock(from: pb, on: selectedDate))
            }
        }
        try? context.save()
    }

    private func showToast(_ message: String, outcome: ToastOutcome = .success) {
        toastFeedback = outcome
        withAnimation(ChronosMotion.smooth) { toast = message }
        Task {
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            withAnimation(ChronosMotion.smooth) { toast = nil }
        }
    }

    // MARK: - TimeBlock <-> PlannerBlock mapping

    /// Project a SwiftData `TimeBlock` onto the portable `PlannerBlock` the planner commands reduce
    /// over (provenance/flexibility/energy translate by raw value across the two module enums).
    private func plannerBlock(from block: TimeBlock) -> PlannerBlock {
        PlannerBlock(
            id: block.id,
            title: block.title,
            category: block.category,
            startMinuteOfDay: block.startMinuteOfDay,
            durationMinutes: block.durationMinutes,
            timezone: block.timezoneIdentifier,
            provenance: coreProvenance(block.provenance),
            flexibility: ChronosCore.BlockFlexibility(rawValue: block.flexibility.rawValue) ?? .movable,
            energyLevel: ChronosCore.EnergyIntensity(rawValue: block.energyLevel.rawValue) ?? .moderate,
            source: block.source,
            taskId: block.taskID,
            calendarEventId: block.calendarEventID,
            medicationPlanId: block.medicationPlanID,
            habitId: block.habitID,
            goalId: block.goalID,
            routineId: block.routineID,
            recurrenceRuleId: block.recurrenceRuleID,
            isLocked: block.isLocked,
            isProtected: block.isProtected,
            actualStartMinuteOfDay: block.actualStartMinuteOfDay,
            actualEndMinuteOfDay: block.actualEndMinuteOfDay
        )
    }

    /// Materialize a `PlannerBlock` (e.g. a duplicate, or an undone delete) into a SwiftData row.
    private func timeBlock(from pb: PlannerBlock, on date: Date) -> TimeBlock {
        TimeBlock(
            id: pb.id,
            date: date,
            title: pb.title,
            category: pb.category,
            startMinuteOfDay: min(max(pb.startMinuteOfDay, 0), 1439),
            durationMinutes: min(max(pb.durationMinutes, 1), 1440),
            timezoneIdentifier: pb.timezone,
            provenance: appProvenance(pb.provenance),
            // App-side enums qualified — ChronosCore exports same-named enums, so bare names are ambiguous.
            flexibility: ChronosFlow.BlockFlexibility(rawValue: pb.flexibility.rawValue) ?? .movable,
            energyLevel: ChronosFlow.EnergyIntensity(rawValue: pb.energyLevel.rawValue) ?? .moderate,
            source: pb.source,
            taskID: pb.taskId,
            calendarEventID: pb.calendarEventId,
            medicationPlanID: pb.medicationPlanId,
            habitID: pb.habitId,
            goalID: pb.goalId,
            routineID: pb.routineId,
            recurrenceRuleID: pb.recurrenceRuleId,
            isLocked: pb.isLocked,
            isProtected: pb.isProtected,
            actualStartMinuteOfDay: pb.actualStartMinuteOfDay,
            actualEndMinuteOfDay: pb.actualEndMinuteOfDay
        )
    }

    /// App `BlockProvenance` → ChronosCore `BlockProvenance`. The core enum has no `.routine`
    /// case, so routine blocks fall back to USER; everything else maps one-for-one.
    private func coreProvenance(_ p: ChronosFlow.BlockProvenance) -> ChronosCore.BlockProvenance {
        switch p {
        case .manual: return .user
        case .ai: return .aiSuggested
        case .calendar: return .calendar
        case .medication: return .medication
        case .habit: return .habit
        case .task: return .task
        case .routine: return .user
        }
    }

    /// ChronosCore `BlockProvenance` → app `BlockProvenance` (USER lands back on `.manual`).
    private func appProvenance(_ p: ChronosCore.BlockProvenance) -> ChronosFlow.BlockProvenance {
        switch p {
        case .user: return .manual
        case .aiSuggested: return .ai
        case .calendar: return .calendar
        case .medication: return .medication
        case .habit: return .habit
        case .task: return .task
        }
    }
}

private struct BlockRow: View {
    let block: TimeBlock
    var body: some View {
        ChronosGlassCard(tone: .standard, tint: ChronosColors.category(block.category)) {
            HStack(spacing: ChronosSpacing.compact) {
                RoundedRectangle(cornerRadius: 3)
                    .fill(ChronosColors.category(block.category))
                    .frame(width: 5, height: 40)
                VStack(alignment: .leading, spacing: 2) {
                    Text(block.title).font(.chronosHeadline)
                    Text("\(block.startMinuteOfDay.clockTime) – \(block.plannedEndMinuteOfDay.clockTime)")
                        .font(.chronosCaption).foregroundStyle(.secondary)
                }
                Spacer()
                if block.provenance == .ai {
                    Image(systemName: "sparkles").foregroundStyle(ChronosColors.brandPrimary)
                }
                if block.isLocked { Image(systemName: "lock.fill").foregroundStyle(.secondary) }
            }
        }
        .pressable()
    }
}

#Preview {
    DayDialScreen()
        .modelContainer(ChronosStore.previewContainer())
}
