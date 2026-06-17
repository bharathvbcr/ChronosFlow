import XCTest
@testable import ChronosCore

/// Tests for the portable journal helpers (prompt scaffolding, answered-prompt tracking,
/// clean-on-save, save gating, word count, and the relative "last saved" label). All pure /
/// deterministic so they run on the Windows/Linux CI that builds ChronosCore.
final class JournalTextTests: XCTestCase {

    private let prompts = journalPrompts
    private var q0: String { prompts[0].question }   // "What went well today?"
    private var q1: String { prompts[1].question }   // "What drained you today?"
    private var q2: String { prompts[2].question }   // "One thing to make tomorrow better?"

    // MARK: Word count

    func testWordCount() {
        XCTAssertEqual(journalWordCount(""), 0)
        XCTAssertEqual(journalWordCount("   "), 0)
        XCTAssertEqual(journalWordCount("one"), 1)
        XCTAssertEqual(journalWordCount("  two   words  "), 2)
        XCTAssertEqual(journalWordCount("line one\nline two here"), 5)
    }

    // MARK: Prompt scaffolding

    func testBodyWithPromptSeedsEmpty() {
        XCTAssertEqual(journalBodyWithPrompt("", question: q0), "\(q0)\n")
        XCTAssertEqual(journalBodyWithPrompt("   ", question: q0), "\(q0)\n")
    }

    func testBodyWithPromptAppendsOnNewLine() {
        let body = journalBodyWithPrompt("Already wrote this.", question: q0)
        XCTAssertEqual(body, "Already wrote this.\n\n\(q0)\n")
    }

    func testBodyWithPromptNoDuplicate() {
        let once = journalBodyWithPrompt("", question: q0)
        let twice = journalBodyWithPrompt(once, question: q0)
        XCTAssertEqual(once, twice)
    }

    func testGuidedTemplateAddsAllPromptsOnce() {
        let body = journalBodyWithGuidedTemplate("")
        for p in prompts { XCTAssertTrue(body.contains(p.question)) }
        // Idempotent: re-applying doesn't duplicate.
        XCTAssertEqual(journalBodyWithGuidedTemplate(body), body)
    }

    // MARK: Answered-prompt tracking

    func testAnsweredKeysCountsReflectedOnly() {
        // q0 answered, q1 left blank.
        let body = "\(q0)\nIt was a calm morning.\n\n\(q1)\n"
        let keys = journalAnsweredPromptKeys(body)
        XCTAssertEqual(keys, [prompts[0].key])
        XCTAssertEqual(journalAnsweredPromptCount(body), 1)
        XCTAssertEqual(journalPromptsPresentCount(body), 2)
    }

    func testMeterLabel() {
        let body = "\(q0)\nGood deep-work block.\n\n\(q1)\n"
        // 1 of 2 reflected · word count of the whole body.
        let words = journalWordCount(body)
        XCTAssertEqual(journalMeterLabel(body), "1 of 2 reflected · \(words) words")
    }

    func testMeterLabelNoPrompts() {
        XCTAssertEqual(journalMeterLabel("just one"), "2 words")
        XCTAssertEqual(journalMeterLabel("solo"), "1 word")
    }

    // MARK: Only-prompts gate

    func testIsOnlyPromptsTrueForBareScaffold() {
        let scaffold = journalBodyWithGuidedTemplate("")
        XCTAssertTrue(journalIsOnlyPrompts(scaffold))
        XCTAssertFalse(journalCanSave(scaffold))
    }

    func testIsOnlyPromptsFalseWithReflection() {
        let body = "\(q0)\nShipped the feature.\n"
        XCTAssertFalse(journalIsOnlyPrompts(body))
        XCTAssertTrue(journalCanSave(body))
    }

    func testCanSaveFalseForEmpty() {
        XCTAssertFalse(journalCanSave("   \n  "))
    }

    // MARK: Clean-on-save

    func testCleanDropsUnansweredPrompts() {
        let body = "\(q0)\nShipped the feature.\n\n\(q1)\n\n\(q2)\nSleep earlier.\n"
        let cleaned = journalCleanForSave(body)
        XCTAssertTrue(cleaned.contains(q0))
        XCTAssertTrue(cleaned.contains("Shipped the feature."))
        XCTAssertFalse(cleaned.contains(q1))          // unanswered → dropped
        XCTAssertTrue(cleaned.contains(q2))
        XCTAssertTrue(cleaned.contains("Sleep earlier."))
    }

    func testCleanKeepsFreeformText() {
        let body = "Just a normal day, nothing structured."
        XCTAssertEqual(journalCleanForSave(body), body)
    }

    func testCleanAllUnansweredCollapsesToEmpty() {
        let scaffold = journalBodyWithGuidedTemplate("")
        XCTAssertTrue(journalCleanForSave(scaffold).isEmpty)
    }

    // MARK: Last-saved label

    func testLastSavedLabel() {
        let now = Date(timeIntervalSince1970: 1_000_000)
        func ago(_ seconds: TimeInterval) -> String {
            journalLastSavedLabel(updatedAt: now.addingTimeInterval(-seconds), now: now)
        }
        XCTAssertEqual(ago(0), "Last saved just now")
        XCTAssertEqual(ago(30), "Last saved just now")
        XCTAssertEqual(ago(60), "Last saved 1m ago")
        XCTAssertEqual(ago(45 * 60), "Last saved 45m ago")
        XCTAssertEqual(ago(2 * 3600), "Last saved 2h ago")
        XCTAssertEqual(ago(3 * 24 * 3600), "Last saved 3d ago")
        // Future timestamps clamp to "just now" (never negative).
        XCTAssertEqual(journalLastSavedLabel(updatedAt: now.addingTimeInterval(120), now: now),
                       "Last saved just now")
    }
}
