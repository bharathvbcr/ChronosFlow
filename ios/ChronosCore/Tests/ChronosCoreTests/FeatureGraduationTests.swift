import XCTest
@testable import ChronosCore

/// Parity port of Android's `ChronosUiSettingsTest` graduation cases. The Android tests exercise a
/// persisted store; here we test the pure decision function plus a tiny in-memory store harness that
/// mirrors how a caller wires reads/writes (read → resolve → optionally set marker; write → set
/// value + marker). This proves the same observable behaviour: legacy parked-`false` values are
/// promoted once, then can be explicitly disabled.
final class FeatureGraduationTests: XCTestCase {

    // MARK: graduationForFlag — pure decision matrix

    func testMarkerUnsetForcesOnRegardlessOfStored() {
        // Legacy parked false, marker not yet promoted → reads as true, promotion is due.
        let r = FeatureGraduations.graduationForFlag(stored: false, markerSet: false, defaultOn: true)
        XCTAssertTrue(r.effective)
        XCTAssertTrue(r.newMarker)
    }

    func testMarkerUnsetForcesOnWhenStoredNil() {
        let r = FeatureGraduations.graduationForFlag(stored: nil, markerSet: false, defaultOn: true)
        XCTAssertTrue(r.effective)
        XCTAssertTrue(r.newMarker)
    }

    func testMarkerUnsetForcesOnEvenWhenStoredTrue() {
        let r = FeatureGraduations.graduationForFlag(stored: true, markerSet: false, defaultOn: true)
        XCTAssertTrue(r.effective)
        XCTAssertTrue(r.newMarker)
    }

    func testMarkerUnsetForcesOnEvenWhenDefaultOff() {
        // The pre-graduation default does not matter while the marker is unset: still force on.
        let r = FeatureGraduations.graduationForFlag(stored: nil, markerSet: false, defaultOn: false)
        XCTAssertTrue(r.effective)
        XCTAssertTrue(r.newMarker)
    }

    func testMarkerSetRespectsStoredFalse() {
        let r = FeatureGraduations.graduationForFlag(stored: false, markerSet: true, defaultOn: true)
        XCTAssertFalse(r.effective)
        XCTAssertFalse(r.newMarker)
    }

    func testMarkerSetRespectsStoredTrue() {
        let r = FeatureGraduations.graduationForFlag(stored: true, markerSet: true, defaultOn: true)
        XCTAssertTrue(r.effective)
        XCTAssertFalse(r.newMarker)
    }

    func testMarkerSetFallsBackToDefaultWhenStoredNil() {
        let on = FeatureGraduations.graduationForFlag(stored: nil, markerSet: true, defaultOn: true)
        XCTAssertTrue(on.effective)
        XCTAssertFalse(on.newMarker)

        let off = FeatureGraduations.graduationForFlag(stored: nil, markerSet: true, defaultOn: false)
        XCTAssertFalse(off.effective)
        XCTAssertFalse(off.newMarker)
    }

    func testResultStructMatchesTuple() {
        let result = FeatureGraduations.resolve(stored: false, markerSet: false, defaultOn: true)
        XCTAssertEqual(result, FeatureGraduationResult(effective: true, newMarker: true))
    }

    // MARK: Catalog — wave membership & marker mapping

    func testCatalogHasTwoWaves() {
        XCTAssertEqual(FeatureGraduations.all.count, 2)
    }

    func testHabitsMedicationWaveMembership() {
        let wave = FeatureGraduations.graduation(forKey: FeatureGraduationKeys.habitsEnabled)
        XCTAssertEqual(wave?.markerKey, FeatureGraduationKeys.habitsMedicationPromoted)
        XCTAssertEqual(
            wave?.featureKeys,
            [FeatureGraduationKeys.habitsEnabled, FeatureGraduationKeys.medicationEnabled]
        )
    }

    func testCompanionWaveMembership() {
        let companion: Set<String> = [
            FeatureGraduationKeys.reviewEnabled,
            FeatureGraduationKeys.aiAdvisorEnabled,
            FeatureGraduationKeys.goalsEnabled,
            FeatureGraduationKeys.journalEnabled,
            FeatureGraduationKeys.sleepEnabled
        ]
        for key in companion {
            XCTAssertEqual(
                FeatureGraduations.markerKey(forKey: key),
                FeatureGraduationKeys.companionPromoted,
                "\(key) must belong to the companion wave"
            )
        }
        let wave = FeatureGraduations.graduation(forKey: FeatureGraduationKeys.reviewEnabled)
        XCTAssertEqual(wave?.featureKeys, companion)
    }

