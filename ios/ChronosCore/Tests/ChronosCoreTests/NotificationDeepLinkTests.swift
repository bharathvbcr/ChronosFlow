import XCTest
@testable import ChronosCore

final class NotificationDeepLinkTests: XCTestCase {
    func testExplicitDeepLinkWins() {
        let url = NotificationDeepLink.resolveURL(from: ["deepLink": "chronosflow://tasks?id=t1"])
        XCTAssertEqual(url?.absoluteString, "chronosflow://tasks?id=t1")
    }

    func testLegacyTaskIDKey() {
        let url = NotificationDeepLink.resolveURL(from: ["taskID": "task-42"])
        XCTAssertEqual(url?.absoluteString, "chronosflow://tasks?id=task-42")
    }

    func testLegacyPlanIDKey() {
        let url = NotificationDeepLink.resolveURL(from: ["planID": "med-7"])
        XCTAssertEqual(url?.absoluteString, "chronosflow://medication?id=med-7")
    }

    func testLegacyHabitIDKey() {
        let url = NotificationDeepLink.resolveURL(from: ["habitID": "habit-3"])
        XCTAssertEqual(url?.absoluteString, "chronosflow://habits?id=habit-3")
    }

    func testBlockIDDeepLink() {
        let url = NotificationDeepLink.resolveURL(from: ["blockID": "blk-1"])
        XCTAssertEqual(url?.absoluteString, "chronosflow://focus?blockId=blk-1")
    }

    func testSleepSectionOpensLog() {
        let url = NotificationDeepLink.resolveURL(from: ["section": "sleep"])
        XCTAssertEqual(url?.absoluteString, "chronosflow://sleep?log=1")
    }

    func testSectionRoutesToHost() {
        let url = NotificationDeepLink.resolveURL(from: ["section": "medication"])
        XCTAssertEqual(url?.absoluteString, "chronosflow://medication")
    }

    func testUnknownUserInfoReturnsNil() {
        XCTAssertNil(NotificationDeepLink.resolveURL(from: ["foo": "bar"]))
    }
}
