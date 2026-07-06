import Foundation

/// On-device semantic response cache — the near-duplicate layer in front of the exact-key
/// ``ResponseLRU``. Where the LRU only replays a byte-identical prompt, this also reuses a prior
/// on-device generation for a prompt that differs only trivially (reordered words, whitespace, a
/// stray edit), so a cold Foundation Models inference is skipped far more often.
///
/// This is the Swift port of the Android `SemanticResponseCache`; the two keep identical matching
/// semantics so the platforms behave the same.
///
/// ### How a match is decided
/// Similarity is **term-frequency cosine** over tokenised prompt text — fully on-device and
/// dependency-free (no embedding model is shipped). A neighbour is accepted only when it:
///  1. lives in the same `namespace` (e.g. the generation profile), so differently-sampled requests
///     never share an answer;
///  2. clears `similarityThreshold`; and
///  3. beats the runner-up neighbour by `similarityMargin` (ambiguity rejection — if two stored
///     prompts are both "close" we cannot safely pick one, so we miss and let inference run).
///
/// ### Why the caller passes `semanticText` separately from `exactKey`
/// Planning prompts are `prefix + suffix`, where the prefix is a large *static* role/schema block
/// shared by every request. Cosine over the whole prompt would be dominated by that shared prefix
/// and could merge two genuinely different requests. Callers therefore pass only the **dynamic** part
/// as `semanticText` (the suffix for prefix-shared calls, or the whole prompt for plain calls); the
/// exact replay key still covers the full prompt.
///
/// Bounded, access-ordered, in-memory, per-entry TTL — nothing is persisted off-device. Not
/// thread-safe; callers serialize access (on-device inference is effectively serial).
public final class SemanticResponseCache {
    private struct Entry {
        let namespace: String
        /// L2-normalised term-frequency vector of the entry's semantic text.
        let vector: [String: Double]
        let tokenCount: Int
        let value: String
        let storedAtMs: Int64
        var tick: UInt64
    }

    private let capacity: Int
    private let ttlMs: Int64
    private let similarityThreshold: Double
    private let similarityMargin: Double
    private let minTokensForSemantic: Int

    private var storage: [String: Entry] = [:]
    private var clock: UInt64 = 0

    public init(
        capacity: Int = 32,
        ttlMs: Int64 = 10 * 60 * 1000,
        similarityThreshold: Double = 0.90,
        similarityMargin: Double = 0.05,
        minTokensForSemantic: Int = 3
    ) {
        self.capacity = max(capacity, 1)
        self.ttlMs = ttlMs
        self.similarityThreshold = similarityThreshold
        self.similarityMargin = similarityMargin
        self.minTokensForSemantic = minTokensForSemantic
    }

    public var count: Int { storage.count }

    /// Exact replay only: returns the value stored under `exactKey` if present and unexpired.
    public func getExact(_ exactKey: String, nowMs: Int64) -> String? {
        guard var entry = storage[exactKey] else { return nil }
        if isExpired(entry, nowMs) {
            storage.removeValue(forKey: exactKey)
            return nil
        }
        clock &+= 1
        entry.tick = clock
        storage[exactKey] = entry
        return entry.value
    }

    /// Exact first, then the best same-namespace near-duplicate of `semanticText`. Returns `nil`
    /// (a miss) unless a neighbour clears the threshold and the margin.
    public func lookup(exactKey: String, namespace: String, semanticText: String, nowMs: Int64) -> String? {
        if let exact = getExact(exactKey, nowMs: nowMs) { return exact }

        let tokens = tokenize(semanticText)
        if tokens.count < minTokensForSemantic { return nil }
        let queryVector = normalize(termFrequency(tokens))
        if queryVector.isEmpty { return nil }

        var bestKey: String?
        var bestScore = -1.0
        var secondScore = -1.0
        var expiredKeys: [String] = []
        for (key, entry) in storage {
            if isExpired(entry, nowMs) {
                expiredKeys.append(key)
                continue
            }
            if entry.namespace != namespace { continue }
            if entry.tokenCount < minTokensForSemantic { continue }
            let score = cosine(queryVector, entry.vector)
            if score > bestScore {
                secondScore = bestScore
                bestScore = score
                bestKey = key
            } else if score > secondScore {
                secondScore = score
            }
        }
        for key in expiredKeys { storage.removeValue(forKey: key) }

        guard let key = bestKey else { return nil }
        if bestScore < similarityThreshold { return nil }
        // Ambiguity gate: a clear runner-up means we cannot safely pick a single answer.
        if secondScore >= 0 && bestScore - secondScore < similarityMargin { return nil }

        guard var winner = storage[key] else { return nil }
        if isExpired(winner, nowMs) {
            storage.removeValue(forKey: key)
            return nil
        }
        clock &+= 1
        winner.tick = clock
        storage[key] = winner
        return winner.value
    }

    public func put(exactKey: String, namespace: String, semanticText: String, value: String, nowMs: Int64) {
        let tokens = tokenize(semanticText)
        clock &+= 1
        storage[exactKey] = Entry(
            namespace: namespace,
            vector: normalize(termFrequency(tokens)),
            tokenCount: tokens.count,
            value: value,
            storedAtMs: nowMs,
            tick: clock
        )
        if storage.count > capacity, let oldest = storage.min(by: { $0.value.tick < $1.value.tick })?.key {
            storage.removeValue(forKey: oldest)
        }
    }

    public func clear() {
        storage.removeAll()
        clock = 0
    }

    private func isExpired(_ entry: Entry, _ nowMs: Int64) -> Bool { nowMs - entry.storedAtMs > ttlMs }

    private func tokenize(_ text: String) -> [String] {
        text.lowercased()
            .split(whereSeparator: { !$0.isLetter && !$0.isNumber })
            .map(String.init)
            .filter { $0.count > 1 }
    }

    private func termFrequency(_ tokens: [String]) -> [String: Double] {
        var counts: [String: Double] = [:]
        for token in tokens { counts[token, default: 0] += 1 }
        return counts
    }

    private func normalize(_ vector: [String: Double]) -> [String: Double] {
        if vector.isEmpty { return vector }
        let norm = (vector.values.reduce(0) { $0 + $1 * $1 }).squareRoot()
        if norm == 0 { return vector }
        return vector.mapValues { $0 / norm }
    }

    /// Dot product of two already L2-normalised vectors == cosine similarity.
    private func cosine(_ a: [String: Double], _ b: [String: Double]) -> Double {
        let (small, large) = a.count <= b.count ? (a, b) : (b, a)
        var dot = 0.0
        for (term, weight) in small {
            if let other = large[term] { dot += weight * other }
        }
        return dot
    }
}