    func testWavesAreDisjoint() {
        let wave1 = FeatureGraduations.all[0].featureKeys
        let wave2 = FeatureGraduations.all[1].featureKeys
        XCTAssertTrue(wave1.isDisjoint(with: wave2))
    }

    func testUnknownKeyIsNotGraduated() {
        XCTAssertNil(FeatureGraduations.graduation(forKey: "feature.somethingElse"))
        XCTAssertNil(FeatureGraduations.markerKey(forKey: "feature.somethingElse"))
        XCTAssertFalse(FeatureGraduations.isGraduated("feature.somethingElse"))
    }

    func testGraduatedKeysReportTrue() {
        XCTAssertTrue(FeatureGraduations.isGraduated(FeatureGraduationKeys.habitsEnabled))
        XCTAssertTrue(FeatureGraduations.isGraduated(FeatureGraduationKeys.sleepEnabled))
    }

    // MARK: resolveFlag — non-graduated passthrough

    func testNonGraduatedKeyIgnoresMarkerAndUsesStored() {
        let r = FeatureGraduations.resolveFlag(
            key: "feature.routinesEnabled",  // iOS-native flag, never graduated
            stored: false,
            markerSet: false,
            defaultOn: true
        )
        XCTAssertFalse(r.effective)   // stored false respected, NOT force-promoted
        XCTAssertFalse(r.newMarker)
    }

    func testNonGraduatedKeyUsesDefaultWhenNil() {
        let r = FeatureGraduations.resolveFlag(
            key: "feature.routinesEnabled",
            stored: nil,
            markerSet: false,
            defaultOn: true
        )
        XCTAssertTrue(r.effective)
        XCTAssertFalse(r.newMarker)
    }

    func testResolveFlagGraduatedDelegates() {
        let r = FeatureGraduations.resolveFlag(
            key: FeatureGraduationKeys.habitsEnabled,
            stored: false,
            markerSet: false,
            defaultOn: true
        )
        XCTAssertTrue(r.effective)
        XCTAssertTrue(r.newMarker)
    }

    // MARK: End-to-end parity via an in-memory store harness

    /// Minimal store mirroring how a caller persists flags + markers, so we can assert the same
    /// observable behaviour Android's persisted-store tests assert.
    private struct Store {
        var values: [String: Bool] = [:]
        var markers: Set<String> = []   // marker keys whose value is `true`

        /// Read with graduation applied; persists the marker on first promotion (Android reads do
        /// not write, but the *next deliberate write* sets it — we model the durable outcome here by
        /// honouring `newMarker` only on writes, matching Android exactly. Reads stay side-effect free.)
        mutating func read(_ key: String, defaultOn: Bool) -> Bool {
            let marker = FeatureGraduations.markerKey(forKey: key)
            let markerSet = marker.map { markers.contains($0) } ?? true
            let r = FeatureGraduations.resolveFlag(
                key: key, stored: values[key], markerSet: markerSet, defaultOn: defaultOn
            )
            return r.effective
        }

        /// Deliberate write: persist the value and set the wave marker (Android's
        /// `writeChronosUiBooleanSetting`).
        mutating func write(_ key: String, _ value: Bool) {
            values[key] = value
            if let marker = FeatureGraduations.markerKey(forKey: key) {
                markers.insert(marker)
            }
        }
    }

    func testLegacyParkedHabitsAndMedsPromotedOnce() {
        var store = Store()
        // Simulate a legacy install that parked false *before* graduation: values present, marker not set.
        store.values[FeatureGraduationKeys.habitsEnabled] = false
        store.values[FeatureGraduationKeys.medicationEnabled] = false

        XCTAssertTrue(store.read(FeatureGraduationKeys.habitsEnabled, defaultOn: true))
        XCTAssertTrue(store.read(FeatureGraduationKeys.medicationEnabled, defaultOn: true))
    }

    func testLegacyParkedReviewAndAiPromotedOnce() {
        var store = Store()
        store.values[FeatureGraduationKeys.reviewEnabled] = false
        store.values[FeatureGraduationKeys.aiAdvisorEnabled] = false

        XCTAssertTrue(store.read(FeatureGraduationKeys.reviewEnabled, defaultOn: true))
        XCTAssertTrue(store.read(FeatureGraduationKeys.aiAdvisorEnabled, defaultOn: true))
    }

