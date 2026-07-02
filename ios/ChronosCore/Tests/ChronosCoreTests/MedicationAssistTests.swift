import XCTest
@testable import ChronosCore

/// Tests for the deterministic medication form-assist planner, mirroring the LOCAL-path cases in
/// Android's `RoutineAssistPlannerTest` (metformin details, cadence spacing, form wording, refill
/// supply, indication notes, pharmacy refill intent).
final class MedicationAssistTests: XCTestCase {

    private func suggestions(
        name: String, dosage: String = "", unit: String = "", notes: String = "",
        primaryMinute: Int = 8 * 60, reminderCount: Int = 1,
        withFood: Bool = false, tracksSupply: Bool = false
    ) -> [MedicationAssistSuggestion] {
        medicationAssistSuggestions(MedicationAssistInput(
            name: name, dosage: dosage, unit: unit, notes: notes,
            primaryReminderMinute: primaryMinute, reminderCount: reminderCount,
            takeWithFood: withFood, tracksSupply: tracksSupply))
    }

    private func details(_ list: [MedicationAssistSuggestion]) -> (name: String?, dosage: String?, unit: String?)? {
        for s in list {
            if case let .details(name, dosage, unit) = s.change { return (name, dosage, unit) }
        }
        return nil
    }

    func testMetforminCaptureExtractsNameDosageUnit() {
        let out = suggestions(name: "metformin 500 mg with dinner")
        let d = details(out)
        XCTAssertEqual(d?.name, "Metformin")
        XCTAssertEqual(d?.dosage, "500")
        XCTAssertEqual(d?.unit, "mg")
        // Dinner wording → evening reminder + with-food meal timing.
        XCTAssertTrue(out.contains { $0.change == .reminders([18 * 60]) })
        XCTAssertTrue(out.contains { $0.change == .takeWithFood })
    }

    func testTwiceDailySpacesTwoRemindersTwelveHoursApart() {
        let out = suggestions(name: "metformin 500 mg twice daily", primaryMinute: 8 * 60)
        XCTAssertTrue(out.contains { $0.change == .reminders([8 * 60, 20 * 60]) })
    }

    func testEveryEightHoursYieldsThreeSpacedReminders() {
        let out = suggestions(name: "amoxicillin every 8 hours", primaryMinute: 7 * 60)
        XCTAssertTrue(out.contains { $0.change == .reminders([7 * 60, 15 * 60, 23 * 60]) })
    }

    func testExplicitTimeIsParsed() {
        let out = suggestions(name: "magnesium 200 mg at 9pm")
        XCTAssertTrue(out.contains { $0.change == .reminders([21 * 60]) })
        XCTAssertTrue(out.contains { $0.label == "9:00 PM reminder" })
    }

    func testBedtimeWordingSuggestsBedtimeReminder() {
        let out = suggestions(name: "melatonin before bed")
        XCTAssertTrue(out.contains { $0.change == .reminders([21 * 60]) })
    }

    func testDropsWordingSuggestsDropUnit() {
        let out = suggestions(name: "eye drops 2x/day")
        XCTAssertTrue(out.contains { $0.change == .unit("drop") })
        // 2x wording → two spaced reminders.
        XCTAssertTrue(out.contains { $0.change == .reminders([8 * 60, 20 * 60]) })
    }

    func testInhalerSupplySuggestsRefillTracking() {
        let out = suggestions(name: "rescue inhaler as needed 23 left")
        XCTAssertTrue(out.contains { $0.change == .trackSupply(dosesLeft: 23) })
        XCTAssertTrue(out.contains { $0.change == .unit("dose") })
    }

    func testSupplyNotSuggestedWhenAlreadyTracking() {
        let out = suggestions(name: "rescue inhaler 23 left", tracksSupply: true)
        XCTAssertFalse(out.contains { if case .trackSupply = $0.change { return true }; return false })
    }

    func testIndicationBecomesNote() {
        let out = suggestions(name: "ibuprofen 200 mg for headache")
        XCTAssertTrue(out.contains { $0.change == .note("Use for headache") })
        // The indication is stripped out of the suggested name.
        XCTAssertEqual(details(out)?.name, "Ibuprofen")
    }

    func testPharmacyRefillIntentBecomesNote() {
        let out = suggestions(name: "refill asthma inhaler at pharmacy")
        XCTAssertTrue(out.contains { $0.change == .note("Refill at pharmacy") })
    }

    func testFoodWordingNotSuggestedWhenAlreadyWithFood() {
        let out = suggestions(name: "metformin with food", withFood: true)
        XCTAssertFalse(out.contains { $0.change == .takeWithFood })
    }

    func testNoteNotSuggestedWhenNotesAlreadyFilled() {
        let out = suggestions(name: "zyrtec for allergies", notes: "Take in the morning")
        XCTAssertFalse(out.contains { if case .note = $0.change { return true }; return false })
    }

    func testCapturePrefixAndNoiseStrippedFromName() {
        let out = suggestions(name: "remind me to take vitamin d 1000 iu every morning")
        let d = details(out)
        XCTAssertEqual(d?.name, "Vitamin D")
        XCTAssertEqual(d?.dosage, "1000")
        XCTAssertEqual(d?.unit, "IU")
        XCTAssertTrue(out.contains { $0.change == .reminders([8 * 60]) })
    }

    func testNoDetailsWhenNothingDiffers() {
        // Name already clean, no dosage in the text — nothing to suggest for details.
        let out = suggestions(name: "Metformin", dosage: "500", unit: "mg")
        XCTAssertNil(details(out))
    }

    func testSuggestionsCappedAtSix() {
        let out = suggestions(
            name: "cough syrup 10 ml with food at 9pm twice daily for cough 20 left refill at pharmacy")
        XCTAssertLessThanOrEqual(out.count, 6)
        XCTAssertFalse(out.isEmpty)
    }
}
