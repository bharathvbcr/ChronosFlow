import SwiftUI
import SwiftData
import HealthKit
import FoundationModels
import ChronosCore

// MARK: - JournalView
//
// The journal PAGE — the iOS analogue of Android's `JournalPageContent` (feature/daydial
// JournalScreen.kt). Brought to parity with the Android source-of-truth: the page no longer hosts the
// composer inline. Instead it presents `JournalEditorSheet` as a modal for new/edit flows (Android's
// `openNew(date)` / `openEdit(entry)`), and the page itself layers:
//   • A header card: "Journal" title, a streak label, and a "New entry" button. (Android header card)
//   • A calendar month overview painting mood emoji + workout badges per day, with month nav and
//     mood/workout filter pills (journalMoodByDate / journalWorkoutDates / journalWrittenDates). (J02)
//   • An INLINE on-device AI insight card (Foundation Models) with a deterministic offline heuristic
//     fallback — generate / regenerate / dismiss live in the card, NOT a separate sheet. (J03)
//   • A HealthKit workout import card (HKSampleQuery over .workoutType()). (J04)
//   • An always-available "Add a point to today" quick-capture card. (J09)
//   • Search + a grouped history list ("X of Y reflections" / "… match"), each day group carrying a
//     per-day header, an "add a full reflection" button, the day's rows, and an inline per-day point
//     adder — matching Android's `grouped.forEach { … JournalPointAdder(compact = true) }`.
//
// The rich composer (mood, prompt-shuffled reflection, highlights, optional time, multi-day range,
// photos, dictation) lives in `JournalEditorSheet` (Android's `JournalEntryComposer`).
//
// Model note: `JournalEntry` (Wellbeing.swift) has no `entryMinuteOfDay` column, so a point's time
// is carried on `createdAt` (a real instant on the entry's day) — `entryDate` stays start-of-day for
// keying. `minuteOfDay(of:)` derives the display/sort time from `createdAt`. Workout points are
// stamped with the `journalWorkoutIdPrefix` id convention so `isJournalWorkoutEntry` recognises them.

