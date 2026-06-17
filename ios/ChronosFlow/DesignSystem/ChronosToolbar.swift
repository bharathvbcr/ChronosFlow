import SwiftUI

// MARK: - iOS 27 toolbar behaviour
//
// iOS 27 lets a navigation bar minimize as the user scrolls down, giving the content more room
// and leaning into the Liquid Glass look. Centralised here so the whole app adopts it consistently
// (and so the single call site is easy to tune if the behaviour changes).

extension View {
    /// Minimize the navigation bar while scrolling down on the main scrollable screens.
    /// Apple's documented signature is the two-argument form with an explicit placement
    /// (`toolbarMinimizeBehavior(_:for:)`, WWDC26 / iOS 26+); the bare single-argument form is
    /// `tabBarMinimizeBehavior(_:)` (TabView), which is a different modifier.
    func chronosScrollMinimizedBar() -> some View {
        toolbarMinimizeBehavior(.onScrollDown, for: .navigationBar)
    }
}
