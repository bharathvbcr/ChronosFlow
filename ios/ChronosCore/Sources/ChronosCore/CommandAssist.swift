import Foundation

// Command-palette assist — pure, deterministic port of CommandAssistPlanner.localRankCommandIds.
//
// Given a free-text query and a set of command descriptors (id, title, keywords), rank the best
// matches by a phrase/token scoring scheme and return their ids, highest score first. This is the
// offline baseline the Android planner always computes (the on-device LLM is only an optional
// re-ranker on top); on iOS the deterministic baseline is enough for a command palette and is
// fully testable without the model.
//
// TODO(ios): there is no command-palette UI on iOS yet. When one is added (e.g. a `.searchable`
// quick-action sheet), feed its query + the registry of action descriptors through `rankCommands`
// to order results; an optional ChronosAssistant re-rank could sit on top, mirroring Android's
// `rankCommandIdsWithAssist`.

public struct CommandAssistCandidate: Sendable, Equatable {
    public let id: String
    public let title: String
    public let keywords: [String]

    public init(id: String, title: String, keywords: [String] = []) {
        self.id = id
        self.title = title
        self.keywords = keywords
    }
}

/// Rank `candidates` against `query`, returning up to `limit` command ids, best match first.
///
/// Scoring mirrors `CommandAssistPlanner.localRankCommandIds`:
/// - Queries shorter than 3 characters return nothing (avoids noisy single-letter matches).
/// - A phrase hit scores by specificity: exact title (100) > title contains (80) > id contains (70)
///   > haystack contains (60). When there's a phrase hit, matching query tokens are added as a
///   tiebreaker.
/// - With no phrase hit, graded token overlap scores `20 + tokenHits * 15` so a single strong
///   keyword still ranks and more overlap ranks higher.
/// - Zero-score candidates are dropped; ties keep the input order (Swift's `sorted` is stable here
///   via the `>` comparator only on score).
public func rankCommands(
    query: String,
    candidates: [CommandAssistCandidate],
    limit: Int = 3
) -> [String] {
    let normalized = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    guard normalized.count >= 3 else { return [] }

    let queryTokens = normalized.split(separator: " ").map(String.init).filter { $0.count > 2 }

    let scored: [(id: String, score: Int)] = candidates.map { candidate in
        let haystack = (candidate.title + " " + candidate.keywords.joined(separator: " ")).lowercased()
        let titleLower = candidate.title.lowercased()
        let idLower = candidate.id.lowercased()

        let phraseScore: Int
        if titleLower == normalized { phraseScore = 100 }
        else if titleLower.contains(normalized) { phraseScore = 80 }
        else if idLower.contains(normalized) { phraseScore = 70 }
        else if haystack.contains(normalized) { phraseScore = 60 }
        else { phraseScore = 0 }

        let tokenHits = queryTokens.filter { haystack.contains($0) }.count
        let score: Int
        if phraseScore > 0 { score = phraseScore + tokenHits }
        else if tokenHits > 0 { score = 20 + tokenHits * 15 }
        else { score = 0 }

        return (candidate.id, score)
    }

    return scored
        .enumerated()
        .filter { $0.element.score > 0 }
        // Sort by score desc, then by original index asc to keep input order on ties (stable).
        .sorted { $0.element.score != $1.element.score ? $0.element.score > $1.element.score : $0.offset < $1.offset }
        .prefix(limit)
        .map { $0.element.id }
}
