import Foundation
import ChronosCore

// MARK: - In-memory response cache for the on-device text tools
//
// The iOS-native analogue of the Android `GenAiResponseCache`: a small, access-ordered LRU keyed
// by a prompt hash so a repeated transform (re-proofreading unchanged notes, re-summarizing the
// same text) returns instantly instead of paying a cold Foundation Models inference. The bounded
// LRU itself lives in ChronosCore (`ResponseLRU`, unit-tested on CI); this thin wrapper just builds
// a stable cache key from the operation + input and is pinned to the main actor to match the
// `@MainActor` text-tools class that owns it. In-memory only — nothing is persisted off-device.

@MainActor
final class ChronosResponseCache {
    private let lru: ResponseLRU

    init(capacity: Int = 32) {
        lru = ResponseLRU(capacity: capacity)
    }

    /// Cached result for `(op, input)`, or `nil` on a miss.
    func value(op: String, input: String) -> String? {
        lru.get(Self.key(op: op, input: input))
    }

    /// Store `result` for `(op, input)`.
    func store(op: String, input: String, result: String) {
        lru.put(Self.key(op: op, input: input), result)
    }

    /// A stable key for the op + input. `hashValue` is per-process-stable, which is all an
    /// in-memory cache needs; pairing it with the op keeps different transforms of the same text
    /// distinct, and the length guards against the rare hash collision producing a wrong hit.
    private static func key(op: String, input: String) -> String {
        "\(op)#\(input.count)#\(input.hashValue)"
    }
}
