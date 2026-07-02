import XCTest
@testable import ChronosCore

/// Mirrors Android's `CaptureIntentClassifierTest` so both classifiers rank the same capture the
/// same way.
final class CaptureIntentClassifierTests: XCTestCase {

    func testClassifiesOneOffCallCaptureAsTaskFirst() {
        let suggestions = CaptureIntentClassifier.classify("call mom tomorrow")
        XCTAssertEqual(suggestions.first?.type, .task)
    }

    func testClassifiesVitaminDoseCaptureAsMedicationFirst() {
        let suggestions = CaptureIntentClassifier.classify("vitamin d 1000 iu morning")
        XCTAssertEqual(suggestions.first?.type, .medication)
        XCTAssertGreaterThan(suggestions.first!.score, suggestions.last!.score)
    }

    func testClassifiesMedicationCapturesWithCadenceAsMedicationFirst() {
        XCTAssertEqual(CaptureIntentClassifier.classify("vitamin d daily").first?.type, .medication)
        XCTAssertEqual(CaptureIntentClassifier.classify("daily aspirin").first?.type, .medication)
        XCTAssertEqual(CaptureIntentClassifier.classify("melatonin nightly").first?.type, .medication)
    }

    func testClassifiesGymCadenceCaptureAsHabitFirst() {
        let suggestions = CaptureIntentClassifier.classify("gym 3x week evening")
        XCTAssertEqual(suggestions.first?.type, .habit)
    }

    func testClassifiesProtectedFocusCapturesAsFocusFirst() {
        XCTAssertEqual(
            CaptureIntentClassifier.classify("focus 45 minutes on launch brief").first?.type, .focus)
        XCTAssertEqual(
            CaptureIntentClassifier.classify("deep work 90m design review").first?.type, .focus)
    }

    func testClassifiesNaturalRecurrenceCapturesAsHabitsFirst() {
        XCTAssertEqual(
            CaptureIntentClassifier.classify("stretch every weekday morning").first?.type, .habit)
        XCTAssertEqual(
            CaptureIntentClassifier.classify("strength training three times per week").first?.type, .habit)
    }

    func testClassifiesOneOffChoreCapturesAsTasksNotHabits() {
        XCTAssertEqual(CaptureIntentClassifier.classify("water plants tomorrow").first?.type, .task)
        XCTAssertEqual(CaptureIntentClassifier.classify("walk dog tonight").first?.type, .task)
    }

    func testClassifiesSpeechStyleCommunicationCapturesAsTasksFirst() {
        XCTAssertEqual(CaptureIntentClassifier.classify("message Sarah tomorrow").first?.type, .task)
        XCTAssertEqual(CaptureIntentClassifier.classify("ask Jordan about the invoice").first?.type, .task)
        XCTAssertEqual(CaptureIntentClassifier.classify("follow up with doctor Friday").first?.type, .task)
    }

    func testClassifiesCommonMedicationAndHabitCapturesWithoutSubstringFalsePositives() {
        XCTAssertEqual(
            CaptureIntentClassifier.classify("metformin 500 mg with dinner").first?.type, .medication)
        XCTAssertEqual(CaptureIntentClassifier.classify("aspirin morning").first?.type, .medication)
        XCTAssertEqual(CaptureIntentClassifier.classify("melatonin before bed").first?.type, .medication)
        XCTAssertEqual(CaptureIntentClassifier.classify("floss nightly").first?.type, .habit)
    }

    func testDoesNotTreatWeakTakeWordingAsMedicationWithoutDoseContext() {
        let suggestions = CaptureIntentClassifier.classify("take package to post office tomorrow")
        XCTAssertEqual(suggestions.first?.type, .task)
    }

    func testClassifiesUnknownInputAsTaskWithMinimumScore() {
        let suggestions = CaptureIntentClassifier.classify("xyzabc123")
        // All scores should be 0, but task should be adjusted to 1.
        let taskSuggestion = suggestions.first { $0.type == .task }
        XCTAssertEqual(taskSuggestion?.score, 1)
    }

    func testShortCapturesAreIgnoredUnlessKnownMedicationTerms() {
        XCTAssertTrue(CaptureIntentClassifier.classify("hi").isEmpty)
        XCTAssertEqual(CaptureIntentClassifier.classify("d3").first?.type, .medication)
        XCTAssertEqual(CaptureIntentClassifier.classify("rx").first?.type, .medication)
    }
}
