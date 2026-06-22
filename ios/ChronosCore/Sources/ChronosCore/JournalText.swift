import Foundation

// JournalText — portable, Foundation-only helpers for the daily reflection journal.
//
// Platform-agnostic port of the Android journal sheet logic
// (`feature/daydial/.../JournalEntrySheet.kt`: word count, prompt scaffolding, answered-prompt
// tracking, clean-on-save, only-prompts gate, and the relative "last saved" label). Pure and
// deterministic — `now` is injected (never `Date()` internally) so it unit-tests off-device
// (the Windows/Linux CI that builds ChronosCore). No SwiftUI / UIKit.

// MARK: - Prompts

/// A guided reflection prompt: a stable `key`, a short chip `label`, and the `question` scaffold
/// that gets folded into the body. Mirrors Android's `JournalPrompt`.
public struct JournalPrompt: Sendable, Equatable, Identifiable {
    public let key: String
    public let label: String
    public let question: String

    public init(key: String, label: String, question: String) {
        self.key = key
        self.label = label
        self.question = question
    }

    public var id: String { key }
}

/// The rotating prompt pool for the daily journal shuffle. Mirrors Android's expanded set.
public let journalPrompts: [JournalPrompt] = [
    JournalPrompt(key: "went_well",  label: "What went well",   question: "What went well today?"),
    JournalPrompt(key: "drained",    label: "What drained me",  question: "What drained you today?"),
    JournalPrompt(key: "tomorrow",   label: "Tomorrow…",        question: "One thing to make tomorrow better?"),
    JournalPrompt(key: "grateful",   label: "Grateful for",     question: "What are you grateful for right now?"),
    JournalPrompt(key: "learned",    label: "Learned",          question: "What did you learn today?"),
    JournalPrompt(key: "energy",     label: "Energy",           question: "What drained your energy? What filled it?"),
    JournalPrompt(key: "proud",      label: "Proud of",         question: "What are you most proud of from today?"),
    JournalPrompt(key: "feeling",    label: "Feeling",          question: "How are you really feeling right now?"),
    JournalPrompt(key: "distracted", label: "Distracted by",    question: "What distracted you most today?"),
    JournalPrompt(key: "kindness",   label: "Kindness",         question: "Did you do something kind for someone — or yourself?"),
]

// MARK: - Word count

/// Whitespace-delimited word count for the journal body, used for the live writing meter.
/// Mirrors `journalWordCount`.
public func journalWordCount(_ body: String) -> Int {
    body.split(whereSeparator: { $0.isWhitespace || $0.isNewline })
        .filter { !$0.isEmpty }
        .count
}

// MARK: - Prompt scaffolding

/// Body text after folding a prompt's `question` in. When the body is blank the question seeds it;
/// otherwise the question is appended on its own line, but only if it isn't already present so
/// repeated taps never duplicate it. Mirrors `journalBodyWithPrompt`.
public func journalBodyWithPrompt(_ body: String, question: String) -> String {
    if body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
        return "\(question)\n"
    }
    if body.contains(question) {
        return body
    }
    let trimmedEnd = String(body.reversed().drop(while: { $0.isWhitespace || $0.isNewline }).reversed())
    return "\(trimmedEnd)\n\n\(question)\n"
}

/// Lays every prompt question into the body as a guided-reflection scaffold, reusing
/// `journalBodyWithPrompt` so already-present questions are never duplicated. Mirrors
/// `journalBodyWithGuidedTemplate`.
public func journalBodyWithGuidedTemplate(_ body: String, prompts: [JournalPrompt] = journalPrompts) -> String {
    prompts.reduce(body) { acc, prompt in journalBodyWithPrompt(acc, question: prompt.question) }
}

// MARK: - Answered-prompt tracking

/// Keys of the prompts whose question appears in the body with at least one non-blank line of
/// reflection beneath it (before the next prompt question). Mirrors `journalAnsweredPromptKeys`.
public func journalAnsweredPromptKeys(_ body: String, prompts: [JournalPrompt] = journalPrompts) -> Set<String> {
    let byQuestion = Dictionary(uniqueKeysWithValues: prompts.map { ($0.question, $0) })
    let lines = body.components(separatedBy: "\n")
    var answered: Set<String> = []
    var i = 0
    while i < lines.count {
        if let prompt = byQuestion[lines[i].trimmingCharacters(in: .whitespaces)] {
            var j = i + 1
            var hasReflection = false
            while j < lines.count, byQuestion[lines[j].trimmingCharacters(in: .whitespaces)] == nil {
                if !lines[j].trimmingCharacters(in: .whitespaces).isEmpty { hasReflection = true }
                j += 1
            }
            if hasReflection { answered.insert(prompt.key) }
            i = j
        } else {
            i += 1
        }
    }
    return answered
}

