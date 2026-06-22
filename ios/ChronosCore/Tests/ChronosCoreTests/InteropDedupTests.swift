import XCTest
@testable import ChronosCore

/// Mirrors the Android determinism of InteropSyncManager.kt's import pass: cap-then-collapse,
/// deterministic ids, title/externalId normalization, and reminder-lead computation. Times are
/// injected (no wall clock) so every case is reproducible.
final class InteropDedupTests: XCTestCase {

    // A fixed reference instant (epoch millis) used wherever "now" is needed.
    private let now: Int64 = 1_000_000_000_000 // 2001-09-09T01:46:40Z, arbitrary but fixed.

    private func task(_ ext: String, _ title: String, due: Int64? = nil,
                      completed: Bool = false, priority: Int? = nil) -> RemoteInteropTask {
        RemoteInteropTask(externalID: ext, title: title, dueAtMillis: due,
                          isCompleted: completed, priority: priority)
    }

    // MARK: Contract constants are byte-compatible with Android

    func testPeerPackageMatchesAndroid() {
        XCTAssertEqual(InteropContract.peer, "com.Meridian.VBCR")
        XCTAssertEqual(InteropContract.chronosflowPackage, "com.ChronosFlow.VBCR")
    }

    func testTaskColumnsExactOrder() {
        XCTAssertEqual(InteropContract.taskColumns,
            ["external_id", "title", "notes", "due_at", "is_completed", "priority", "timezone", "updated_at"])
    }

    func testLimitsMatchAndroid() {
        XCTAssertEqual(InteropContract.maxInteropTasks, 500)
        XCTAssertEqual(InteropContract.maxTitleLength, 500)
        XCTAssertEqual(InteropContract.maxExternalIdLength, 128)
        XCTAssertEqual(InteropContract.reminderLeadMillis, 10 * 60 * 1000)
    }

    // MARK: Deterministic id — "interop:<peer>:<externalId>"

    func testImportedTaskID() {
        XCTAssertEqual(InteropContract.importedTaskID(externalID: "abc"),
                       "interop:com.Meridian.VBCR:abc")
    }

    func testPlanProducesDeterministicIDs() {
        let plan = InteropDedup.plan([task("x1", "A"), task("x2", "B")])
        XCTAssertEqual(plan.tasks.map(\.id),
            ["interop:com.Meridian.VBCR:x1", "interop:com.Meridian.VBCR:x2"])
        XCTAssertEqual(plan.keepIDs,
            ["interop:com.Meridian.VBCR:x1", "interop:com.Meridian.VBCR:x2"])
    }

    // MARK: Dedup key — title.trim().lowercase() + "|" + (due ?? "none")

    func testDedupKeyNormalizesTitle() {
        XCTAssertEqual(InteropDedup.dedupKey(title: "  Buy Milk  ", dueAtMillis: nil), "buy milk|none")
        XCTAssertEqual(InteropDedup.dedupKey(title: "Buy Milk", dueAtMillis: 42), "buy milk|42")
    }

    func testDedupKeyDistinguishesByDue() {
        XCTAssertNotEqual(
            InteropDedup.dedupKey(title: "T", dueAtMillis: 1),
            InteropDedup.dedupKey(title: "T", dueAtMillis: 2))
        // undated vs dated differ.
        XCTAssertNotEqual(
            InteropDedup.dedupKey(title: "T", dueAtMillis: nil),
            InteropDedup.dedupKey(title: "T", dueAtMillis: 0))
    }

    // MARK: Collapse-by-(title,due) keeps FIRST occurrence, preserves order

    func testCollapsesCaseAndWhitespaceVariantsKeepingFirst() {
        let raw = [
            task("first", "Buy milk", due: 100),
            task("dup", "  BUY MILK ", due: 100), // same key as first (trim + lowercase)
            task("keep", "Buy milk", due: 200),   // different due -> kept
            task("keep2", "Other", due: 100),     // different title -> kept
        ]
        let plan = InteropDedup.plan(raw)
        // First wins; second collapsed away. Order preserved.
        XCTAssertEqual(plan.tasks.map(\.externalID), ["first", "keep", "keep2"])
    }

