import SwiftUI
import SwiftData
import UniformTypeIdentifiers
import ChronosCore

/// Settings → Data: export the whole store to a JSON file and restore it back. Ports the Android
/// full-export/restore. Restore offers two modes (BK04): "Replace all data" (destructive, confirmed
/// first) and "Only fill empty data" (non-destructive merge — never overwrites live entities), the
/// latter being the safe default for moving data onto a device that already has some.
struct DataManagementView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @State private var exporting = false
    @State private var importing = false
    @State private var document: ChronosBackupDocument?
    @State private var pendingRestore: ChronosBackup?
    @State private var message: String?
    @State private var cloudSync = ChronosStore.cloudSyncEnabled
    @State private var shareData: Data?
    @State private var isSharing = false

    /// Restore policy applied by both file restore and stored-snapshot restore (BK04). Bound to the
    /// picker as a Bool (the ChronosCore `RestoreMode` is value-typed but not `Hashable`, which a
    /// `Picker` tag requires) and mapped to `restoreMode` for the restore calls.
    @State private var replaceAllOnRestore = false
    private var restoreMode: RestoreMode { replaceAllOnRestore ? .destructive : .mergeIfEmpty }

    // Scheduled auto-backup state.
    @State private var autoBackupEnabled = false
    @State private var autoBackups: [ChronosAutoBackup.Entry] = []
    @State private var lastBackupDate: Date? = nil
    @State private var lastBackupResult: String? = nil

    var body: some View {
        NavigationStack {
            Form {
                Section("Backup") {
                    Button {
                        document = ChronosBackupDocument(backup: ChronosBackupService.export(from: context))
                        exporting = true
                    } label: { Label("Export all data (JSON)", systemImage: "square.and.arrow.up") }

                    Picker("Restore mode", selection: $replaceAllOnRestore) {
                        Text("Only fill empty data").tag(false)
                        Text("Replace all data").tag(true)
                    }
                    .pickerStyle(.menu)

                    Button {
                        importing = true
                    } label: { Label("Restore from file…", systemImage: "square.and.arrow.down") }
                } footer: {
                    Text(restoreMode == .mergeIfEmpty
                         ? "Export creates a single JSON file with every task, habit, goal, block, medication, journal entry, sleep night, and check-in. \"Only fill empty data\" merges a backup non-destructively — it adds data only to categories that are currently empty and never overwrites what's already here. Ideal for moving data onto a device you've started using."
                         : "Export creates a single JSON file with every task, habit, goal, block, medication, journal entry, sleep night, and check-in. \"Replace all data\" wipes everything on this device first, then restores the backup.")
                }

                Section("Automatic backup") {
                    Toggle("Automatic daily backup", isOn: $autoBackupEnabled)
                        .onChange(of: autoBackupEnabled) { _, on in
                            ChronosAutoBackup.isEnabled = on
                            refreshAutoBackup()
                        }
                    LabeledContent("Last backup") {
                        Text(lastBackupDate.map { $0.formatted(date: .abbreviated, time: .shortened) } ?? "Never")
                            .foregroundStyle(.secondary)
                    }
                    if let result = lastBackupResult {
                        Text(result).font(.chronosCaption).foregroundStyle(.secondary)
                    }
                    Button {
                        Task {
                            _ = await ChronosAutoBackup.runBackup()
                            refreshAutoBackup()
                            withAnimation(ChronosMotion.snappy) { message = ChronosAutoBackup.lastResult }
                        }
                    } label: { Label("Back up now", systemImage: "arrow.clockwise") }
                } footer: {
                    Text("Keeps a rolling set of the \(ChronosAutoBackup.maxRetained) most recent snapshots inside the app's private storage. The system runs them roughly once a day in the background.")
                }

                if !autoBackups.isEmpty {
                    Section("Stored snapshots") {
                        ForEach(autoBackups) { entry in
                            Button {
                                restore(entry)
                            } label: {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(entry.date.formatted(date: .abbreviated, time: .shortened))
                                    Text(entry.fileName).font(.chronosCaption).foregroundStyle(.secondary)
                                }
                            }
                        }
                        .onDelete { offsets in
                            offsets.map { autoBackups[$0] }.forEach(ChronosAutoBackup.delete)
                            refreshAutoBackup()
                        }
                    } footer: {
                        Text("Tap a snapshot to restore it (replaces all current data). Swipe to delete.")
                    }
                }

                Section("Sync") {
                    Toggle("iCloud sync (CloudKit)", isOn: $cloudSync)
                        .onChange(of: cloudSync) { _, on in
                            ChronosSettings.shared.cloudSyncEnabled = on
                        }
                } footer: {
                    Text(cloudSync
                         ? "Your data mirrors across your devices via iCloud. Takes effect after you relaunch ChronosFlow. Requires the iCloud capability + container to be provisioned."
                         : "iCloud sync is off — your data stays on this device. Turn it on to mirror across your devices via iCloud (applies after relaunch). Until then, use Export/Restore to move data between devices.")
                }

                Section("Device transfer") {
                    Button {
                        let backup = ChronosBackupService.export(from: context)
                        let encoder = JSONEncoder(); encoder.dateEncodingStrategy = .iso8601
                        if let data = try? encoder.encode(backup) {
                            shareData = data
                            isSharing = true
                        }
                    } label: {
                        Label("Share backup to another device", systemImage: "arrow.right.circle.fill")
                    }

                    Button {
                        do {
                            let url = try ChronosDeviceTransfer.writeSnapshot(from: context)
                            show("Transfer snapshot saved to \(url.lastPathComponent). A new install that starts with no data will pick it up automatically on first launch.")
                        } catch {
                            show("Couldn't write transfer snapshot: \(error.localizedDescription)")
                        }
                    } label: {
                        Label("Prepare device-transfer snapshot", systemImage: "externaldrive.badge.timemachine")
                    }
                } footer: {
                    Text("Share backup opens the iOS share sheet (AirDrop, Files, Messages). \"Prepare device-transfer snapshot\" writes a portable copy inside the app's shared storage; a fresh install that starts empty restores it non-destructively on first launch (it never overwrites existing data).")
                }

                if let message {
                    Section { Text(message).font(.chronosCaption).foregroundStyle(.secondary) }
                        .transition(.opacity.combined(with: .move(edge: .bottom)))
                }
            }
            .navigationTitle("Data")
            .onAppear {
                autoBackupEnabled = ChronosAutoBackup.isEnabled
                refreshAutoBackup()
            }
            .toolbarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .fileExporter(isPresented: $exporting,
                          document: document,
                          contentType: .json,
                          defaultFilename: "ChronosFlow-Backup-\(Date.now.formatted(.iso8601.year().month().day()))") { result in
                if case .success = result { show("Backup exported.") }
                else if case .failure(let e) = result { show("Export failed: \(e.localizedDescription)") }
            }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.json]) { result in
                switch result {
                case .success(let url):
                    loadBackup(from: url)
                case .failure(let e):
                    show("Import failed: \(e.localizedDescription)")
                }
            }
            .alert(restoreMode == .destructive ? "Replace all data?" : "Restore backup?",
                   isPresented: Binding(
                get: { pendingRestore != nil }, set: { if !$0 { pendingRestore = nil } })) {
                Button("Cancel", role: .cancel) { pendingRestore = nil }
                Button(restoreMode == .destructive ? "Replace" : "Restore",
                       role: restoreMode == .destructive ? .destructive : nil) {
                    if let backup = pendingRestore {
                        let summary = ChronosBackupService.restore(backup, into: context, mode: restoreMode)
                        show(restoreMessage(for: summary))
                    }
                    pendingRestore = nil
                }
            } message: {
                Text(restoreMode == .destructive
                     ? "This replaces everything in ChronosFlow with: \(pendingRestore?.tasks.count ?? 0) tasks, \(pendingRestore?.blocks.count ?? 0) blocks, \(pendingRestore?.habits.count ?? 0) habits, \(pendingRestore?.journal.count ?? 0) journal entries."
                     : "This adds data only to categories that are currently empty. Categories that already have data on this device are left untouched.")
            }
            .sheet(isPresented: $isSharing) {
                if let data = shareData {
                    ShareSheet(items: [data as Any])
                }
            }
        }
    }

    /// Animate result-message changes so the footer Section's insertion/removal eases in/out.
    private func show(_ text: String) {
        withAnimation(ChronosMotion.snappy) { message = text }
    }

    private func refreshAutoBackup() {
        autoBackups = ChronosAutoBackup.listBackups()
        lastBackupDate = ChronosAutoBackup.lastBackupDate
        lastBackupResult = ChronosAutoBackup.lastResult
    }

    /// User-facing result string for a restore, tailored to the mode (BK04). Destructive reports the
    /// full restored set; mergeIfEmpty reports what was filled and what was left untouched.
    private func restoreMessage(for summary: ChronosBackupService.RestoreSummary) -> String {
        if restoreMode == .destructive {
            return "Restored \(summary.insertedRows) records across \(summary.filledTables.count) categories."
        }
        if summary.nothingRestored {
            return summary.skippedNonEmptyTables.isEmpty
                ? "Nothing to restore — the backup was empty."
                : "Nothing added — every category on this device already has data, so the backup was left unused."
        }
        var msg = "Added \(summary.insertedRows) records to \(summary.filledTables.count) empty categories."
        if !summary.skippedNonEmptyTables.isEmpty {
            msg += " Left \(summary.skippedNonEmptyTables.count) category(ies) untouched (already had data)."
        }
        return msg
    }

    /// Decode a stored auto-backup and route it through the same confirm-then-restore alert as file restore.
    private func restore(_ entry: ChronosAutoBackup.Entry) {
        do {
            pendingRestore = try ChronosAutoBackup.decode(entry)
        } catch {
            show("Couldn't read snapshot: \(error.localizedDescription)")
        }
    }

    private func loadBackup(from url: URL) {
        guard url.startAccessingSecurityScopedResource() else {
            show("Couldn't access the selected file."); return
        }
        defer { url.stopAccessingSecurityScopedResource() }
        do {
            let data = try Data(contentsOf: url)
            let decoder = JSONDecoder(); decoder.dateDecodingStrategy = .iso8601
            pendingRestore = try decoder.decode(ChronosBackup.self, from: data)
        } catch {
            show("Couldn't read backup: \(error.localizedDescription)")
        }
    }
}

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}
