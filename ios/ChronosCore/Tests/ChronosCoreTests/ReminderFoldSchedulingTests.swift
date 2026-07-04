import XCTest
@testable import ChronosCore

final class ReminderFoldSchedulingTests: XCTestCase {
    func testSkipsWhenBothFoldAndBlockLiveOn() {
        XCTAssertTrue(ReminderFoldScheduling.skipsSeparateRemindersWhenFolded(
            remindersFoldedIntoLiveActivity: true,
            currentBlockLiveActivityEnabled: true))
    }

    func testDoesNotSkipWhenFoldOff() {
        XCTAssertFalse(ReminderFoldScheduling.skipsSeparateRemindersWhenFolded(
            remindersFoldedIntoLiveActivity: false,
            currentBlockLiveActivityEnabled: true))
    }

    func testDoesNotSkipWhenBlockLiveOff() {
        XCTAssertFalse(ReminderFoldScheduling.skipsSeparateRemindersWhenFolded(
            remindersFoldedIntoLiveActivity: true,
            currentBlockLiveActivityEnabled: false))
    }

    func testDoesNotSkipWhenBothOff() {
        XCTAssertFalse(ReminderFoldScheduling.skipsSeparateRemindersWhenFolded(
            remindersFoldedIntoLiveActivity: false,
            currentBlockLiveActivityEnabled: false))
    }
}
