import XCTest
@testable import ChronosCore

final class FoldedReminderTests: XCTestCase {
    func testRankPrefersMedicationThenMostOverdue() {
        let candidates = [
            FoldedReminder(
                kind: .habit,
                entityID: "h1",
                title: "Stretch",
                detail: "Window open",
                dueMinute: 8 * 60,
                isOverdue: false),
            FoldedReminder(
                kind: .medication,
                entityID: "m1",
                title: "Aspirin",
                detail: "Due 9:00 AM",
                dueMinute: 9 * 60,
                isOverdue: false),
            FoldedReminder(
                kind: .task,
                entityID: "t1",
                title: "Email",
                detail: "Due 8:30 AM",
                dueMinute: 8 * 60 + 30,
                isOverdue: false),
        ]
        let ranked = rankFoldedReminders(candidates)
        XCTAssertEqual(ranked.map(\.entityID), ["m1", "t1", "h1"])
    }

    func testMedicationFoldUsesLatestPassedReminderMinute() {
        let plan = MedicationFoldInput(
            id: "med-1",
            name: "Vitamin",
            dosage: "1",
            unit: "tab",
            isActive: true,
            reminderMinutes: [8 * 60, 14 * 60, 20 * 60],
            pausedUntil: nil,
            takenScheduledMinutes: [])
        let today = Calendar.current.startOfDay(for: Date())
        let folded = buildMedicationFoldedReminders(
            plans: [plan],
            today: today,
            nowMinute: 15 * 60)
        XCTAssertEqual(folded.count, 1)
        XCTAssertEqual(folded.first?.dueMinute, 14 * 60)
    }
}
