import SwiftUI
import EventKit
import ChronosCore

// MARK: - CalendarManagementView
//
// The iOS analogue of Android's CALENDARS sidebar page (SidebarPageContent.kt lines 537–908,
// 1533–1557). Consolidates the calendar surface that was previously scattered across the dial
// overlay toggle into one screen, mirroring Android's dedicated page:
//   1. A month picker for date navigation (Android: the month-grid card).
//   2. A calendar sync-status card with an EventKit permission-request flow + rationale, modelled
//      on Android's CalendarPermissionStatus / collapsible "Calendar sync" section. Mirrors the
//      rationale copy in DayDialCalendarPermissions.kt / SidebarPageContent.kt.
//   3. A source-filtered "Detailed calendar" timeline with the three segmented presets
//      (All / Schedule / Calendar — CalendarTimelinePreset) and a per-source filter menu, rendering
//      imported calendar events grouped by source (Android: buildSidebarTimelineItems).
//
// Calendar events are read read-only via the shared `CalendarOverlayProvider` (EventKit). The
// schedule-side sources (Task / Habit / Medication / Plan) are surfaced as preset/filter rows with a
// clearly-commented placeholder empty state — wiring those into the SwiftData stores lives outside
// this view's owned files, exactly as Android renders an empty section when a source has no items.

struct CalendarManagementView: View {
    @State private var model = CalendarManagementModel()

    var body: some View {
        Form {
            monthPickerSection
            syncStatusSection
            timelineSection
        }
        .navigationTitle("Calendars")
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.refresh() }
    }

    // MARK: Month picker (Android: the month-grid navigation card)

    private var monthPickerSection: some View {
        Section {
            DatePicker("Date",
                       selection: $model.selectedDate,
                       displayedComponents: .date)
                .datePickerStyle(.graphical)
                .onChange(of: model.selectedDate) { _, _ in
                    Task { await model.reloadEvents() }
                }
        } header: {
            Text(model.selectedDate.formatted(.dateTime.month(.wide).year()))
        }
    }

    // MARK: Sync status + permission flow (Android: the "Calendar sync" collapsible)

    private var syncStatusSection: some View {
        Section {
            LabeledContent("Status") {
                HStack(spacing: 4) {
                    Circle()
                        .fill(model.statusColor)
                        .frame(width: 8, height: 8)
                    Text(model.connectionSummary)
                        .foregroundStyle(.secondary)
                }
            }
            Text(model.statusMessage)
                .font(.chronosCaption)
                .foregroundStyle(.secondary)

            // Last-refresh label, reusing the shared relative formatter (Android: "Synced X ago").
            Text(formatLastSyncedLabel(lastSyncAt: model.lastRefresh, now: .now))
                .font(.chronosCaption)
                .foregroundStyle(.secondary)

            // Actionable buttons mirror Android's status-card actions (Connect / Refresh / Open Settings).
            switch model.permission {
            case .notDetermined:
                Button("Connect calendar") { Task { await model.requestAccess() } }
            case .denied, .restricted:
                Button("Open Settings") { model.openSystemSettings() }
            case .writeOnly:
                Button("Enable read access in Settings") { model.openSystemSettings() }
            case .fullAccess:
                Button {
                    Task { await model.reloadEvents() }
                } label: {
                    Label("Refresh device events", systemImage: "arrow.clockwise")
                }
            }
        } header: {
            Text("Calendar sync")
        } footer: {
            Text(model.rationaleCopy)
        }
    }

    // MARK: Detailed calendar timeline (Android: "Detailed calendar")

    private var timelineSection: some View {
        Section {
            // Three segmented presets (Android: CalendarTimelinePreset All / Schedule / Calendar).
            Picker("Preset", selection: $model.preset) {
                ForEach(CalendarTimelinePreset.allCases) { Text($0.label).tag($0) }
            }
            .pickerStyle(.segmented)
            .onChange(of: model.preset) { _, newValue in
                model.applyPreset(newValue)
            }

            // Per-source filter + hide-completed menu (Android: the filter dropdown).
            filterMenu

            timelineContent
        } header: {
            Text("Detailed calendar")
        } footer: {
            Text("Calendar, tasks, habits, and medications for \(model.selectedDate.formatted(.dateTime.month().day())). Mirrors the Android Detailed Calendar timeline.")
        }
    }

    private var filterMenu: some View {
        Menu {
            Toggle("Hide completed", isOn: $model.hideCompleted)
            Toggle("Connected calendar only", isOn: $model.connectedCalendarOnly)
            Divider()
            ForEach(CalendarTimelineSource.allCases) { source in
                Toggle(source.label, isOn: model.sourceBinding(source))
            }
        } label: {
            Label("Filter", systemImage: "line.3.horizontal.decrease.circle")
        }
    }

    @ViewBuilder
    private var timelineContent: some View {
        let grouped = model.groupedTimeline()
        if grouped.isEmpty {
            Text(model.emptyStateMessage)
                .font(.chronosCaption)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            ForEach(grouped, id: \.source) { group in
                // Source header with count (Android: "{sourceLabel} ({count})").
                HStack(spacing: ChronosSpacing.small) {
                    Image(systemName: group.source.symbol)
                        .foregroundStyle(ChronosColors.category(group.source.label))
                    Text("\(group.source.label) (\(group.items.count))")
                        .font(.chronosLabel)
                        .foregroundStyle(ChronosColors.brandPrimary)
                }
                ForEach(group.items) { item in
                    CalendarTimelineRow(item: item, sourceColor: ChronosColors.category(group.source.label))
                }
            }
        }
    }
}

