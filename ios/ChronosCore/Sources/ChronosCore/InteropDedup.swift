import Foundation

// Import-only interop dedup — Foundation-only port of InteropSyncManager.kt's task-mirroring pass.
// Pure and deterministic: no wall-clock reads, no I/O. The caller fetches RemoteInteropTask rows
// from the peer (DevTime/Meridian) and feeds them here; the result is the exact set of tasks to
// upsert, the deterministic ids to keep, and per-task reminder trigger instants.
//
// Determinism MUST match Android byte-for-byte:
//   1. CAP first:  take(maxInteropTasks)  — keep the first N rows in arrival order.
//   2. DEDUP:      distinctBy { title.trim().lowercased() + "|" + (dueAtMillis ?: "none") }
//                  keeping the FIRST occurrence of each key, preserving order.
//   3. ID:         "interop:<peer>:<externalId>"
//   4. TITLE:      blank -> "(untitled)", then truncated to maxTitleLength.
//   5. EXTID:      truncated to maxExternalIdLength (nil stays nil).

/// A task row fetched from the peer's interop provider (mirrors InteropClient.RemoteTask).
public struct RemoteInteropTask: Sendable, Equatable {
    public let externalID: String
    public let title: String
    /// Due instant in epoch milliseconds, or nil if undated.
    public let dueAtMillis: Int64?
    public let isCompleted: Bool
    public let priority: Int?

    public init(externalID: String, title: String, dueAtMillis: Int64? = nil,
                isCompleted: Bool = false, priority: Int? = nil) {
        self.externalID = externalID
        self.title = title
        self.dueAtMillis = dueAtMillis
        self.isCompleted = isCompleted
        self.priority = priority
    }
}

/// A normalized task ready to mirror into ChronosFlow, with its deterministic interop id.
public struct ImportedInteropTask: Sendable, Equatable {
    /// Deterministic id: "interop:<peer>:<externalId>".
    public let id: String
    public let title: String
    public let isCompleted: Bool
    public let priority: Int
    public let dueAtMillis: Int64?
    /// Truncated external id persisted alongside the row (nil-safe, capped).
    public let externalID: String?

    public init(id: String, title: String, isCompleted: Bool, priority: Int,
                dueAtMillis: Int64?, externalID: String?) {
        self.id = id
        self.title = title
        self.isCompleted = isCompleted
        self.priority = priority
        self.dueAtMillis = dueAtMillis
        self.externalID = externalID
    }
}

/// One scheduled reminder for an imported task: which id, and the trigger instant (epoch millis).
public struct InteropReminder: Sendable, Equatable {
    public let id: String
    public let triggerAtMillis: Int64
    public let title: String

    public init(id: String, triggerAtMillis: Int64, title: String) {
        self.id = id
        self.triggerAtMillis = triggerAtMillis
        self.title = title
    }
}

/// The full deterministic result of a sync pass against the peer's tasks.
public struct InteropImportPlan: Sendable, Equatable {
    /// Tasks to upsert, in deterministic order (capped + deduped + normalized).
    public let tasks: [ImportedInteropTask]
    /// Whether the cap was hit (peer returned more than `maxInteropTasks`).
    public let capExceeded: Bool

    public init(tasks: [ImportedInteropTask], capExceeded: Bool) {
        self.tasks = tasks
        self.capExceeded = capExceeded
    }

    /// Deterministic ids to keep; anything previously imported and not in this set is stale.
    public var keepIDs: [String] { tasks.map(\.id) }

    /// Ids from `priorImportedIDs` that no longer exist upstream and should be pruned (sorted).
    public func staleIDs(priorImportedIDs: Set<String>) -> [String] {
        priorImportedIDs.subtracting(Set(keepIDs)).sorted()
    }
}

public enum InteropDedup {