    func testUndatedDuplicatesCollapseViaNoneKey() {
        let raw = [task("a", "Task"), task("b", "task"), task("c", "TASK ")]
        let plan = InteropDedup.plan(raw)
        XCTAssertEqual(plan.tasks.map(\.externalID), ["a"])
    }

    func testDistinctTitlesAllKept() {
        let raw = [task("a", "One"), task("b", "Two"), task("c", "Three")]
        XCTAssertEqual(InteropDedup.plan(raw).tasks.count, 3)
    }

    // MARK: Cap is applied BEFORE dedup (Android: take(N) then distinctBy)

    func testCapTakesFirstNThenDedups() {
        // 3 rows, cap=2 -> only first two survive the cap; the 3rd (a dup of #1) never seen.
        let raw = [task("a", "Same", due: 1), task("b", "Different", due: 1), task("c", "Same", due: 1)]
        let (kept, exceeded) = InteropDedup.capAndCollapse(raw, maxTasks: 2)
        XCTAssertTrue(exceeded)
        XCTAssertEqual(kept.map(\.externalID), ["a", "b"])
    }

    func testCapBeforeDedupCanDropUniqueRowReachableOnlyAfterCap() {
        // If dedup ran first then capped, "c" (unique) could survive; Android caps first, so "c" is cut.
        let raw = [task("a", "X", due: 1), task("b", "X", due: 1), task("c", "Y", due: 1)]
        let plan = InteropDedup.plan(raw, maxTasks: 2)
        // cap -> [a, b]; dedup collapses b into a -> [a]. "c" never considered.
        XCTAssertEqual(plan.tasks.map(\.externalID), ["a"])
        XCTAssertTrue(plan.capExceeded)
    }

    func testCapNotExceededWhenUnderLimit() {
        let plan = InteropDedup.plan([task("a", "A")], maxTasks: 500)
        XCTAssertFalse(plan.capExceeded)
    }

    func testEmptyInput() {
        let plan = InteropDedup.plan([])
        XCTAssertTrue(plan.tasks.isEmpty)
        XCTAssertFalse(plan.capExceeded)
        XCTAssertTrue(plan.keepIDs.isEmpty)
    }

    // MARK: Title normalization — blank -> "(untitled)", truncate to 500

    func testBlankTitleBecomesUntitled() {
        XCTAssertEqual(InteropDedup.plan([task("a", "   ")]).tasks.first?.title, "(untitled)")
        XCTAssertEqual(InteropDedup.plan([task("b", "")]).tasks.first?.title, "(untitled)")
    }

    func testTitleTruncatedTo500() {
        let long = String(repeating: "z", count: 600)
        let title = InteropDedup.plan([task("a", long)]).tasks.first?.title
        XCTAssertEqual(title?.count, 500)
    }

    // MARK: externalId truncation — capped to 128

    func testExternalIdTruncatedTo128() {
        let longExt = String(repeating: "e", count: 200)
        let stored = InteropDedup.plan([task(longExt, "A")]).tasks.first?.externalID
        XCTAssertEqual(stored?.count, 128)
        // The id, however, uses the full (untruncated) external id like Android's "interop:$origin:${t.externalId}".
        XCTAssertEqual(InteropDedup.plan([task(longExt, "A")]).tasks.first?.id,
                       "interop:com.Meridian.VBCR:\(longExt)")
    }

    // MARK: priority default 0

    func testPriorityDefaultsToZero() {
        XCTAssertEqual(InteropDedup.plan([task("a", "A", priority: nil)]).tasks.first?.priority, 0)
        XCTAssertEqual(InteropDedup.plan([task("b", "B", priority: 3)]).tasks.first?.priority, 3)
    }