    func testLegacyParkedJournalAndSleepPromotedOnce() {
        var store = Store()
        store.values[FeatureGraduationKeys.journalEnabled] = false
        store.values[FeatureGraduationKeys.sleepEnabled] = false

        XCTAssertTrue(store.read(FeatureGraduationKeys.journalEnabled, defaultOn: true))
        XCTAssertTrue(store.read(FeatureGraduationKeys.sleepEnabled, defaultOn: true))
    }

    func testPromotedHabitsAndMedsCanStillBeDisabledExplicitly() {
        var store = Store()
        // Deliberate user choice → sets value AND marker.
        store.write(FeatureGraduationKeys.habitsEnabled, false)
        store.write(FeatureGraduationKeys.medicationEnabled, false)

        XCTAssertFalse(store.read(FeatureGraduationKeys.habitsEnabled, defaultOn: true))
        XCTAssertFalse(store.read(FeatureGraduationKeys.medicationEnabled, defaultOn: true))
    }

    func testPromotedReviewAndAiCanStillBeDisabledExplicitly() {
        var store = Store()
        store.write(FeatureGraduationKeys.reviewEnabled, false)
        store.write(FeatureGraduationKeys.aiAdvisorEnabled, false)

        XCTAssertFalse(store.read(FeatureGraduationKeys.reviewEnabled, defaultOn: true))
        XCTAssertFalse(store.read(FeatureGraduationKeys.aiAdvisorEnabled, defaultOn: true))
    }

    func testPromotedJournalAndSleepCanStillBeDisabledExplicitly() {
        var store = Store()
        store.write(FeatureGraduationKeys.journalEnabled, false)
        store.write(FeatureGraduationKeys.sleepEnabled, false)

        XCTAssertFalse(store.read(FeatureGraduationKeys.journalEnabled, defaultOn: true))
        XCTAssertFalse(store.read(FeatureGraduationKeys.sleepEnabled, defaultOn: true))
    }

    func testDisablingOneCompanionDoesNotDisableOtherWave() {
        var store = Store()
        store.write(FeatureGraduationKeys.reviewEnabled, false)
        store.write(FeatureGraduationKeys.goalsEnabled, false)

        XCTAssertFalse(store.read(FeatureGraduationKeys.reviewEnabled, defaultOn: true))
        XCTAssertFalse(store.read(FeatureGraduationKeys.goalsEnabled, defaultOn: true))
        // The habits+medication wave must remain promoted on-by-default (separate marker).
        XCTAssertTrue(store.read(FeatureGraduationKeys.habitsEnabled, defaultOn: true))
    }

    func testWritingOneFlagInAWavePromotesTheWholeWave() {
        var store = Store()
        // Legacy parked false on BOTH companion flags, then user explicitly disables only one.
        store.values[FeatureGraduationKeys.reviewEnabled] = false
        store.values[FeatureGraduationKeys.aiAdvisorEnabled] = false

        // User makes a deliberate choice about review only → marker now set for the whole wave.
        store.write(FeatureGraduationKeys.reviewEnabled, false)

        // review respects its explicit false; aiAdvisor's parked false is now respected too
        // (the shared marker is set), matching Android's single-marker-per-wave semantics.
        XCTAssertFalse(store.read(FeatureGraduationKeys.reviewEnabled, defaultOn: true))
        XCTAssertFalse(store.read(FeatureGraduationKeys.aiAdvisorEnabled, defaultOn: true))
    }

    func testFreshInstallAllGraduatedDefaultOn() {
        var store = Store()
        for key in [
            FeatureGraduationKeys.habitsEnabled,
            FeatureGraduationKeys.medicationEnabled,
            FeatureGraduationKeys.reviewEnabled,
            FeatureGraduationKeys.aiAdvisorEnabled,
            FeatureGraduationKeys.goalsEnabled,
            FeatureGraduationKeys.journalEnabled,
            FeatureGraduationKeys.sleepEnabled
        ] {
            XCTAssertTrue(store.read(key, defaultOn: true), "\(key) should default on for fresh install")
        }
    }

    func testReEnableAfterExplicitDisable() {
        var store = Store()
        store.write(FeatureGraduationKeys.sleepEnabled, false)
        XCTAssertFalse(store.read(FeatureGraduationKeys.sleepEnabled, defaultOn: true))
        store.write(FeatureGraduationKeys.sleepEnabled, true)
        XCTAssertTrue(store.read(FeatureGraduationKeys.sleepEnabled, defaultOn: true))
    }
}
