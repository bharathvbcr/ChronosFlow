// swift-tools-version: 5.9
import PackageDescription

// ChronosCore — the platform-agnostic logic core of the ChronosFlow iOS port.
//
// These are the pure algorithms ported from the Android core/domain/planner module: the dial
// minute↔angle math, sleep-readiness derivation, and conflict / free-window detection. They depend
// only on Foundation (no SwiftUI / SwiftData / CoreGraphics), so they compile and unit-test on ANY
// platform — including this Windows/Linux CI — which lets the ported logic be built and verified
// without a Mac. The iOS app's DialGeometry/Planner are the CoreGraphics+SwiftData-bound surface of
// these same formulas.
let package = Package(
    name: "ChronosCore",
    products: [
        .library(name: "ChronosCore", targets: ["ChronosCore"]),
    ],
    targets: [
        .target(name: "ChronosCore"),
        .testTarget(name: "ChronosCoreTests", dependencies: ["ChronosCore"]),
    ]
)
