import SwiftUI
import Observation
import ChronosCore

// MARK: - EditorLayoutStore
//
// Per-editor disclosure memory for `CardEditorScaffold`. Each card editor (task / medication /
// habit / goal / block / routine) is a "kind"; each collapsible field group is a "section". The store
// remembers, per kind:
//   • which sections the user pinned (always shown above "More options"),
//   • which sections the user hid (kept under "More options" until reopened),
//   • how often each section is opened — the adaptive signal that auto-promotes a frequently used
//     section so power users stop hunting for it under "More".
//
// Backed by the App-Group `UserDefaults` (same store as `ChronosSettings`) so the preference is
// device-local and survives relaunch. Mirrors the ChronosSettings pattern: `@Observable` stored
// dictionaries drive SwiftUI updates; every mutation persists immediately.

@Observable
final class EditorLayoutStore {
    /// Process-wide singleton — any editor sheet reads/writes the same disclosure memory.
    @MainActor static let shared = EditorLayoutStore()

    /// Opens needed before a section auto-surfaces above "More options" (adaptive promotion).
    static let autoSurfaceThreshold = 3

    @ObservationIgnored private let defaults: UserDefaults

    /// kind → pinned section ids.
    private var pinned: [String: Set<String>]
    /// kind → hidden section ids.
    private var hidden: [String: Set<String>]
    /// kind → (section id → open count).
    private var usage: [String: [String: Int]]

    @MainActor private init() {
        let d = UserDefaults(suiteName: ChronosStore.appGroup) ?? .standard
        self.defaults = d
        pinned = Self.loadSets(d, key: "editor.pinned")
        hidden = Self.loadSets(d, key: "editor.hidden")
        usage = (d.dictionary(forKey: "editor.usage") as? [String: [String: Int]]) ?? [:]
    }

    // MARK: Queries

    func isPinned(_ kind: String, _ id: String) -> Bool { pinned[kind]?.contains(id) ?? false }
    func isHidden(_ kind: String, _ id: String) -> Bool { hidden[kind]?.contains(id) ?? false }
    func opens(_ kind: String, _ id: String) -> Int { usage[kind]?[id] ?? 0 }

    /// A section adaptively surfaces once it has been opened enough times and isn't hidden.
    func isAutoSurfaced(_ kind: String, _ id: String) -> Bool {
        !isHidden(kind, id) && opens(kind, id) >= Self.autoSurfaceThreshold
    }

    // MARK: Mutations

    func togglePin(_ kind: String, _ id: String) {
        var s = pinned[kind] ?? []
        if s.contains(id) {
            s.remove(id)
        } else {
            s.insert(id)
            unhide(kind, id) // pinning a hidden section un-hides it
        }
        pinned[kind] = s
        persistSets()
    }

    func toggleHidden(_ kind: String, _ id: String) {
        var h = hidden[kind] ?? []
        if h.contains(id) {
            h.remove(id)
        } else {
            h.insert(id)
            // Hiding wins over pin: a section can't be both.
            pinned[kind]?.remove(id)
        }
        hidden[kind] = h
        persistSets()
    }

    /// Record that the user opened a section — the adaptive-promotion signal.
    func recordOpen(_ kind: String, _ id: String) {
        var m = usage[kind] ?? [:]
        m[id, default: 0] += 1
        usage[kind] = m
        defaults.set(usage, forKey: "editor.usage")
    }

    // MARK: Helpers

    private func unhide(_ kind: String, _ id: String) { hidden[kind]?.remove(id) }

    private func persistSets() {
        defaults.set(Self.encodeSets(pinned), forKey: "editor.pinned")
        defaults.set(Self.encodeSets(hidden), forKey: "editor.hidden")
    }

    /// `[String: Set<String>]` isn't a plist type; persist as `[String: [String]]`.
    private static func loadSets(_ d: UserDefaults, key: String) -> [String: Set<String>] {
        guard let raw = d.dictionary(forKey: key) as? [String: [String]] else { return [:] }
        return raw.mapValues(Set.init)
    }

    private static func encodeSets(_ sets: [String: Set<String>]) -> [String: [String]] {
        sets.mapValues { Array($0) }
    }
}