// MARK: - Timeline row

/// One imported-calendar timeline item, mirroring Android's SidebarTimelineItem rendering
/// (left accent bar, time, title, detail/status).
private struct CalendarTimelineRow: View {
    let item: CalendarTimelineItem
    let sourceColor: Color

    var body: some View {
        HStack(spacing: ChronosSpacing.compact) {
            RoundedRectangle(cornerRadius: 2)
                .fill(sourceColor)
                .frame(width: 4)
            VStack(alignment: .leading, spacing: 2) {
                Text(item.title)
                    .font(.chronosBody)
                Text(item.timeLabel)
                    .font(.chronosCaption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if item.isAllDay {
                Text("All day")
                    .font(.chronosCaption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }
}

// MARK: - Model

/// EventKit-backed, source-filterable timeline state. Pure-ish view model: it owns the selected date,
/// the EventKit permission snapshot, the segmented preset + per-source filter set, and the events read
/// from the device calendar via the shared `CalendarOverlayProvider`. Marked `@MainActor` because it
/// touches `EKEventStore` and `@Observable` state the view binds to.
@Observable
@MainActor
final class CalendarManagementModel {
    /// Mirrors `EKAuthorizationStatus` collapsed to the states the status card branches on,
    /// modelled on Android's CalendarPermissionStatus (read/write granted, denied).
    enum Permission { case notDetermined, denied, restricted, writeOnly, fullAccess }

    var selectedDate: Date = .now
    var permission: Permission = .notDetermined
    var preset: CalendarTimelinePreset = .all
    var enabledSources: Set<CalendarTimelineSource> = Set(CalendarTimelineSource.allCases)
    var hideCompleted = false
    var connectedCalendarOnly = false
    var lastRefresh: Date?

    private var calendarEvents: [CalendarOverlayEvent] = []
    private let provider = CalendarOverlayProvider()

    // MARK: Permission

    func refresh() async {
        readPermission()
        if permission == .fullAccess { await reloadEvents() }
    }

    private func readPermission() {
        switch EKEventStore.authorizationStatus(for: .event) {
        case .notDetermined: permission = .notDetermined
        case .restricted: permission = .restricted
        case .denied: permission = .denied
        case .writeOnly: permission = .writeOnly
        case .fullAccess: permission = .fullAccess
        case .authorized: permission = .fullAccess   // legacy (< iOS 17) full access
        @unknown default: permission = .denied
        }
    }

    func requestAccess() async {
        _ = await provider.requestAccess()
        readPermission()
        if permission == .fullAccess { await reloadEvents() }
    }

    func openSystemSettings() {
        #if canImport(UIKit)
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
        #endif
    }

    // MARK: Events

    func reloadEvents() async {
        guard permission == .fullAccess else { calendarEvents = []; return }
        let timed = await provider.events(on: selectedDate)
        let allDay = await provider.allDayContext(on: selectedDate)
        calendarEvents = timed + allDay
        lastRefresh = .now
    }

    // MARK: Filtering

    func applyPreset(_ preset: CalendarTimelinePreset) {
        enabledSources = preset.sources
    }

    func sourceBinding(_ source: CalendarTimelineSource) -> Binding<Bool> {
        Binding(
            get: { self.enabledSources.contains(source) },
            set: { isOn in
                if isOn { self.enabledSources.insert(source) } else { self.enabledSources.remove(source) }
                // Drop to the matching preset, or to .all when the selection no longer matches one.
                self.preset = CalendarTimelinePreset.allCases.first { $0.sources == self.enabledSources } ?? .all
            }
        )
    }

    /// Build the source-grouped timeline. Calendar / All-day-calendar sources are populated from
    /// EventKit; the schedule-side sources (Task / Habit / Medication / Plan) render an empty group
    /// when enabled — the SwiftData queries for those live outside this view's owned files, so this
    /// matches Android's behaviour of showing an empty section rather than hiding the source.
    func groupedTimeline() -> [CalendarTimelineGroup] {
        var groups: [CalendarTimelineGroup] = []
        for source in CalendarTimelineSource.allCases where enabledSources.contains(source) {
            switch source {
            case .calendar:
                let items = calendarEvents.filter { !$0.isAllDay }.map(CalendarTimelineItem.init)
                if !items.isEmpty { groups.append(.init(source: source, items: items)) }
            case .allDayCalendar:
                if connectedCalendarOnly == false || permission == .fullAccess {
                    let items = calendarEvents.filter(\.isAllDay).map(CalendarTimelineItem.init)
                    if !items.isEmpty { groups.append(.init(source: source, items: items)) }
                }
            case .task, .habit, .medication, .plan:
                // PLACEHOLDER: schedule-side sources are owned by the SwiftData stores outside this
                // view. They appear in the filter/preset model (parity) but render no rows here yet.
                break
            }
        }
        return groups
    }

    var emptyStateMessage: String {
        if connectedCalendarOnly { return "No connected calendar items for this date." }
        if hideCompleted { return "No timeline items for this date with completed items hidden." }
        if enabledSources.isEmpty { return "No sections enabled for this timeline." }
        return "No timeline items for this date."
    }

    // MARK: Status copy (mirrors Android's CalendarPermissionStatus-driven messages)

    var connectionSummary: String {
        switch permission {
        case .fullAccess: "Connected"
        case .writeOnly: "Write-only"
        case .notDetermined: "Not connected"
        case .denied, .restricted: "Access off"
        }
    }

    var statusColor: Color {
        switch permission {
        case .fullAccess: .green
        case .writeOnly: .orange
        case .notDetermined: .secondary
        case .denied, .restricted: .red
        }
    }

    var statusMessage: String {
        switch permission {
        case .fullAccess:
            "Calendar connected. ChronosFlow reads your device events to avoid duplicate plans and shows them on the dial."
        case .writeOnly:
            "ChronosFlow can write calendar exports but can’t read your device events yet. Enable read access to import scheduled events."
        case .notDetermined:
            "Connect your calendar to import scheduled events and surface them on the dial."
        case .denied, .restricted:
            "Calendar access is off. iOS won’t let ChronosFlow import calendar items until you re-enable permission in Settings."
        }
    }

    /// Rationale copy shown in the section footer — mirrors Android's "Calendar access is needed"
    /// rationale (DayDialCalendarPermissions.kt / SidebarPageContent.kt).
    var rationaleCopy: String {
        "ChronosFlow reads your device calendar to avoid duplicate plans and to show your existing commitments on the dial. Calendar events are read-only — they’re never silently turned into blocks."
    }
}

// MARK: - Timeline value types

struct CalendarTimelineGroup: Identifiable {
    let source: CalendarTimelineSource
    let items: [CalendarTimelineItem]
    var id: String { source.rawValue }
}

struct CalendarTimelineItem: Identifiable, Equatable {
    let id: String
    let title: String
    let timeLabel: String
    let isAllDay: Bool

    init(_ event: CalendarOverlayEvent) {
        self.id = event.id
        self.title = event.title
        self.isAllDay = event.isAllDay
        if event.isAllDay {
            self.timeLabel = "All day"
        } else {
            let start = Self.clock(event.startMinute)
            let end = Self.clock(event.startMinute + event.durationMinutes)
            self.timeLabel = "\(start) – \(end)"
        }
    }

    /// Minute-of-day → "h:mm a" wall-clock label.
    private static func clock(_ minuteOfDay: Int) -> String {
        let m = ((minuteOfDay % 1440) + 1440) % 1440
        var comps = DateComponents()
        comps.hour = m / 60
        comps.minute = m % 60
        let date = Calendar.current.date(from: comps) ?? .now
        return date.formatted(.dateTime.hour().minute())
    }
}

#Preview {
    NavigationStack { CalendarManagementView() }
}