/// How many prompt questions present in the body have at least one line of reflection beneath them.
/// Mirrors `journalAnsweredPromptCount`.
public func journalAnsweredPromptCount(_ body: String, prompts: [JournalPrompt] = journalPrompts) -> Int {
    journalAnsweredPromptKeys(body, prompts: prompts).count
}

/// How many of the `prompts` questions appear anywhere in the body. Mirrors `journalPromptsPresentCount`.
public func journalPromptsPresentCount(_ body: String, prompts: [JournalPrompt] = journalPrompts) -> Int {
    prompts.filter { body.contains($0.question) }.count
}

/// A short status line for the writing meter, e.g. "2 of 3 reflected · 45 words" when prompts are
/// present, or just "45 words" otherwise. Mirrors the Android meter text.
public func journalMeterLabel(_ body: String, prompts: [JournalPrompt] = journalPrompts) -> String {
    let words = journalWordCount(body)
    let wordLabel = words == 1 ? "1 word" : "\(words) words"
    let present = journalPromptsPresentCount(body, prompts: prompts)
    guard present > 0 else { return wordLabel }
    let reflected = journalAnsweredPromptCount(body, prompts: prompts)
    return "\(reflected) of \(present) reflected · \(wordLabel)"
}

// MARK: - Save gating + clean-on-save

/// True when the body is a non-empty scaffold of only prompt questions and no actual reflection —
/// used to keep Save disabled until the user adds their own words. Mirrors `journalIsOnlyPrompts`.
public func journalIsOnlyPrompts(_ body: String, prompts: [JournalPrompt] = journalPrompts) -> Bool {
    let questions = Set(prompts.map { $0.question })
    let contentLines = body.components(separatedBy: "\n")
        .map { $0.trimmingCharacters(in: .whitespaces) }
        .filter { !$0.isEmpty }
    guard !contentLines.isEmpty else { return false }
    return contentLines.allSatisfy { questions.contains($0) }
}

/// Whether the draft can be saved: it has real content and isn't a bare prompt scaffold.
public func journalCanSave(_ body: String, prompts: [JournalPrompt] = journalPrompts) -> Bool {
    !body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        && !journalIsOnlyPrompts(body, prompts: prompts)
}

/// Body to persist: drops any prompt question left unanswered (no reflection beneath it) along with
/// its trailing blank lines, so a guided entry saves only the sections the user filled in. Answered
/// question/reflection pairs and free-form text are untouched (whitespace-trimmed). Mirrors
/// `journalCleanForSave`.
public func journalCleanForSave(_ body: String, prompts: [JournalPrompt] = journalPrompts) -> String {
    let questions = Set(prompts.map { $0.question })
    let lines = body.components(separatedBy: "\n")
    var keep = [Bool](repeating: true, count: lines.count)
    var i = 0
    while i < lines.count {
        if questions.contains(lines[i].trimmingCharacters(in: .whitespaces)) {
            var j = i + 1
            var hasReflection = false
            while j < lines.count, !questions.contains(lines[j].trimmingCharacters(in: .whitespaces)) {
                if !lines[j].trimmingCharacters(in: .whitespaces).isEmpty { hasReflection = true }
                j += 1
            }
            if !hasReflection {
                for k in i..<j { keep[k] = false }
            }
            i = j
        } else {
            i += 1
        }
    }
    let kept = lines.enumerated().filter { keep[$0.offset] }.map { $0.element }
    return kept.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
}

// MARK: - Relative "last saved" label

/// Relative "last saved" label for an edited entry, coarsened to just-now / minutes / hours / days.
/// `now` is injected for deterministic tests. Mirrors `journalLastSavedLabel`.
public func journalLastSavedLabel(updatedAt: Date, now: Date) -> String {
    let minutes = max(0, Int(now.timeIntervalSince(updatedAt) / 60))
    switch minutes {
    case ..<1: return "Last saved just now"
    case ..<60: return "Last saved \(minutes)m ago"
    case ..<(24 * 60): return "Last saved \(minutes / 60)h ago"
    default: return "Last saved \(minutes / (24 * 60))d ago"
    }
}
