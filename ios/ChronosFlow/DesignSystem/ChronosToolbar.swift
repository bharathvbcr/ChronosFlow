import SwiftUI

// MARK: - iOS 27 toolbar behaviour
//
// iOS 27 lets a navigation bar minimize as the user scrolls down, giving the content more room
// and leaning into the Liquid Glass look. Centralised here so the whole app adopts it consistently
// (and so the single call site is easy to tune if the behaviour changes).

extension View {
    /// Minimize the navigation bar while scrolling down on the main scrollable screens.
    /// Apple's documented signature is the two-argument form with an explicit placement
    /// (`toolbarMinimizeBehavior(_:for:)`, WWDC26 / iOS 27 SDK); the bare single-argument form is
    /// `tabBarMinimizeBehavior(_:)` (TabView), which is a different modifier.
    ///
    /// `toolbarMinimizeBehavior(_:for:)` only ships in the iOS 27 SDK, so it is gated on the
    /// compiler version: Xcode 27 ships Swift 6.4+, whereas Xcode 26.x (Swift ≤ 6.3) lacks the
    /// symbol entirely and would fail to compile even behind an `#available` runtime check.
    /// On older toolchains this degrades to a no-op (the bar simply doesn't auto-minimize on
    /// scroll); rebuild with Xcode 27 to restore the behaviour.
    func chronosScrollMinimizedBar() -> some View {
        #if compiler(>=6.4)
        toolbarMinimizeBehavior(.onScrollDown, for: .navigationBar)
        #else
        self
        #endif
    }
}

// MARK: - Section(title:content:footer:) compatibility shim
//
// SwiftUI ships `Section(_:content:)` (string title, no footer) and `Section(content:header:footer:)`
// (closure header), but NOT a string-title + footer initializer. The app uses the
// `Section("Title") { … } footer: { … }` form pervasively, so this bridges that spelling to the
// closure-header initializer (`header: { Text(title) }`).
extension Section where Parent == Text, Content: View, Footer: View {
    init(_ titleKey: LocalizedStringKey,
         @ViewBuilder content: () -> Content,
         @ViewBuilder footer: () -> Footer) {
        self.init(content: content, header: { Text(titleKey) }, footer: footer)
    }
}
