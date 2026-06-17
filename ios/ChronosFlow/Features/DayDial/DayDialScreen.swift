import SwiftUI
import SwiftData

/// The Plan tab: the Chronos Dial over the day's blocks, plus a chronological list, conflict
/// banner, free-window chips, and the AI day-planner entry point. Ports `feature/daydial`.
struct DayDialScreen: View {
    @Environment(\.modelContext) private var context
    @State private var selectedDate = Calendar.current.startOfDay(for: .now)
    @State private var activeSheet: DialSheet?
    @State private var showCalendar = false
    @State private var overlayEvents: [CalendarOverlayEvent] = []
    /// The block whose drag handles/live preview the dial shows. Selected by starting a drag on it;
    /// a plain tap still opens the editor (see `onTapBlock`).
    @State private var selectedBlockID: String?
    private let calendarProvider = CalendarOverlayProvider()

    @Query private var allBlocks: [TimeBlock]

    /// One enum-driven sheet — stacking multiple `.sheet` modifiers on one view is unreliable.
    private enum DialSheet: Identifiable {
        case edit(TimeBlock)
        case create(minute: Int)
        case ai
        case gapFill
        case resolve
        var id: String {
            switch self {
            case .edit(let b): "edit-\(b.id)"
            case .create(let m): "create-\(m)"
            case .ai: "ai"
            case .gapFill: "gapfill"
            case .resolve: "resolve"
            }
        }
    }

    private var dayBlocks: [TimeBlock] {
        allBlocks
            .filter { Calendar.current.isDate($0.date, inSameDayAs: selectedDate) }
            .sorted { $0.startMinuteOfDay < $1.startMinuteOfDay }
    }

    private var conflicts: [ScheduleConflict] { PlannerMath.conflicts(in: dayBlocks) }
    private var freeWindows: [FreeWindow] { PlannerMath.freeWindows(in: dayBlocks) }

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
                            onTapBlock: { activeSheet = .edit($0) },
                            onSelectBlock: { selectedBlockID = $0 },
                            onCreate: { activeSheet = .create(minute: $0) },
                            onAdjustBlock: { block, newStart, newDuration in
                                guard !block.isLocked, block.flexibility != .fixed else { return }
                                block.updateStart(newStart)
                                block.updateDuration(newDuration)
                                try? context.save()
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
            .toolbarTitleDisplayMode(.inlineLarge)
            // iOS 27: collapse the nav bar as the day's block list scrolls up, giving the dial room.
            // Routed through the single helper so the new-API signature has one fix point.
            .chronosScrollMinimizedBar()
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    DatePicker("Day", selection: $selectedDate, displayedComponents: .date)
                        .labelsHidden()
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button {
                        showCalendar.toggle()
                        if showCalendar { Task { await loadOverlay() } }
                    } label: {
                        Image(systemName: showCalendar ? "calendar.circle.fill" : "calendar.circle")
                    }
                    .accessibilityLabel("Toggle calendar overlay")
                    Menu {
                        Button { activeSheet = .ai } label: { Label("AI day plan", systemImage: "sparkles") }
                        Button { activeSheet = .gapFill } label: { Label("Fill free time", systemImage: "rectangle.compress.vertical") }
                    } label: {
                        Image(systemName: "wand.and.stars")
                    }
                    .accessibilityLabel("Plan")
                    Button { activeSheet = .create(minute: nowMinute) } label: { Image(systemName: "plus") }
                        .accessibilityLabel("Add block")
                }
            }
            .task(id: selectedDate) { if showCalendar { await loadOverlay() } }
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
                    ConflictResolveSheet(blocks: dayBlocks)
                }
            }
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

    private var blockList: some View {
        VStack(spacing: ChronosSpacing.small) {
            ForEach(dayBlocks) { block in
                Button { activeSheet = .edit(block) } label: { BlockRow(block: block) }
                    .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, ChronosSpacing.standard)
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
