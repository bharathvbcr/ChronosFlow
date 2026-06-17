import Foundation

// Portable copies of the domain enums (Foundation-only). Identical raw values to the app's
// Models/Enums.swift so behaviour matches across the portable core and the iOS target.

public enum BlockFlexibility: String, Codable, CaseIterable, Sendable {
    case fixed, movable, resizable, optional
}

public enum EnergyIntensity: Int, Codable, CaseIterable, Sendable {
    case low = 1, moderate = 2, high = 3, intense = 4, max = 5

    public static func fromLevel(_ level: Int?) -> EnergyIntensity {
        EnergyIntensity(rawValue: level ?? 2) ?? .moderate
    }
}

public enum SleepReadiness: String, Codable, Sendable {
    case unknown, depleted, normal, rested
}
