import SwiftUI
import SwiftData
import UniformTypeIdentifiers

/// Settings → Data: export the whole store to a JSON file and restore it back. Ports the Android
/// full-export/restore. Restore is destructive (replaces current data) and is confirmed first.
struct DataManagementView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @State private var exporting = false
    @State private var importing = false
    @State private var document: ChronosBackupDocument?
    @State private var pendingRestore: ChronosBackup?
    @State private var message: String?
    @State private var cloudSync = ChronosStore.cloudSyncEnabled

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

                    Button {
                        importing = true
                    } label: { Label("Restore from file…", systemImage: "square.and.arrow.down") }
                } footer: {
                    Text("Export creates a single JSON file with every task, habit, goal, block, medication, journal entry, sleep night, and check-in. Restoring replaces all current data.")
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
                            message = ChronosAutoBackup.lastResult
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

                if let message {
                    Section { Text(message).font(.chronosCaption).foregroundStyle(.secondary) }
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
                if case .success = result { message = "Backup exported." }
                else if case .failure(let e) = result { message = "Export failed: \(e.localizedDescription)" }
            }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.json]) { result in
                switch result {
                case .success(let url):
                    loadBackup(from: url)
                case .failure(let e):
                    message = "Import failed: \(e.localizedDescription)"
                }
            }
            .alert("Replace all data?", isPresented: Binding(
                get: { pendingRestore != nil }, set: { if !$0 { pendingRestore = nil } })) {
                Button("Cancel", role: .cancel) { pendingRestore = nil }
                Button("Restore", role: .destructive) {
                    if let backup = pendingRestore {
                        ChronosBackupService.restore(backup, into: context)
                        message = "Restored \(backup.tasks.count) tasks, \(backup.blocks.count) blocks, \(backup.habits.count) habits."
                    }
                    pendingRestore = nil
                }
            } message: {
                Text("This replaces everything currently in ChronosFlow with the contents of the backup file.")
            }
        }
    }

    private func refreshAutoBackup() {
        autoBackups = ChronosAutoBackup.listBackups()
        lastBackupDate = ChronosAutoBackup.lastBackupDate
        lastBackupResult = ChronosAutoBackup.lastResult
    }

    /// Decode a stored auto-backup and route it through the same confirm-then-restore alert as file restore.
    private func restore(_ entry: ChronosAutoBackup.Entry) {
        do {
            pendingRestore = try ChronosAutoBackup.decode(entry)
        } catch {
            message = "Couldn't read snapshot: \(error.localizedDescription)"
        }
    }

    private func loadBackup(from url: URL) {
        guard url.startAccessingSecurityScopedResource() else {
            message = "Couldn't access the selected file."; return
        }
        defer { url.stopAccessingSecurityScopedResource() }
        do {
            let data = try Data(contentsOf: url)
            let decoder = JSONDecoder(); decoder.dateDecodingStrategy = .iso8601
            pendingRestore = try decoder.decode(ChronosBackup.self, from: data)
        } catch {
            message = "Couldn't read backup: \(error.localizedDescription)"
        }
    }
}
