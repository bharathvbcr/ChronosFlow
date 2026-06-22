import XCTest
@testable import ChronosCore

private let utcCal: Calendar = {
    var c = Calendar(identifier: .gregorian)
    c.timeZone = TimeZone(identifier: "UTC")!
    return c
}()
private func day(_ y: Int, _ m: Int, _ d: Int) -> Date {
    utcCal.date(from: DateComponents(year: y, month: m, day: d))!
}

private func block(
    _ id: String,
    start: Int = 60,
    duration: Int = 30,
    flexibility: BlockFlexibility = .movable,
    provenance: BlockProvenance = .user,
    isLocked: Bool = false,
    taskId: String? = nil,
    habitId: String? = nil
) -> PlannerBlock {
    PlannerBlock(
        id: id,
        title: "Block \(id)",
        category: "Work",
        startMinuteOfDay: start,
        durationMinutes: duration,
        provenance: provenance,
        flexibility: flexibility,
        taskId: taskId,
        habitId: habitId,
        isLocked: isLocked
    )
}

// MARK: - Command apply / inverse

final class PlannerCommandTests: XCTestCase {

    func testCreateAppliesAndInverts() {
        let day: [PlannerBlock] = [block("a", start: 60)]
        let new = block("b", start: 120)
        let cmd = PlannerCommand.create(id: "c1", block: new)

        let applied = cmd.apply(to: day)
        XCTAssertEqual(applied.count, 2)
        XCTAssertTrue(applied.contains { $0.id == "b" })

        let inverted = cmd.inverse(on: applied)
        XCTAssertEqual(inverted.map(\.id), ["a"])
    }

    func testCreateInsertIsIdempotentById() {
        // Redo after undo must not double-insert the same block.
        let day: [PlannerBlock] = [block("a")]
        let cmd = PlannerCommand.create(id: "c1", block: block("a"))
        let applied = cmd.apply(to: day)
        XCTAssertEqual(applied.count, 1)
    }

    func testDeleteRemovesAndRestoresSnapshot() {
        let original = block("a", start: 200, duration: 45, taskId: "task-9")
        let day = [original, block("b")]
        let cmd = PlannerCommand.delete(id: "d1", blockSnapshot: original)

        let applied = cmd.apply(to: day)
        XCTAssertEqual(applied.map(\.id), ["b"])

        let restored = cmd.inverse(on: applied)
        XCTAssertEqual(Set(restored.map(\.id)), ["a", "b"])
        // Snapshot restored exactly — including its instance-identity link.
        let restoredA = restored.first { $0.id == "a" }
        XCTAssertEqual(restoredA?.startMinuteOfDay, 200)
        XCTAssertEqual(restoredA?.durationMinutes, 45)
        XCTAssertEqual(restoredA?.taskId, "task-9")
    }

    func testMoveAppliesTargetAndInvertsToOriginal() {
        let day = [block("a", start: 60), block("b", start: 600)]
        let cmd = PlannerCommand.move(id: "m1", blockId: "a", targetStartMinute: 480, originalStartMinute: 60)

        let applied = cmd.apply(to: day)
        XCTAssertEqual(applied.first { $0.id == "a" }?.startMinuteOfDay, 480)
        // Other blocks untouched.
        XCTAssertEqual(applied.first { $0.id == "b" }?.startMinuteOfDay, 600)

        let inverted = cmd.inverse(on: applied)
        XCTAssertEqual(inverted.first { $0.id == "a" }?.startMinuteOfDay, 60)
    }

    func testResizeAppliesTargetAndInvertsToOriginal() {
        let day = [block("a", start: 60, duration: 30)]
        let cmd = PlannerCommand.resize(id: "r1", blockId: "a", targetDurationMinutes: 90, originalDurationMinutes: 30)

        let applied = cmd.apply(to: day)
        XCTAssertEqual(applied.first { $0.id == "a" }?.durationMinutes, 90)

        let inverted = cmd.inverse(on: applied)
        XCTAssertEqual(inverted.first { $0.id == "a" }?.durationMinutes, 30)
    }

    func testResolveConflictsBatchAppliesAndInvertsAllStarts() {
        let day = [block("a", start: 60), block("b", start: 70), block("c", start: 600)]
        let changes = [
            BlockStartChange(blockId: "a", originalStartMinute: 60, newStartMinute: 0),
            BlockStartChange(blockId: "b", originalStartMinute: 70, newStartMinute: 120)
        ]
        let cmd = PlannerCommand.resolveConflicts(id: "rc1", changes: changes)

        let applied = cmd.apply(to: day)
        XCTAssertEqual(applied.first { $0.id == "a" }?.startMinuteOfDay, 0)
        XCTAssertEqual(applied.first { $0.id == "b" }?.startMinuteOfDay, 120)
        XCTAssertEqual(applied.first { $0.id == "c" }?.startMinuteOfDay, 600) // untouched

        let inverted = cmd.inverse(on: applied)
        XCTAssertEqual(inverted.first { $0.id == "a" }?.startMinuteOfDay, 60)
        XCTAssertEqual(inverted.first { $0.id == "b" }?.startMinuteOfDay, 70)
    }