    // MARK: staleIDs — prior imports not in this sync, sorted

    func testStaleIDsArePriorMinusKeptSorted() {
        let plan = InteropDedup.plan([task("a", "A"), task("b", "B")])
        let prior: Set<String> = [
            "interop:com.Meridian.VBCR:a",
            "interop:com.Meridian.VBCR:gone2",
            "interop:com.Meridian.VBCR:gone1",
        ]
        XCTAssertEqual(plan.staleIDs(priorImportedIDs: prior),
            ["interop:com.Meridian.VBCR:gone1", "interop:com.Meridian.VBCR:gone2"])
    }

    func testStaleIDsEmptyWhenAllKept() {
        let plan = InteropDedup.plan([task("a", "A")])
        XCTAssertTrue(plan.staleIDs(priorImportedIDs: ["interop:com.Meridian.VBCR:a"]).isEmpty)
    }

    // MARK: Reminder lead — lead>now -> lead; else due>now -> due; else nil

    func testReminderFiresAtLeadWhenLeadInFuture() {
        let due = now + 60 * 60 * 1000 // 1h ahead
        let t = InteropDedup.plan([task("a", "A", due: due)]).tasks.first!
        let r = InteropDedup.reminder(for: t, now: now)
        XCTAssertEqual(r?.triggerAtMillis, due - InteropContract.reminderLeadMillis)
        XCTAssertEqual(r?.id, "interop:com.Meridian.VBCR:a")
        XCTAssertEqual(r?.title, "A")
    }

    func testReminderFiresAtDueWhenLeadAlreadyPassedButDueFuture() {
        // due 5 min ahead; lead (10 min before due) is in the past -> fire at due.
        let due = now + 5 * 60 * 1000
        let t = InteropDedup.plan([task("a", "A", due: due)]).tasks.first!
        XCTAssertEqual(InteropDedup.reminder(for: t, now: now)?.triggerAtMillis, due)
    }

    func testReminderNilWhenDueInPast() {
        let due = now - 1
        let t = InteropDedup.plan([task("a", "A", due: due)]).tasks.first!
        XCTAssertNil(InteropDedup.reminder(for: t, now: now))
    }

    func testReminderNilForUndatedTask() {
        let t = InteropDedup.plan([task("a", "A")]).tasks.first!
        XCTAssertNil(InteropDedup.reminder(for: t, now: now))
    }

    func testReminderNilForCompletedTask() {
        let due = now + 60 * 60 * 1000
        let t = InteropDedup.plan([task("a", "A", due: due, completed: true)]).tasks.first!
        XCTAssertNil(InteropDedup.reminder(for: t, now: now))
    }

    func testReminderTitleFallbackForBlank() {
        // Blank title becomes "(untitled)" in plan() (non-blank), so reminder uses that.
        let due = now + 60 * 60 * 1000
        let t = InteropDedup.plan([task("a", "   ", due: due)]).tasks.first!
        XCTAssertEqual(InteropDedup.reminder(for: t, now: now)?.title, "(untitled)")
    }

    func testRemindersForPlanDropsNonFiring() {
        let future = now + 60 * 60 * 1000
        let raw = [
            task("a", "Future", due: future),       // fires
            task("b", "Past", due: now - 1),         // dropped (past)
            task("c", "Undated"),                    // dropped (no due)
            task("d", "Done", due: future, completed: true), // dropped (completed)
        ]
        let plan = InteropDedup.plan(raw)
        let reminders = InteropDedup.reminders(for: plan, now: now)
        XCTAssertEqual(reminders.map(\.id), ["interop:com.Meridian.VBCR:a"])
    }

    // MARK: Sendable/Equatable plumbing

    func testPlanEquatable() {
        XCTAssertEqual(InteropDedup.plan([task("a", "A")]), InteropDedup.plan([task("a", "A")]))
    }
}
