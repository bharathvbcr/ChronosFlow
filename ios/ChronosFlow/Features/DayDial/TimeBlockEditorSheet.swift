import SwiftUI
import SwiftData
import EventKit
import ChronosCore

/// Create / edit a time block. Used both from a dial tap (create) and a block tap (edit).
///
/// Calendar parity (mirrors Android `DayDialBlockDelegate` + `CalendarEventRepositoryImpl`):
///  - **Provenance guard:** blocks imported from the device calendar (`provenance == .calendar`)
///    are read-only here. Editing them is disabled and deletion is gated behind a confirmation,
///    because their link points at the *user's own* event — we must never write back to it.
///  - **Export to Calendar:** user-created blocks can be pushed to the device calendar via EventKit.
///    The decision (save / update / delete / no-op) is owned by ChronosCore's `exportIntent` /
///    `deleteIntent`; EventKit is wired here. The exported event's identifier is persisted so later
///    edits update (not duplicate) and deletes clean up.
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

    // Export-to-calendar state.
    @State private var exportEnabled: Bool
    @State private var exportEventID: String?
    @State private var calendarErrorMessage: String?
    @State private var showDeleteImportedConfirm = false

    private let store = EKEventStore()
    private let categories = ["FOCUS", "WORK", "STUDY", "BREAK", "MEAL", "ROUTINE", "EXERCISE", "SLEEP"]

    /// True when the block originated from the device calendar — it is read-only here.
    private var isImportedBlock: Bool {
        guard let existing else { return false }
        return isImportedCalendarBlock(provenance: existing.provenance.rawValue)
    }

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
        let storedEventID = block.flatMap { Self.storedExportEventID(for: $0.id) }
        _exportEventID = State(initialValue: storedEventID)
        _exportEnabled = State(initialValue: storedEventID != nil)
    }

    var body: some View {
        CardEditorScaffold(
            kind: "block",
            navTitle: existing == nil ? "New block" : (isImportedBlock ? "Calendar event" : "Edit block"),
            chips: blockChips,
            sections: blockSections,
            footer: existing != nil ? AnyView(deleteFooter) : nil,
            saveDisabled: title.isEmpty || isImportedBlock,
            onCancel: { dismiss() },
            onSave: { Task { await save() } }
        ) {
            blockHeader
        }
        .confirmationDialog(
            "Remove this calendar event from your plan?",
            isPresented: $showDeleteImportedConfirm,
            titleVisibility: .visible
        ) {
            Button("Remove from plan", role: .destructive) {
                if let existing { deleteBlock(existing) }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This only removes it from ChronosFlow. Your device calendar event won't change.")
        }
        .alert("Calendar", isPresented: Binding(
            get: { calendarErrorMessage != nil },
            set: { if !$0 { calendarErrorMessage = nil } }
        )) {
            Button("OK", role: .cancel) { calendarErrorMessage = nil }
        } message: {
            Text(calendarErrorMessage ?? "")
        }
    }

    // MARK: - Scaffold pieces

    @ViewBuilder private var blockHeader: some View {
        if isImportedBlock {
            Label("Imported from your calendar. Edit it in the Calendar app — changes here won't sync back.",
                  systemImage: "calendar.badge.exclamationmark")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        TextField("Title", text: $title).disabled(isImportedBlock)
        Picker("Category", selection: $category) {
            ForEach(categories, id: \.self) { c in
                Label(c.capitalized, systemImage: "circle.fill")
                    .foregroundStyle(ChronosColors.category(c))
                    .tag(c)
            }
        }
        .disabled(isImportedBlock)
    }

    private var blockChips: [EditorChip] {
        var chips = [EditorChip(id: "planner", systemImage: "slider.horizontal.3",
                                title: "Planner", value: plannerChipValue)]
        if !isImportedBlock {
            chips.append(EditorChip(id: "calendar", systemImage: "calendar", title: "Calendar",
                value: exportEnabled ? "Exporting" : nil,
                onClear: { withAnimation(ChronosMotion.snappy) { exportEnabled = false } }))
        }
        return chips
    }

    private var plannerChipValue: String? {
        var parts: [String] = []
        if flexibility != .movable { parts.append(flexibility.rawValue.capitalized) }
        if energy != .moderate { parts.append(energy.label) }
        if isLocked { parts.append("Locked") }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    private var blockSections: [EditorSection] {
        var list: [EditorSection] = [
            EditorSection(id: "time", title: "Time", systemImage: "clock", alwaysPrimary: true) { timeRows },
            EditorSection(id: "planner", title: "Planner", systemImage: "slider.horizontal.3",
                          hasValue: plannerChipValue != nil) { plannerRows },
        ]
        if !isImportedBlock {
            list.append(EditorSection(id: "calendar", title: "Calendar", systemImage: "calendar",
                                      hasValue: exportEnabled) { calendarRows })
        }
        return list
    }

    @ViewBuilder private var timeRows: some View {
        Group {
            LabeledContent("Start", value: Int(startMinute).clockTime)
            Slider(value: $startMinute, in: 0...1425, step: 15)
                // A bare Slider speaks its value as a percentage; announce the real clock time (§7).
                .accessibilityLabel("Start time")
                .accessibilityValue(Int(startMinute).clockTime)
            LabeledContent("Duration", value: "\(Int(duration)) min")
            Slider(value: $duration, in: 15...720, step: 15)
                .accessibilityLabel("Duration")
                .accessibilityValue("\(Int(duration)) minutes")
            LabeledContent("Ends", value: ((Int(startMinute) + Int(duration)) % 1440).clockTime)
        }
        .disabled(isImportedBlock)
    }

    @ViewBuilder private var plannerRows: some View {
        Group {
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
        .disabled(isImportedBlock)
    }

    @ViewBuilder private var calendarRows: some View {
        Toggle("Export to Calendar", isOn: $exportEnabled)
        if exportEnabled {
            Text("Adds this block to your device calendar and keeps it in sync when you edit it.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    @ViewBuilder private var deleteFooter: some View {
        if let existing {
            Button(isImportedBlock ? "Remove from plan" : "Delete block", role: .destructive) {
                if isImportedBlock {
                    showDeleteImportedConfirm = true
                } else {
                    deleteBlock(existing)
                }
            }
            if isImportedBlock {
                Text("Removing only hides it from your plan. Your calendar event is untouched.")
                    .font(.chronosCaption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    // MARK: - Save

    private func save() async {
        // Provenance guard: never persist edits to imported calendar blocks (mirrors Android).
        guard !isImportedBlock else { dismiss(); return }

        if let existing {
            existing.title = title
            existing.category = category
            existing.updateStart(Int(startMinute))
            existing.updateDuration(Int(duration))
            existing.flexibility = flexibility
            existing.energyLevel = energy
            existing.isLocked = isLocked
            try? context.save()
            await syncCalendarExport(for: existing)
        } else {
            let block = TimeBlock(
                date: date, title: title, category: category,
                startMinuteOfDay: Int(startMinute), durationMinutes: Int(duration),
                flexibility: flexibility, energyLevel: energy, isLocked: isLocked
            )
            context.insert(block)
            try? context.save()
            await syncCalendarExport(for: block)
        }
        if calendarErrorMessage == nil { dismiss() }
    }

    private func deleteBlock(_ block: TimeBlock) {
        Task {
            await removeCalendarExport(for: block)
            context.delete(block)
            try? context.save()
            dismiss()
        }
    }

    // MARK: - EventKit export (driven by ChronosCore intents)

    /// Build the ChronosCore export input from the current block state.
    private func exportableBlock(for block: TimeBlock) -> ExportableBlock {
        ExportableBlock(
            title: block.title,
            date: block.date,
            startMinute: block.startMinuteOfDay,
            durationMinutes: block.durationMinutes,
            timezone: block.timezoneIdentifier,
            provenance: block.provenance.rawValue,
            eventID: exportEventID
        )
    }

    /// On save: insert/update/remove the device-calendar event so it matches the export toggle.
    /// ChronosCore decides the intent; EventKit performs it. The exported identifier is persisted.
    private func syncCalendarExport(for block: TimeBlock) async {
        // Toggle OFF (and was previously exported): remove the event we own, then forget the id.
        if !exportEnabled {
            if exportEventID != nil { await removeCalendarExport(for: block) }
            return
        }

        guard await requestCalendarAccess() else {
            calendarErrorMessage = "Calendar access is needed to export this block. Enable it in Settings."
            return
        }

        let intent = exportIntent(for: exportableBlock(for: block))
        switch intent {
        case .save(let fields):
            do {
                let event = EKEvent(eventStore: store)
                apply(fields, to: event)
                try store.save(event, span: .thisEvent, commit: true)
                exportEventID = event.eventIdentifier
                Self.setStoredExportEventID(event.eventIdentifier, for: block.id)
            } catch {
                calendarErrorMessage = "Couldn't export to your calendar: \(error.localizedDescription)"
            }
        case .update(let eventID, let fields):
            guard let event = store.event(withIdentifier: eventID) else {
                // The event was deleted out from under us — re-create it.
                do {
                    let fresh = EKEvent(eventStore: store)
                    apply(fields, to: fresh)
                    try store.save(fresh, span: .thisEvent, commit: true)
                    exportEventID = fresh.eventIdentifier
                    Self.setStoredExportEventID(fresh.eventIdentifier, for: block.id)
                } catch {
                    calendarErrorMessage = "Couldn't update your calendar event: \(error.localizedDescription)"
                }
                return
            }
            do {
                apply(fields, to: event)
                try store.save(event, span: .thisEvent, commit: true)
            } catch {
                calendarErrorMessage = "Couldn't update your calendar event: \(error.localizedDescription)"
            }
        case .delete, .noOp:
            // syncCalendarExport never deletes; that's removeCalendarExport's job. noOp = nothing to do.
            break
        }
    }

    /// On delete (or export toggled off): remove the device-calendar event ChronosFlow owns.
    /// Provenance guard lives in `deleteIntent` — imported blocks resolve to `.noOp`.
    private func removeCalendarExport(for block: TimeBlock) async {
        let intent = deleteIntent(for: exportableBlock(for: block))
        guard case .delete(let eventID) = intent else {
            Self.setStoredExportEventID(nil, for: block.id)
            exportEventID = nil
            return
        }
        guard await requestCalendarAccess() else { return }
        if let event = store.event(withIdentifier: eventID) {
            try? store.remove(event, span: .thisEvent, commit: true)
        }
        Self.setStoredExportEventID(nil, for: block.id)
        exportEventID = nil
    }

    /// Copy ChronosCore-mapped fields onto an EKEvent (no business logic here — pure adapter).
    private func apply(_ fields: CalendarEventFields, to event: EKEvent) {
        event.title = fields.title
        event.notes = fields.notes
        event.startDate = fields.start
        event.endDate = fields.end
        event.isAllDay = fields.isAllDay
        event.timeZone = resolvedTimeZone(fields.timezoneID)
        if event.calendar == nil {
            event.calendar = store.defaultCalendarForNewEvents
        }
    }

    private func requestCalendarAccess() async -> Bool {
        if EKEventStore.authorizationStatus(for: .event) == .fullAccess { return true }
        return (try? await store.requestFullAccessToEvents()) ?? false
    }

    // MARK: - Exported-event identifier persistence

    /// EventKit identifiers are Strings, but `TimeBlock.calendarEventID` is `Int64?` (mirrors the
    /// Android Room column) so we persist the exported event's identifier in UserDefaults keyed by
    /// block id. This keeps export idempotent (update vs duplicate) without a model migration.
    private static let exportKeyPrefix = "calendar.export.eventID."

    private static func storedExportEventID(for blockID: String) -> String? {
        UserDefaults.standard.string(forKey: exportKeyPrefix + blockID)
    }

    private static func setStoredExportEventID(_ eventID: String?, for blockID: String) {
        let key = exportKeyPrefix + blockID
        if let eventID { UserDefaults.standard.set(eventID, forKey: key) }
        else { UserDefaults.standard.removeObject(forKey: key) }
    }
}