    /// Dedup key for collapsing near-identical upstream rows. Mirrors Android's distinctBy key:
    /// `title.trim().lowercase() + "|" + (dueAt ?: "none")`.
    public static func dedupKey(title: String, dueAtMillis: Int64?) -> String {
        let normalizedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let due = dueAtMillis.map(String.init) ?? "none"
        return normalizedTitle + "|" + due
    }

    /// Cap (first N) then collapse-by-(title,due), keeping the first occurrence and preserving order.
    /// Returns the surviving raw rows plus whether the cap was exceeded.
    public static func capAndCollapse(
        _ raw: [RemoteInteropTask],
        maxTasks: Int = InteropContract.maxInteropTasks
    ) -> (kept: [RemoteInteropTask], capExceeded: Bool) {
        let capExceeded = raw.count > maxTasks
        let capped = Array(raw.prefix(maxTasks))
        var seen = Set<String>()
        var kept: [RemoteInteropTask] = []
        kept.reserveCapacity(capped.count)
        for task in capped {
            let key = dedupKey(title: task.title, dueAtMillis: task.dueAtMillis)
            if seen.insert(key).inserted {
                kept.append(task)
            }
        }
        return (kept, capExceeded)
    }

    /// Build the deterministic import plan: cap, collapse, then normalize id/title/externalId for each
    /// surviving row. Pure — does not schedule reminders (see `reminder`).
    public static func plan(
        _ raw: [RemoteInteropTask],
        peer: String = InteropContract.peer,
        maxTasks: Int = InteropContract.maxInteropTasks
    ) -> InteropImportPlan {
        let (kept, capExceeded) = capAndCollapse(raw, maxTasks: maxTasks)
        let tasks = kept.map { task -> ImportedInteropTask in
            let trimmedTitle = task.title.isBlank ? InteropContract.untitledTaskTitle : task.title
            return ImportedInteropTask(
                id: InteropContract.importedTaskID(externalID: task.externalID, peer: peer),
                title: String(trimmedTitle.prefix(InteropContract.maxTitleLength)),
                isCompleted: task.isCompleted,
                priority: task.priority ?? 0,
                dueAtMillis: task.dueAtMillis,
                externalID: String(task.externalID.prefix(InteropContract.maxExternalIdLength))
            )
        }
        return InteropImportPlan(tasks: tasks, capExceeded: capExceeded)
    }

    /// Compute the reminder for a single imported task, given the current instant (epoch millis).
    /// Mirrors InteropSyncManager.scheduleReminder:
    ///   - completed task   -> nil (caller cancels any existing alarm)
    ///   - undated task     -> nil
    ///   - lead > now       -> fire at (due - reminderLead)
    ///   - else due > now   -> fire at due
    ///   - else (past)      -> nil
    /// `now` is injected; this never reads the wall clock.
    public static func reminder(
        for task: ImportedInteropTask,
        now nowMillis: Int64,
        leadMillis: Int64 = InteropContract.reminderLeadMillis
    ) -> InteropReminder? {
        if task.isCompleted { return nil }
        guard let due = task.dueAtMillis else { return nil }
        let lead = due - leadMillis
        let triggerAt: Int64
        if lead > nowMillis {
            triggerAt = lead
        } else if due > nowMillis {
            triggerAt = due
        } else {
            return nil
        }
        let title = task.title.isBlank ? "Task" : task.title
        return InteropReminder(id: task.id, triggerAtMillis: triggerAt, title: title)
    }

    /// Convenience: reminders for every task in a plan (drops the ones that shouldn't fire),
    /// preserving plan order. `now` is injected.
    public static func reminders(
        for plan: InteropImportPlan,
        now nowMillis: Int64,
        leadMillis: Int64 = InteropContract.reminderLeadMillis
    ) -> [InteropReminder] {
        plan.tasks.compactMap { reminder(for: $0, now: nowMillis, leadMillis: leadMillis) }
    }
}

private extension String {
    /// Kotlin `isBlank()` parity: empty or whitespace-only.
    var isBlank: Bool {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}
