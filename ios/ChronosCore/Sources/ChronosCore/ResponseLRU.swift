import Foundation

// Bounded, access-ordered replay cache — pure port of GenAiResponseCache.
//
// Repeating the same on-device request (re-opening a plan, re-proofreading unchanged text) should
// return instantly instead of paying a cold inference. This is the deterministic, testable core of
// that cache: a fixed-capacity LRU keyed by a prompt hash, evicting the least-recently-used entry.
// It is in-memory only, so nothing is persisted off-device. Not thread-safe; callers serialize
// access (on-device inference is effectively serial).

public final class ResponseLRU {
    private struct Node {
        var value: String
        var tick: UInt64
    }

    private let capacity: Int
    private var storage: [String: Node] = [:]
    private var clock: UInt64 = 0

    public init(capacity: Int = 32) {
        self.capacity = max(capacity, 1)
    }

    public var count: Int { storage.count }

    /// Return the cached value for `key`, marking it most-recently-used. `nil` on a miss.
    public func get(_ key: String) -> String? {
        guard var node = storage[key] else { return nil }
        clock &+= 1
        node.tick = clock
        storage[key] = node
        return node.value
    }

    /// Store `value` for `key` (most-recently-used). Evicts the least-recently-used entry when over
    /// capacity. Re-putting an existing key refreshes its value and recency without growing the map.
    public func put(_ key: String, _ value: String) {
        clock &+= 1
        storage[key] = Node(value: value, tick: clock)
        if storage.count > capacity, let oldest = storage.min(by: { $0.value.tick < $1.value.tick })?.key {
            storage.removeValue(forKey: oldest)
        }
    }

    public func clear() {
        storage.removeAll()
        clock = 0
    }
}