struct JournalView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \JournalEntry.entryDate, order: .reverse) private var entries: [JournalEntry]

    // Editor sheet target — Android's (composing, editingEntryId, composeDate).
    @State private var editorTarget: JournalEditorTarget?

    // Inline point adder (today)
    @State private var pointDraft = ""
    @State private var pointTimeText = ""

    /// Shared, Dynamic-Type-aware width for the inline time fields (top card + per-day adder).
    @ScaledMetric(relativeTo: .caption) private var timeFieldWidth: CGFloat = 72

    // Per-day inline point adders in the history (keyed by start-of-day).
    @State private var dayPointDraft: [Date: String] = [:]
    @State private var dayPointTime: [Date: String] = [:]

    // Calendar overview
    @State private var visibleMonth: Date = Calendar.current.startOfDay(for: .now)
    @State private var showMoodLayer = true
    @State private var showWorkoutLayer = true
    /// Toggled on tapping a written calendar day, to drive the selection haptic.
    @State private var calendarTapFeedback = false

    // History search
    @State private var searchText = ""
    /// When set, the history list scrolls to this day's group (driven by tapping a calendar day).
    @State private var scrollTarget: Date?

    // AI insight
    @State private var insight = JournalInsightEngine()

    // HealthKit workout import
    @State private var workouts = JournalWorkoutImporter()

    private let cal = Calendar.current

    // MARK: Derived collections

    /// Value-typed snapshot of all entries for the ChronosCore overview helpers.
    private var records: [JournalEntryRecord] {
        entries.map { entry in
            JournalEntryRecord(
                id: entry.id,
                entryDate: entry.entryDate,
                body: entry.body,
                isPrimary: entry.isPrimary,
                dayRating: entry.moodRating > 0 ? entry.moodRating : nil,
                entryMinuteOfDay: minuteOfDay(of: entry),
                createdAt: entry.createdAt)
        }
    }

    private var moodByDate: [Date: ChronosCore.JournalMood] { journalMoodByDate(records, calendar: cal) }
    private var workoutDates: Set<Date> { journalWorkoutDates(records, calendar: cal) }
    private var writtenDates: Set<Date> { journalWrittenDates(records, calendar: cal) }

    // MARK: Streak

    private var currentStreak: Int {
        let today = cal.startOfDay(for: .now)
        let entryDates = Set(entries.map { cal.startOfDay(for: $0.entryDate) })
        var streak = 0
        var cursor = today
        if entryDates.contains(today) {
            streak = 1
            cursor = cal.date(byAdding: .day, value: -1, to: today) ?? today
        } else {
            cursor = cal.date(byAdding: .day, value: -1, to: today) ?? today
        }
        while entryDates.contains(cursor) {
            streak += 1
            cursor = cal.date(byAdding: .day, value: -1, to: cursor) ?? cursor
        }
        return streak
    }

    // MARK: Streak label

    /// "🔥 5-day streak" / "🔥 On a roll — 12 days!" — mirrors Android's `journalStreakLabel`.
    private var streakLabel: String? {
        let streak = currentStreak
        guard streak > 0 else { return nil }
        return streak >= 7 ? "🔥 On a roll — \(streak) days!" : "🔥 \(streak)-day streak"
    }

    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: ChronosSpacing.medium) {
                    headerCard
                    calendarOverview
                    aiInsightCard
                    workoutImportCard
                    pointsSection
                    historySection
                }
                .padding(ChronosSpacing.standard)
            }
            // Tapping a calendar day scrolls the history to that day's group (Android parity for the
            // tappable month grid). Only days with entries are tappable, so the target always exists.
            .onChange(of: scrollTarget) { _, target in
                guard let target else { return }
                withAnimation(ChronosMotion.snappy) { proxy.scrollTo(target, anchor: .top) }
                scrollTarget = nil
            }
            .background { ChronosBackdrop() }
            .navigationTitle("Journal")
            .chronosScrollMinimizedBar()
            .onAppear { workouts.refreshAvailability() }
            // The composer is a modal sheet (Android's JournalComposerSheet), not inline. Editing the
            // day's existing entry, or composing a new one for a specific day.
            .sheet(item: $editorTarget) { target in
                JournalEditorSheet(editing: target.entry,
                                   initialDate: target.date,
                                   streak: currentStreak)
            }
            }
        }
    }

    // MARK: Header card

    /// Title + streak + "New entry" — Android's `JournalPageContent` header `ChronosListCard`.
    private var headerCard: some View {
        ChronosGlassCard {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: ChronosSpacing.micro) {
                    Text("Journal")
                        .font(.chronosTitle)
                    Text(streakLabel ?? "Start a streak today")
                        .font(.chronosLabel)
                        .foregroundStyle(currentStreak > 0 ? ChronosColors.brandPrimary : .secondary)
                }
                Spacer()
                Button {
                    editorTarget = JournalEditorTarget(date: cal.startOfDay(for: .now), entry: nil)
                } label: {
                    Label("New entry", systemImage: "plus")
                        .font(.chronosLabel)
                }
                .buttonStyle(.borderedProminent)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// Open the composer for a brand-new entry pinned to `date` (Android's `openNew(date)`).
    private func openNew(_ date: Date) {
        editorTarget = JournalEditorTarget(date: cal.startOfDay(for: date), entry: nil)
    }

    /// Open the composer to edit `entry` (Android's `openEdit(entry)`).
    private func openEdit(_ entry: JournalEntry) {
        editorTarget = JournalEditorTarget(date: cal.startOfDay(for: entry.entryDate), entry: entry)
    }

    // MARK: Inline point adder ("Add a point to today")

    private var pointsSection: some View {
        ChronosGlassCard {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                Text("Add a point to today")
                    .font(.chronosHeadline)
                Text("Quick timed notes for today — a moment, a thought, a win.")
                    .font(.chronosCaption)
                    .foregroundStyle(.secondary)

                HStack(alignment: .top, spacing: ChronosSpacing.small) {
                    TextField("What happened?", text: $pointDraft, axis: .vertical)
                        .lineLimit(1...4)
                        .font(.chronosBody)
                    TextField("time", text: $pointTimeText)
                        .font(.chronosCaption)
                        .frame(minWidth: timeFieldWidth)
                        .textInputAutocapitalization(.characters)
                }

                Button {
                    addPoint(on: .now, body: pointDraft, timeText: pointTimeText)
                    pointDraft = ""
                    pointTimeText = ""
                } label: {
                    Label("Add point", systemImage: "plus")
                        .font(.chronosCaption)
                }
                .buttonStyle(.bordered)
                .disabled(pointDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: Workout import card (HealthKit)

    @ViewBuilder
    private var workoutImportCard: some View {
        switch workouts.availability {
        case .unavailable:
            EmptyView()
        default:
            ChronosGlassCard(tint: ChronosColors.brandSecondary) {
                VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                    Label("Import workouts", systemImage: "figure.run")
                        .font(.chronosHeadline)
                    Text("Add your recent HealthKit workouts as journal points.")
                        .font(.chronosCaption)
                        .foregroundStyle(.secondary)

                    if workouts.isImporting {
                        HStack(spacing: ChronosSpacing.small) {
                            ProgressView()
                            Text("Importing…").font(.chronosCaption).foregroundStyle(.secondary)
                        }
                    } else if let result = workouts.lastResult {
                        Text(result.inserted == 0
                             ? "Up to date — no new workouts."
                             : "Imported \(result.inserted) workout\(result.inserted == 1 ? "" : "s").")
                            .font(.chronosCaption)
                            .foregroundStyle(ChronosColors.brandSecondary)
                    }

                    Button {
                        Task {
                            if workouts.availability == .needsAuthorization {
                                await workouts.requestAuthorization()
                            }
                            await workouts.importRecent(into: context)
                        }
                    } label: {
                        Label(workouts.availability == .needsAuthorization ? "Connect HealthKit" : "Import",
                              systemImage: "square.and.arrow.down")
                            .font(.chronosCaption)
                    }
                    .buttonStyle(.bordered)
                    .disabled(workouts.isImporting)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    // MARK: AI insight card

    @ViewBuilder
    private var aiInsightCard: some View {
        if !records.isEmpty {
            ChronosGlassCard(tint: ChronosColors.brandPrimary) {
                VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                    HStack {
                        Label("Reflection insight", systemImage: "sparkles")
                            .font(.chronosHeadline)
                        Spacer()
                        if case .ready(_, let fromAi) = insight.state {
                            Text(fromAi ? "On-device AI" : "Summary")
                                .font(.chronosCaption)
                                .foregroundStyle(.secondary)
                        }
                    }

                    switch insight.state {
                    case .idle:
                        Text("Notice patterns in your mood and recurring themes from recent entries.")
                            .font(.chronosCaption)
                            .foregroundStyle(.secondary)
                        Button {
                            generateInsight()
                        } label: {
                            Label("Reflect on recent entries", systemImage: "wand.and.stars")
                                .font(.chronosCaption)
                        }
                        .buttonStyle(.bordered)

                    case .loading:
                        HStack(spacing: ChronosSpacing.small) {
                            ProgressView()
                            Text("Reflecting…").font(.chronosCaption).foregroundStyle(.secondary)
                        }

                    case .ready(let text, _):
                        Text(text).font(.chronosBody)
                        HStack(spacing: ChronosSpacing.standard) {
                            Button {
                                generateInsight()
                            } label: {
                                Label("Regenerate", systemImage: "arrow.clockwise").font(.chronosCaption)
                            }
                            .buttonStyle(.plain)
                            .foregroundStyle(ChronosColors.brandPrimary)
                            Button {
                                withAnimation(ChronosMotion.smooth) { insight.dismiss() }
                            } label: {
                                Text("Dismiss").font(.chronosCaption)
                            }
                            .buttonStyle(.plain)
                            .foregroundStyle(.secondary)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private func generateInsight() {
        let snapshot = journalSortedPoints(records.filter { !isJournalWorkoutEntry($0) })
            .filter { $0.isPrimary || $0.dayRating != nil }
        // Newest first for the prompt builder.
        let ordered = snapshot.sorted { $0.entryDate > $1.entryDate }
        Task { await insight.generate(from: ordered, today: .now, calendar: cal) }
    }

    // MARK: Calendar overview

    private var calendarOverview: some View {
        ChronosGlassCard {
            VStack(alignment: .leading, spacing: ChronosSpacing.compact) {
                HStack {
                    Button {
                        withAnimation(ChronosMotion.snappy) { shiftMonth(by: -1) }
                    } label: {
                        Image(systemName: "chevron.left")
                            .frame(minWidth: 44, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Previous month")
                    Spacer()
                    Text(visibleMonth.formatted(.dateTime.month(.wide).year()))
                        .font(.chronosHeadline)
                    Spacer()
                    Button {
                        withAnimation(ChronosMotion.snappy) { shiftMonth(by: 1) }
                    } label: {
                        Image(systemName: "chevron.right")
                            .frame(minWidth: 44, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                        .buttonStyle(.plain)
                        .disabled(isCurrentMonth)
                        .accessibilityLabel("Next month")
                }

                HStack(spacing: ChronosSpacing.small) {
                    filterPill("Mood", systemImage: "face.smiling", on: showMoodLayer) {
                        withAnimation(ChronosMotion.snappy) { showMoodLayer.toggle() }
                    }
                    filterPill("Workouts", systemImage: "figure.run", on: showWorkoutLayer) {
                        withAnimation(ChronosMotion.snappy) { showWorkoutLayer.toggle() }
                    }
                }

                weekdayHeader
                monthGrid

                if let recap = monthRecap {
                    Divider()
                    Text(recap)
                        .font(.chronosCaption)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .center)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// One-line summary of the visible month — "avg 😄 Great · 8 days journaled · 3 workouts" —
    /// mirroring the Android calendar recap below the month grid. `nil` when the month is empty.
    private var monthRecap: String? {
        let inMonth: (Date) -> Bool = { cal.isDate($0, equalTo: visibleMonth, toGranularity: .month) }
        let journaled = writtenDates.filter(inMonth).count
        let workouts = workoutDates.filter(inMonth).count
        let ratings = moodByDate.filter { inMonth($0.key) }.map { $0.value.rating }
        guard journaled > 0 || workouts > 0 || !ratings.isEmpty else { return nil }

        var parts: [String] = []
        if !ratings.isEmpty {
            let avg = Int((Double(ratings.reduce(0, +)) / Double(ratings.count)).rounded())
            if let mood = journalMoodFor(avg) { parts.append("avg \(mood.emoji) \(mood.label)") }
        }
        if journaled > 0 { parts.append("\(journaled) day\(journaled == 1 ? "" : "s") journaled") }
        if workouts > 0 { parts.append("\(workouts) workout\(workouts == 1 ? "" : "s")") }
        return parts.joined(separator: " · ")
    }

    private func filterPill(_ title: String, systemImage: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: systemImage)
                .font(.chronosCaption)
                .foregroundStyle(on ? ChronosColors.brandPrimary : .secondary)
                .padding(.horizontal, ChronosSpacing.compact)
                .padding(.vertical, ChronosSpacing.micro)
                .background((on ? ChronosColors.brandPrimary : Color.secondary).opacity(0.14), in: Capsule())
        }
        .buttonStyle(.plain)
    }

    private var weekdayHeader: some View {
        let symbols = cal.veryShortStandaloneWeekdaySymbols
        let ordered = Array(symbols[(cal.firstWeekday - 1)...] + symbols[..<(cal.firstWeekday - 1)])
        return HStack(spacing: 0) {
            ForEach(Array(ordered.enumerated()), id: \.offset) { _, sym in
                Text(sym)
                    .font(.chronosCaption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private var monthGrid: some View {
        let days = monthDays()
        let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: 7)
        return LazyVGrid(columns: columns, spacing: 4) {
            ForEach(Array(days.enumerated()), id: \.offset) { _, day in
                calendarCell(day)
            }
        }
    }

    @ViewBuilder
    private func calendarCell(_ day: Date?) -> some View {
        if let day {
            let key = cal.startOfDay(for: day)
            let mood = showMoodLayer ? moodByDate[key] : nil
            let hasWorkout = showWorkoutLayer && workoutDates.contains(key)
            let wrote = writtenDates.contains(key)
            let isToday = cal.isDateInToday(day)
            VStack(spacing: 1) {
                ZStack {
                    if let mood {
                        Text(mood.emoji).font(.system(size: 18))
                    } else {
                        Text("\(cal.component(.day, from: day))")
                            .font(.chronosCaption)
                            .foregroundStyle(wrote ? .primary : .secondary)
                    }
                    if hasWorkout {
                        Image(systemName: "figure.run")
                            .font(.system(size: 8))
                            .foregroundStyle(ChronosColors.brandSecondary)
                            .offset(x: 11, y: -11)
                    }
                }
                .frame(height: 24)
                Circle()
                    .fill(wrote ? ChronosColors.brandPrimary : .clear)
                    .frame(width: 4, height: 4)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 2)
            .background(isToday ? ChronosColors.brandPrimary.opacity(0.12) : .clear,
                        in: RoundedRectangle(cornerRadius: ChronosRadius.extraSmall, style: .continuous))
            .contentShape(Rectangle())
            .accessibilityElement(children: .combine)
            .accessibilityLabel(Self.cellLabel(day: day, cal: cal, mood: mood,
                                               hasWorkout: hasWorkout, wrote: wrote, isToday: isToday))
            .accessibilityHint(wrote ? "Opens reflections" : "")
            .pressable()
            // Tap a day that has reflections to jump the history list to it.
            .onTapGesture {
                if wrote { scrollTarget = key; calendarTapFeedback.toggle() }
            }
            .sensoryFeedback(.selection, trigger: calendarTapFeedback)
        } else {
            Color.clear.frame(height: 32)
        }
    }

    /// Spoken VoiceOver label for a calendar day cell — date, plus any state the cell conveys only
    /// via emoji / glyph / dot (today, mood, workout, has-reflection).
    private static func cellLabel(day: Date, cal: Calendar, mood: ChronosCore.JournalMood?,
                                  hasWorkout: Bool, wrote: Bool, isToday: Bool) -> String {
        var parts: [String] = [day.formatted(.dateTime.month().day())]
        if isToday { parts.append("Today") }
        if let mood { parts.append("mood \(mood.label)") }
        if hasWorkout { parts.append("workout") }
        if wrote { parts.append("has reflection") }
        return parts.joined(separator: ", ")
    }

    private var isCurrentMonth: Bool {
        cal.isDate(visibleMonth, equalTo: .now, toGranularity: .month)
    }

    private func shiftMonth(by months: Int) {
        if let next = cal.date(byAdding: .month, value: months, to: visibleMonth) {
            visibleMonth = cal.startOfDay(for: next)
        }
    }

    /// The visible month's day cells, padded with leading nils so the 1st lands under its weekday.
    private func monthDays() -> [Date?] {
        guard let monthInterval = cal.dateInterval(of: .month, for: visibleMonth),
              let range = cal.range(of: .day, in: .month, for: visibleMonth) else { return [] }
        let firstWeekday = cal.component(.weekday, from: monthInterval.start)
        let leading = (firstWeekday - cal.firstWeekday + 7) % 7
        var cells: [Date?] = Array(repeating: nil, count: leading)
        for offset in 0..<range.count {
            if let day = cal.date(byAdding: .day, value: offset, to: monthInterval.start) {
                cells.append(day)
            }
        }
        return cells
    }

    // MARK: Actions

    /// Create a secondary, timed journal point on `day` from an inline adder (top card or per-day).
    /// Mirrors Android's `JournalViewModel.addPoint(date, body, minuteOfDay)`.
    private func addPoint(on day: Date, body: String, timeText: String) {
        let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let timestamp = composedTimestamp(on: day, timeText: timeText)
        let entry = JournalEntry(
            entryDate: cal.startOfDay(for: day),
            createdAt: timestamp,
            updatedAt: .now,
            body: trimmed,
            isPrimary: false,
            moodRating: 0)
        context.insert(entry)
        try? context.save()
    }

    /// Resolve the entry's stored instant: the day's start-of-day plus the parsed minute-of-day when a
    /// time was given, otherwise `now` (today) or the day's noon. Lets `minuteOfDay(of:)` recover it.
    private func composedTimestamp(on day: Date, timeText text: String) -> Date {
        let start = cal.startOfDay(for: day)
        if let minute = parseFlexibleMinute(text) {
            return cal.date(byAdding: .minute, value: minute, to: start) ?? day
        }
        if cal.isDateInToday(day) { return .now }
        return cal.date(byAdding: .hour, value: 12, to: start) ?? start
    }

    /// Minute-of-day for an entry, derived from its `createdAt` instant (0–1439), or nil for the
    /// auto-stamped epoch placeholder.
    private func minuteOfDay(of entry: JournalEntry) -> Int? {
        let comps = cal.dateComponents([.hour, .minute], from: entry.createdAt)
        guard let hour = comps.hour, let minute = comps.minute else { return nil }
        return hour * 60 + minute
    }

    // MARK: History

    private var filteredEntries: [JournalEntry] {
        guard !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return entries }
        return entries.filter { $0.body.range(of: searchText, options: .caseInsensitive) != nil }
    }

    private var groupedHistory: [(key: Date, entries: [JournalEntry])] {
        let dict = Dictionary(grouping: filteredEntries) { cal.startOfDay(for: $0.entryDate) }
        return dict
            .map { (key: $0.key, entries: sortedDayEntries($0.value)) }
            .sorted { $0.key > $1.key }
    }

    /// Day entries ordered like Android's list: primary first, then by time-of-day, then creation.
    private func sortedDayEntries(_ dayEntries: [JournalEntry]) -> [JournalEntry] {
        dayEntries.sorted { lhs, rhs in
            if lhs.isPrimary != rhs.isPrimary { return lhs.isPrimary }
            let lm = minuteOfDay(of: lhs) ?? Int.max
            let rm = minuteOfDay(of: rhs) ?? Int.max
            if lm != rm { return lm < rm }
            return lhs.createdAt < rhs.createdAt
        }
    }

    @ViewBuilder
    private var historySection: some View {
        if entries.isEmpty {
            ContentUnavailableView(
                "No reflections yet",
                systemImage: "book.closed",
                description: Text("Your saved reflections will appear here."))
                .padding(.top, ChronosSpacing.large)
        } else {
            // "History" + Android's `journalHistoryPageSummary` ("5 reflections" / "2 of 5 … match").
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("History").font(.chronosTitle)
                    Text(journalHistoryPageSummary(total: entries.count,
                                                   shown: filteredEntries.count,
                                                   query: searchText))
                        .font(.chronosCaption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
            }

            HStack(spacing: ChronosSpacing.small) {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary).accessibilityHidden(true)
                TextField("Search your reflections", text: $searchText)
                    .font(.chronosBody)
                if !searchText.isEmpty {
                    Button {
                        searchText = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Clear search")
                }
            }
            .padding(ChronosSpacing.compact)
            .background(.thinMaterial, in: RoundedRectangle(cornerRadius: ChronosRadius.small, style: .continuous))

            if filteredEntries.isEmpty {
                Text("No reflections match “\(searchText.trimmingCharacters(in: .whitespacesAndNewlines))”.")
                    .font(.chronosBody)
                    .foregroundStyle(.secondary)
                    .padding(.top, ChronosSpacing.small)
            } else {
                ForEach(groupedHistory, id: \.key) { group in
                    dayGroup(group)
                        .id(group.key)
                }
            }
        }
    }

    /// One day group in the history list: a header with the day's mood emoji + a "+" to compose a full
    /// reflection on that day, the day's rows, then an inline point adder — Android's `grouped.forEach`.
    @ViewBuilder
    private func dayGroup(_ group: (key: Date, entries: [JournalEntry])) -> some View {
        // Exclude auto-imported workout entries from the human-written count (calendar "journaled" semantics).
        let writtenCount = group.entries.filter { !$0.id.hasPrefix(journalWorkoutIdPrefix) }.count

        VStack(alignment: .leading, spacing: ChronosSpacing.small) {
            HStack(spacing: ChronosSpacing.small) {
                if let mood = moodByDate[group.key] {
                    Text(mood.emoji).font(.chronosCaption)
                }
                Text(journalDayGroupLabel(group.key, count: writtenCount, calendar: cal))
                    .font(.chronosLabel)
                    .foregroundStyle(ChronosColors.brandPrimary)
                Spacer()
                Button {
                    openNew(group.key)
                } label: {
                    Image(systemName: "calendar.badge.plus")
                        .font(.chronosBody)
                        .foregroundStyle(ChronosColors.brandPrimary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Add a full reflection on this day")
            }
            .padding(.top, ChronosSpacing.small)

            ForEach(group.entries) { entry in
                JournalHistoryCard(entry: entry, minuteOfDay: minuteOfDay(of: entry)) {
                    // Tapping a written (non-workout) row opens the editor for it (Android openEdit).
                    if !entry.id.hasPrefix(journalWorkoutIdPrefix) { openEdit(entry) }
                } onDelete: {
                    context.delete(entry)
                    try? context.save()
                }
            }

            // Inline subtask-style adder for another timed point on this day (Android JournalPointAdder).
            dayPointAdder(for: group.key)
        }
    }

    /// Compact per-day point adder shown under each day group's entries (Android `compact = true`).
    private func dayPointAdder(for day: Date) -> some View {
        let bodyBinding = Binding(
            get: { dayPointDraft[day] ?? "" },
            set: { dayPointDraft[day] = $0 })
        let timeBinding = Binding(
            get: { dayPointTime[day] ?? "" },
            set: { dayPointTime[day] = $0 })
        let canAdd = !(dayPointDraft[day] ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty

        return HStack(spacing: ChronosSpacing.small) {
            TextField("Add another point…", text: bodyBinding)
                .font(.chronosCaption)
            TextField("time", text: timeBinding)
                .font(.chronosCaption)
                .frame(minWidth: timeFieldWidth)
                .textInputAutocapitalization(.characters)
            Button {
                addPoint(on: day, body: dayPointDraft[day] ?? "", timeText: dayPointTime[day] ?? "")
                dayPointDraft[day] = ""
                dayPointTime[day] = ""
            } label: {
                Image(systemName: "plus.circle.fill")
                    .foregroundStyle(canAdd ? ChronosColors.brandPrimary : .secondary)
            }
            .buttonStyle(.plain)
            .disabled(!canAdd)
            .accessibilityLabel("Add point")
        }
        .padding(.leading, ChronosSpacing.small)
    }
}

// MARK: - JournalEditorTarget
//
// Identifies what the editor sheet is composing — a new entry for `date` (entry == nil) or an edit of
// an existing `entry`. Mirrors Android's (composing, editingEntryId, composeDate) sheet state. Used
// with `.sheet(item:)`, so it is Identifiable; the id distinguishes a fresh compose-for-day from an
// edit so reopening for a different day/entry re-presents the sheet.
private struct JournalEditorTarget: Identifiable {
    let date: Date
    let entry: JournalEntry?
    var id: String { entry?.id ?? "new-\(date.timeIntervalSince1970)" }
}

// MARK: - JournalHistoryCard

private struct JournalHistoryCard: View {
    let entry: JournalEntry
    let minuteOfDay: Int?
    /// Tapping the card opens the editor for this entry (Android `onClick = { openEdit(entry) }`).
    let onTap: () -> Void
    let onDelete: () -> Void
    @State private var expanded = false

    private var mood: JournalMood { JournalMood(rawValue: entry.moodRating) ?? .unset }
    private var isWorkout: Bool { entry.id.hasPrefix(journalWorkoutIdPrefix) }
    private var parts: JournalBodyParts { journalParseBody(entry.body) }

    var body: some View {
        ChronosGlassCard(tint: isWorkout ? ChronosColors.brandSecondary : nil) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                HStack(spacing: ChronosSpacing.small) {
                    if isWorkout {
                        Image(systemName: "figure.run").foregroundStyle(ChronosColors.brandSecondary)
                    }
                    if let minute = minuteOfDay {
                        Text(formatDisplayMinute(minute))
                            .font(.chronosCaption).foregroundStyle(.secondary)
                    } else {
                        Text(entry.entryDate.formatted(.dateTime.hour().minute()))
                            .font(.chronosCaption).foregroundStyle(.secondary)
                    }
                    if mood != .unset {
                        Text(mood.emoji).font(.chronosCaption)
                    }
                    if !entry.isPrimary && !isWorkout {
                        Text("point")
                            .font(.chronosCaption)
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 6).padding(.vertical, 1)
                            .background(Color.secondary.opacity(0.14), in: Capsule())
                    }
                    Spacer()
                    if entry.streakDay > 0 && entry.isPrimary {
                        Label("\(entry.streakDay)", systemImage: "flame.fill")
                            .font(.chronosCaption)
                            .foregroundStyle(ChronosColors.brandAccent)
                    }
                }

                if !parts.mainNote.isEmpty {
                    Text(parts.mainNote)
                        .font(.chronosBody)
                        .lineLimit(expanded ? nil : 4)
                }

                // Sub-note highlights as a bullet list.
                if !parts.subNotes.isEmpty {
                    VStack(alignment: .leading, spacing: 2) {
                        ForEach(Array(parts.subNotes.enumerated()), id: \.offset) { _, note in
                            HStack(alignment: .top, spacing: ChronosSpacing.small) {
                                Text("•").foregroundStyle(.secondary)
                                Text(note).font(.chronosBody)
                            }
                        }
                    }
                }

                if isLong {
                    Button(expanded ? "Show less" : "Show more") {
                        withAnimation(ChronosMotion.smooth) { expanded.toggle() }
                    }
                    .font(.chronosCaption)
                    .buttonStyle(.plain)
                    .foregroundStyle(ChronosColors.brandPrimary)
                }

                if !entry.photoUris.isEmpty {
                    savedPhotoStrip
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .contentShape(Rectangle())
        // Tap to edit (skipped for read-only workout points). The expand/collapse "Show more" button
        // has its own hit target, so a long entry can still be expanded without opening the editor.
        .onTapGesture { onTap() }
        // The history list lives in a ScrollView (not a List), so `.swipeActions` wouldn't fire here;
        // a context menu is the portable delete affordance.
        .contextMenu {
            Button(role: .destructive, action: onDelete) {
                Label("Delete entry", systemImage: "trash")
            }
        }
    }

    private var isLong: Bool {
        parts.mainNote.count > 220 || parts.mainNote.filter { $0 == "\n" }.count >= 4
    }

    private var savedPhotoStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: ChronosSpacing.small) {
                ForEach(entry.photoUris, id: \.self) { path in
                    if let url = URL(string: path),
                       let data = try? Data(contentsOf: url),
                       let uiImage = UIImage(data: data) {
                        Image(uiImage: uiImage)
                            .resizable()
                            .scaledToFill()
                            .frame(width: 56, height: 56)
                            .clipShape(RoundedRectangle(cornerRadius: ChronosRadius.extraSmall, style: .continuous))
                    }
                }
            }
        }
    }
}

// MARK: - JournalInsightEngine
//
// On-device AI reflection over recent journal entries with a deterministic offline fallback.
// Uses Foundation Models (iOS 27 `SystemLanguageModel` / `LanguageModelSession`) when Apple
// Intelligence is available; otherwise it falls back to a local heuristic (average mood + recurring
// word themes) so the card is always useful. The AI prompt is built by ChronosCore's
// `buildJournalInsightPrompt` for parity with Android.

@MainActor
@Observable
final class JournalInsightEngine {
    enum State: Equatable {
        case idle
        case loading
        /// `fromAi` is false when the deterministic offline heuristic produced the text.
        case ready(String, fromAi: Bool)
    }

    private(set) var state: State = .idle

    private var canUseModel: Bool {
        if case .available = SystemLanguageModel.default.availability { return true }
        return false
    }

    func dismiss() { state = .idle }

    func generate(from entries: [JournalEntryRecord], today: Date, calendar: Calendar) async {
        guard !entries.isEmpty else { return }
        state = .loading

        if canUseModel {
            let prompt = buildJournalInsightPrompt(entries: entries, today: today, calendar: calendar)
            let session = LanguageModelSession(
                instructions: Instructions {
                    "You are a warm, concise journaling companion. From the supplied reflections only, "
                    + "note any patterns in mood or recurring themes in 2-3 short sentences, then offer "
                    + "one gentle, encouraging suggestion. Avoid clinical or therapy language."
                })
            do {
                let response = try await session.respond(
                    to: prompt,
                    options: GenerationOptions(temperature: GenerationProfile.balanced.temperature))
                let text = response.content.trimmingCharacters(in: .whitespacesAndNewlines)
                if !text.isEmpty {
                    state = .ready(text, fromAi: true)
                    return
                }
            } catch {
                // fall through to the offline heuristic
            }
        }

        state = .ready(Self.offlineInsight(from: entries), fromAi: false)
    }

    /// Deterministic, no-network reflection: average mood band + the most-recurring meaningful word.
    static func offlineInsight(from entries: [JournalEntryRecord]) -> String {
        let recent = Array(entries.prefix(14))
        let ratings = recent.compactMap(\.dayRating)
        var lines: [String] = []

        if !ratings.isEmpty {
            let avg = Double(ratings.reduce(0, +)) / Double(ratings.count)
            let band: String
            switch avg {
            case ..<2.0: band = "These days have felt heavy"
            case ..<3.0: band = "Things have felt a bit low lately"
            case ..<4.0: band = "Your days have felt steady"
            default: band = "Your days have felt good"
            }
            lines.append("\(band) — average mood \(String(format: "%.1f", avg))/5 across \(ratings.count) day\(ratings.count == 1 ? "" : "s").")
        }

        if let theme = topTheme(in: recent) {
            lines.append("\"\(theme)\" keeps coming up in what you write.")
        }

        lines.append("Take a moment for one small thing that recharges you tonight.")
        return lines.joined(separator: " ")
    }

    /// The most frequent meaningful (4+ letter, non-stopword) lowercased word across the entries.
    private static func topTheme(in entries: [JournalEntryRecord]) -> String? {
        let stop: Set<String> = ["that", "this", "with", "have", "today", "from", "were", "your",
                                 "about", "they", "what", "when", "just", "really", "could", "would",
                                 "there", "their", "them", "then", "than", "been", "felt", "feel",
                                 "more", "some", "much", "very", "into", "over", "after", "still"]
        var counts: [String: Int] = [:]
        for entry in entries {
            let words = entry.body.lowercased()
                .components(separatedBy: CharacterSet.alphanumerics.inverted)
                .filter { $0.count >= 4 && !stop.contains($0) }
            for word in words { counts[word, default: 0] += 1 }
        }
        return counts.filter { $0.value >= 2 }.max { $0.value < $1.value }?.key
    }
}

// MARK: - JournalWorkoutImporter
//
// The iOS-native, read-only HealthKit workout importer — the journal analogue of Android's
// JournalViewModel.importWorkouts (reads Health Connect exercises and writes `hc-workout-` journal
// points). It queries `.workoutType()` over the trailing 30 days and upserts one secondary, timed
// `JournalEntry` per workout, stamping the `journalWorkoutIdPrefix` id so `isJournalWorkoutEntry`
// recognises it and re-runs never duplicate. The point's time-of-day is carried on `createdAt`.

@Observable
@MainActor
final class JournalWorkoutImporter {
    enum Availability: Equatable {
        case available
        case needsAuthorization
        case unavailable
    }

    struct ImportResult: Equatable {
        var inserted = 0
        var skipped = 0
    }

    private let store = HKHealthStore()
    private(set) var availability: Availability = .unavailable
    private(set) var isImporting = false
    private(set) var lastResult: ImportResult?

    private var workoutType: HKWorkoutType { HKObjectType.workoutType() }

    func refreshAvailability() {
        guard HKHealthStore.isHealthDataAvailable() else {
            availability = .unavailable
            return
        }
        switch store.authorizationStatus(for: workoutType) {
        case .sharingAuthorized: availability = .available
        case .notDetermined, .sharingDenied: availability = .needsAuthorization
        @unknown default: availability = .needsAuthorization
        }
    }

    func requestAuthorization() async {
        guard HKHealthStore.isHealthDataAvailable() else {
            availability = .unavailable
            return
        }
        do {
            try await store.requestAuthorization(toShare: [], read: [workoutType])
        } catch {
            // Leave as-is; the import will simply read nothing if access was refused.
        }
        refreshAvailability()
        // Reads keep status `.notDetermined` even after a grant — treat a completed prompt as ready.
        if availability == .needsAuthorization { availability = .available }
    }

    @discardableResult
    func importRecent(into context: ModelContext, days: Int = 30) async -> ImportResult {
        guard HKHealthStore.isHealthDataAvailable() else { return ImportResult() }
        isImporting = true
        defer { isImporting = false }

        let now = Date()
        let start = Calendar.current.date(byAdding: .day, value: -max(days, 1), to: now) ?? now
        let predicate = HKQuery.predicateForSamples(withStart: start, end: now, options: [])
        let workouts = await queryWorkouts(predicate: predicate)

        // Existing workout-point ids to avoid duplicates on re-run.
        let existing = ((try? context.fetch(FetchDescriptor<JournalEntry>())) ?? [])
            .filter { $0.id.hasPrefix(journalWorkoutIdPrefix) }
            .map(\.id)
        let existingIDs = Set(existing)

        var result = ImportResult()
        for workout in workouts {
            let id = journalWorkoutIdPrefix + workout.uuid.uuidString
            if existingIDs.contains(id) { result.skipped += 1; continue }
            let entry = JournalEntry(
                id: id,
                entryDate: workout.startDate,
                createdAt: workout.startDate,
                updatedAt: now,
                body: workoutLabel(workout),
                isPrimary: false,
                moodRating: 0,
                isWorkoutEntry: true)
            context.insert(entry)
            result.inserted += 1
        }
        try? context.save()
        lastResult = result
        return result
    }

    private func queryWorkouts(predicate: NSPredicate) async -> [HKWorkout] {
        await withCheckedContinuation { continuation in
            let sort = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: true)
            let query = HKSampleQuery(
                sampleType: workoutType, predicate: predicate,
                limit: HKObjectQueryNoLimit, sortDescriptors: [sort]
            ) { _, samples, _ in
                continuation.resume(returning: (samples as? [HKWorkout]) ?? [])
            }
            store.execute(query)
        }
    }

    /// A short human label for a workout point, e.g. "Running · 32 min".
    private func workoutLabel(_ workout: HKWorkout) -> String {
        let minutes = Int(workout.duration / 60)
        return "\(workout.workoutActivityType.displayName) · \(minutes) min"
    }
}

// MARK: - HKWorkoutActivityType labels

private extension HKWorkoutActivityType {
    var displayName: String {
        switch self {
        case .running: "Running"
        case .walking: "Walking"
        case .cycling: "Cycling"
        case .swimming: "Swimming"
        case .hiking: "Hiking"
        case .yoga: "Yoga"
        case .functionalStrengthTraining, .traditionalStrengthTraining: "Strength training"
        case .highIntensityIntervalTraining: "HIIT"
        case .coreTraining: "Core training"
        case .pilates: "Pilates"
        case .dance, .cardioDance: "Dance"
        case .elliptical: "Elliptical"
        case .rowing: "Rowing"
        case .stairClimbing, .stairs: "Stair climbing"
        case .tennis: "Tennis"
        case .basketball: "Basketball"
        case .soccer: "Soccer"
        case .golf: "Golf"
        default: "Workout"
        }
    }
}

// MARK: - CameraPickerView (UIViewControllerRepresentable)

/// Wraps UIImagePickerController with camera source for in-app photo capture.
/// Falls back gracefully on simulator (no camera hardware). Module-internal so the journal editor
/// sheet (a sibling file) can reuse it.
struct CameraPickerView: UIViewControllerRepresentable {
    @Environment(\.dismiss) private var dismiss
    var onCapture: (Data?) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = UIImagePickerController.isSourceTypeAvailable(.camera) ? .camera : .photoLibrary
        picker.allowsEditing = false
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: CameraPickerView

        init(parent: CameraPickerView) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController,
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            let image = info[.originalImage] as? UIImage
            let data = image?.jpegData(compressionQuality: 0.85)
            parent.onCapture(data)
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.onCapture(nil)
            parent.dismiss()
        }
    }
}

#Preview { JournalView().modelContainer(ChronosStore.previewContainer()) }
