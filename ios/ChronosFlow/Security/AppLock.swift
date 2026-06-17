import SwiftUI
import LocalAuthentication
import Observation

// MARK: - AppLock
//
// Biometric / passcode gate for sensitive surfaces. iOS-native analogue of Android's
// SensitiveArea.MEDICATION app-lock: when the privacy flag is on, the Medication tab is
// protected behind Face ID / Touch ID with a device-passcode fallback.
//
// Design notes:
//  - Uses `.deviceOwnerAuthentication` (biometrics first, passcode fallback) so a user with
//    no enrolled biometrics — or a stolen-finger scenario — can still get in with the passcode.
//  - On a device with NO authentication configured at all (most Simulators), we must NOT
//    hard-lock the user out: `canEvaluatePolicy` fails, and we treat the surface as unlocked
//    while noting why (`unavailableReason`). This mirrors the Android behaviour where the lock
//    silently no-ops if no device credential is set.
//  - Re-locks whenever the app leaves the foreground, so backgrounding re-arms the gate.

@Observable
@MainActor
final class AppLock {
    /// True when the protected content may be shown. Starts locked; flips after a successful
    /// `unlock()` or when authentication is unavailable on this device.
    private(set) var isUnlocked: Bool = false

    /// Set when biometrics/passcode can't be evaluated (e.g. Simulator with no passcode). When
    /// non-nil the gate has opened itself so the user isn't stranded; surface it as an info note.
    private(set) var unavailableReason: String?

    /// True while an `evaluatePolicy` round-trip is in flight (drives the Unlock button spinner).
    private(set) var isAuthenticating: Bool = false

    /// Last authentication failure message, for an inline error under the Unlock button.
    private(set) var lastError: String?

    init() {}

    /// Attempt to unlock via biometrics with a passcode fallback. Safe to call repeatedly; a
    /// no-op while already unlocked or mid-authentication.
    func unlock() async {
        guard !isUnlocked, !isAuthenticating else { return }

        let context = LAContext()
        context.localizedFallbackTitle = "Use Passcode"

        var policyError: NSError?
        let policy: LAPolicy = .deviceOwnerAuthentication
        guard context.canEvaluatePolicy(policy, error: &policyError) else {
            // No biometrics AND no passcode configured (typical on Simulator). Don't strand the
            // user behind a lock they can never satisfy — open the gate but record why.
            unavailableReason = "Device authentication isn’t set up, so Medications aren’t locked."
            isUnlocked = true
            return
        }

        isAuthenticating = true
        lastError = nil
        defer { isAuthenticating = false }

        let reason = "Unlock to view your medications"
        do {
            let success = try await context.evaluatePolicy(policy, localizedReason: reason)
            withAnimation(ChronosMotion.smooth) { isUnlocked = success }
            if !success { lastError = "Authentication didn’t succeed. Try again." }
        } catch let error as LAError where error.code == .userCancel || error.code == .appCancel || error.code == .systemCancel {
            // User dismissed the sheet — stay locked, no error noise.
            lastError = nil
        } catch {
            lastError = error.localizedDescription
        }
    }

    /// Re-arm the gate (call when leaving the foreground). Leaves `unavailableReason` intact so
    /// an auth-less device stays effectively open on its next appearance.
    func lock() {
        guard unavailableReason == nil else { return }
        isUnlocked = false
        lastError = nil
    }
}

// MARK: - MedicationLockGate

/// Wraps protected content behind `AppLock`. Shows a glassy "locked" overlay with an Unlock
/// button while `ChronosSettings.shared.medicationLockEnabled` is on and the gate hasn't been
/// satisfied; otherwise renders `content`. Re-locks on background via `scenePhase`.
struct MedicationLockGate<Content: View>: View {
    @ViewBuilder var content: () -> Content

    @State private var lock = AppLock()
    @Environment(\.scenePhase) private var scenePhase

    /// Read once per body eval — the settings flag can be toggled live from Settings.
    private var lockEnabled: Bool { ChronosSettings.shared.medicationLockEnabled }

    var body: some View {
        ZStack {
            content()
                .accessibilityHidden(showOverlay)
                .blur(radius: showOverlay ? 18 : 0)
                .allowsHitTesting(!showOverlay)

            if showOverlay {
                LockedOverlay(lock: lock)
                    .transition(.opacity.combined(with: .scale(scale: 0.96)))
            }
        }
        .animation(ChronosMotion.smooth, value: showOverlay)
        .task(id: lockEnabled) {
            // On first appearance (or when the flag is switched on) immediately prompt so the
            // user lands on the biometric sheet rather than a dead "tap to unlock" wall.
            if lockEnabled, !lock.isUnlocked { await lock.unlock() }
        }
        .onChange(of: scenePhase) { _, phase in
            // Re-arm the gate whenever we leave the foreground.
            if phase != .active { lock.lock() }
        }
    }

    private var showOverlay: Bool { lockEnabled && !lock.isUnlocked }
}

private struct LockedOverlay: View {
    @Bindable var lock: AppLock

    var body: some View {
        VStack(spacing: ChronosSpacing.standard) {
            Image(systemName: "lock.fill")
                .font(.system(size: 44, weight: .semibold, design: .rounded))
                .foregroundStyle(ChronosColors.category("MEDICATION"))
                .symbolEffect(.bounce, value: lock.isAuthenticating)

            VStack(spacing: ChronosSpacing.micro) {
                Text("Medications are protected")
                    .font(.chronosTitle)
                Text("Unlock with Face ID, Touch ID, or your passcode to view your medication schedule.")
                    .font(.chronosCaption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            Button {
                Task { await lock.unlock() }
            } label: {
                Label(lock.isAuthenticating ? "Unlocking…" : "Unlock",
                      systemImage: "faceid")
                    .font(.chronosLabel)
                    .padding(.horizontal, ChronosSpacing.standard)
                    .padding(.vertical, ChronosSpacing.small)
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.capsule)
            .tint(ChronosColors.category("MEDICATION"))
            .disabled(lock.isAuthenticating)

            if let error = lock.lastError {
                Text(error)
                    .font(.chronosCaption)
                    .foregroundStyle(ChronosColors.brandAccent)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(ChronosSpacing.medium)
        .frame(maxWidth: 360)
        .modifier(LockedOverlayBackground())
        .padding(ChronosSpacing.standard)
    }
}

/// Glassy card chrome for the locked overlay, with the reduce-transparency fallback that the
/// rest of the design system uses.
private struct LockedOverlayBackground: ViewModifier {
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: ChronosRadius.extraLarge, style: .continuous)
        if reduceTransparency {
            content
                .background(.regularMaterial, in: shape)
                .overlay(shape.strokeBorder(.separator, lineWidth: 1))
        } else {
            content.glassEffect(.regular.interactive(), in: shape)
        }
    }
}

extension View {
    /// Gate this view behind the medication app-lock (respects the privacy settings flag).
    func medicationLock() -> some View {
        MedicationLockGate { self }
    }
}