    func testApplyThenInverseRoundTripsToOriginalDay() {
        let day = [block("a", start: 60, duration: 30), block("b", start: 600, duration: 60)]
        let commands: [PlannerCommand] = [
            .create(id: "c", block: block("z", start: 800)),
            .move(id: "m", blockId: "a", targetStartMinute: 90, originalStartMinute: 60),
            .resize(id: "r", blockId: "b", targetDurationMinutes: 120, originalDurationMinutes: 60),
            .delete(id: "d", blockSnapshot: block("a", start: 60, duration: 30))
        ]
        for cmd in commands {
            let applied = cmd.apply(to: day)
            let back = cmd.inverse(on: applied)
            XCTAssertEqual(Set(back.map(\.id)), Set(day.map(\.id)), "\(cmd.label) did not round-trip ids")
        }
    }

    func testLabelsMatchAndroid() {
        XCTAssertEqual(PlannerCommand.create(id: "1", block: block("a")).label, "Create block")
        XCTAssertEqual(PlannerCommand.move(id: "1", blockId: "a", targetStartMinute: 0, originalStartMinute: 0).label, "Move block")
        XCTAssertEqual(PlannerCommand.resize(id: "1", blockId: "a", targetDurationMinutes: 1, originalDurationMinutes: 1).label, "Resize block")
        XCTAssertEqual(PlannerCommand.delete(id: "1", blockSnapshot: block("a")).label, "Delete block")
        XCTAssertEqual(PlannerCommand.resolveConflicts(id: "1", changes: []).label, "Repair schedule")
    }

    func testCommandIdSurfacedAcrossCases() {
        XCTAssertEqual(PlannerCommand.create(id: "cid", block: block("a")).id, "cid")
        XCTAssertEqual(PlannerCommand.resolveConflicts(id: "rid", changes: []).id, "rid")
    }
}

// MARK: - Command history (mirrors Android PlannerCommandHistoryTest)

final class PlannerCommandHistoryTests: XCTestCase {

    private func stub(_ id: String) -> PlannerCommand {
        .create(id: id, block: block(id))
    }

    func testInitialHistoryHasNoUndoOrRedo() {
        let history = PlannerCommandHistory()
        XCTAssertFalse(history.canUndo)
        XCTAssertFalse(history.canRedo)
    }

    func testPushEnablesUndoAndClearsRedo() {
        var history = PlannerCommandHistory()
        history.push(stub("one"))
        history.push(stub("two"))

        XCTAssertTrue(history.canUndo)
        XCTAssertFalse(history.canRedo)

        let undo = history.popUndo()
        XCTAssertEqual(undo?.id, "two")
        XCTAssertTrue(history.canUndo)
        XCTAssertTrue(history.canRedo)
    }

    func testUndoThenRedoRestoresCommandState() {
        var history = PlannerCommandHistory()
        history.push(stub("first"))
        let undone = history.popUndo()

        XCTAssertEqual(undone?.id, "first")
        XCTAssertFalse(history.canUndo)
        XCTAssertTrue(history.canRedo)

        let redone = history.popRedo()
        XCTAssertEqual(redone?.id, "first")
        XCTAssertTrue(history.canUndo)
        XCTAssertFalse(history.canRedo)
    }

    func testPoppingEmptyStacksReturnsNilAndKeepsFlagsFalse() {
        var history = PlannerCommandHistory()
        XCTAssertNil(history.popUndo())
        XCTAssertNil(history.popRedo())
        XCTAssertFalse(history.canUndo)
        XCTAssertFalse(history.canRedo)
    }

    func testClearEmptiesBothStacksAndDisablesUndoRedo() {
        var history = PlannerCommandHistory()
        history.push(stub("first"))
        history.push(stub("second"))
        _ = history.popUndo()
        XCTAssertTrue(history.canRedo)

        history.clear()

        XCTAssertFalse(history.canUndo)
        XCTAssertFalse(history.canRedo)
    }

    func testPushAfterUndoForksHistoryDiscardingRedo() {
        var history = PlannerCommandHistory()
        history.push(stub("a"))
        history.push(stub("b"))
        _ = history.popUndo()          // undo "b" -> redo has "b"
        XCTAssertTrue(history.canRedo)

        history.push(stub("c"))        // new edit forks: redo cleared
        XCTAssertFalse(history.canRedo)
        XCTAssertTrue(history.canUndo)
        XCTAssertEqual(history.popUndo()?.id, "c")
    }

    func testValueSemanticsCopyIsIndependent() {
        var original = PlannerCommandHistory()
        original.push(stub("a"))
        var copy = original
        copy.clear()
        // Mutating the copy must not affect the original (struct value semantics).
        XCTAssertTrue(original.canUndo)
        XCTAssertFalse(copy.canUndo)
    }

    func testFullUndoRedoDriveOverBlocks() {
        // End-to-end: history returns commands, caller applies/inverts them over the day.
        var day: [PlannerBlock] = [block("a", start: 60)]
        var history = PlannerCommandHistory()

        let create = PlannerCommand.create(id: "c1", block: block("b", start: 200))
        day = create.apply(to: day)
        history.push(create)
        XCTAssertEqual(Set(day.map(\.id)), ["a", "b"])

        // Undo: pop and invert.
        if let undo = history.popUndo() { day = undo.inverse(on: day) }
        XCTAssertEqual(day.map(\.id), ["a"])

        // Redo: pop and re-apply.
        if let redo = history.popRedo() { day = redo.apply(to: day) }
        XCTAssertEqual(Set(day.map(\.id)), ["a", "b"])
    }
}
