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

// MARK: - SensitiveArea

/// Identifies a protected surface. iOS analogue of Android's SensitiveRouteGate generalization
/// (medication + data-export). Add new cases here when more areas need optional auth-gating.
enum SensitiveArea: String, CaseIterable {
    case medication
    case dataExport

    /// Whether this surface requires authentication right now (reads live settings flags).
    var requiresLock: Bool {
        switch self {
        case .medication: return ChronosSettings.shared.medicationLockEnabled
        case .dataExport: return false  // data export NOT locked by default (user can enable later)
        }
    }

    var lockedTitle: String {
        switch self {
        case .medication: return "Medications are protected"
        case .dataExport: return "Data export is protected"
        }
    }

    var lockedDescription: String {
        switch self {
        case .medication: return "Unlock with Face ID, Touch ID, or your passcode to view your medication schedule."
        case .dataExport: return "Unlock to export your personal health and schedule data."
        }
    }

    var unlockReason: String {
        switch self {
        case .medication: return "Unlock to view your medications"
        case .dataExport: return "Unlock to export your data"
        }
    }

    var systemImage: String {
        switch self {
        case .medication: return "pills.fill"
        case .dataExport: return "externaldrive.fill"
        }
    }

    var tint: Color { ChronosColors.category(rawValue.uppercased()) }

    var unavailableReason: String {
        "Device authentication isn't set up, so \(rawValue) isn't locked."
    }
}

// MARK: - SensitiveAreaGate

/// Generic auth gate for any `SensitiveArea`. Renders `content` normally when the area doesn't
/// require a lock or the user has already authenticated; otherwise shows a glassy `LockedOverlay`.
/// Re-locks whenever the app leaves the foreground.
struct SensitiveAreaGate<Content: View>: View {
    let area: SensitiveArea
    @ViewBuilder var content: () -> Content

    @State private var lock = AppLock()
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ZStack {
            content()
                .accessibilityHidden(showOverlay)
                .blur(radius: showOverlay ? 18 : 0)
                .allowsHitTesting(!showOverlay)

            if showOverlay {
                LockedOverlay(lock: lock, area: area)
                    .transition(.opacity.combined(with: .scale(scale: 0.96)))
            }
        }
        .animation(ChronosMotion.smooth, value: showOverlay)
        .task(id: area.requiresLock) {
            // Immediately prompt on first appearance (or when the flag is switched on) so the
            // user lands on the biometric sheet rather than a dead "tap to unlock" wall.
            if area.requiresLock, !lock.isUnlocked { await lock.unlock() }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { lock.lock() }
        }
    }

    private var showOverlay: Bool { area.requiresLock && !lock.isUnlocked }
}

// MARK: - MedicationLockGate (backwards compatibility)

/// Retained for call sites that haven't migrated to `SensitiveAreaGate` / `.sensitiveGate(_:)`.
/// Internally delegates to `SensitiveAreaGate(area: .medication)` so behaviour is identical.
struct MedicationLockGate<Content: View>: View {
    @ViewBuilder var content: () -> Content
    var body: some View { SensitiveAreaGate(area: .medication, content: content) }
}

// MARK: - LockedOverlay

private struct LockedOverlay: View {
    @Bindable var lock: AppLock
    let area: SensitiveArea

    var body: some View {
        VStack(spacing: ChronosSpacing.standard) {
            Image(systemName: area.systemImage)
                .font(.system(size: 44, weight: .semibold, design: .rounded))
                .foregroundStyle(area.tint)
                .symbolEffect(.bounce, value: lock.isAuthenticating)
                .accessibilityHidden(true)

            VStack(spacing: ChronosSpacing.micro) {
                Text(area.lockedTitle)
                    .font(.chronosTitle)
                Text(area.lockedDescription)
                    .font(.chronosCaption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            Button {
                Task { await lock.unlock() }
            } label: {
                HStack(spacing: ChronosSpacing.micro) {
                    if lock.isAuthenticating {
                        ProgressView()
                            .controlSize(.small)
                    } else {
                        Image(systemName: "faceid")
                    }
                    Text(lock.isAuthenticating ? "Unlocking…" : "Unlock")
                }
                .font(.chronosLabel)
                .padding(.horizontal, ChronosSpacing.standard)
                .padding(.vertical, ChronosSpacing.small)
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.capsule)
            .tint(area.tint)
            .disabled(lock.isAuthenticating)

            if let error = lock.lastError {
                Text(error)
                    .font(.chronosCaption)
                    .foregroundStyle(ChronosColors.brandAccent)
                    .multilineTextAlignment(.center)
            }

            if let note = lock.unavailableReason {
                Text(note)
                    .font(.chronosCaption)
                    .foregroundStyle(.tertiary)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(ChronosSpacing.medium)
        .frame(maxWidth: 360)
        .modifier(LockedOverlayBackground())
        .padding(ChronosSpacing.standard)
        .sensoryFeedback(.success, trigger: lock.isUnlocked) { old, new in !old && new }
        .sensoryFeedback(.error, trigger: lock.lastError) { _, new in new != nil }
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
    /// Gate this view behind the given `SensitiveArea`. Shows the auth overlay when the area's
    /// `requiresLock` flag is on and the device can evaluate a policy; fails open otherwise so
    /// no user is ever stranded (mirrors Android's `canAuthenticate == false` fallback).
    func sensitiveGate(_ area: SensitiveArea) -> some View {
        SensitiveAreaGate(area: area) { self }
    }

    /// Gate this view behind the medication app-lock (respects the privacy settings flag).
    /// Kept for backwards compatibility — internally calls `sensitiveGate(.medication)`.
    func medicationLock() -> some View {
        sensitiveGate(.medication)
    }
}
