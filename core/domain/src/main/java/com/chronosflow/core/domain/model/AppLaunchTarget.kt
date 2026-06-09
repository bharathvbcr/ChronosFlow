package com.chronosflow.core.domain.model

data class AppLaunchTarget(
    val label: String,
    val value: String
)

data class AppLaunchComponent(
    val packageName: String,
    val className: String
) {
    val launchValue: String = "$componentLaunchPrefix$packageName/$className"
}

fun normalizeAppLaunchTarget(
    label: String,
    value: String
): AppLaunchTarget? {
    val normalizedValue = normalizeAppLaunchValue(value) ?: return null
    return AppLaunchTarget(
        label = label.trim().ifBlank { "Open app" },
        value = normalizedValue
    )
}

fun normalizeAppLaunchValue(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null

    val withoutPackagePrefix = trimmed.removePrefix("package:").trim()
    parseAppLaunchComponent(withoutPackagePrefix)?.let { return it.launchValue }
    return when {
        withoutPackagePrefix.startsWith("intent:", ignoreCase = true) -> withoutPackagePrefix
        uriSchemePattern.containsMatchIn(withoutPackagePrefix) -> withoutPackagePrefix
        packageNamePattern.matches(withoutPackagePrefix) -> withoutPackagePrefix
        else -> null
    }
}

fun parseAppLaunchComponent(value: String): AppLaunchComponent? {
    val trimmed = value.trim()
    if (!trimmed.startsWith(componentLaunchPrefix, ignoreCase = true)) return null
    val flattened = trimmed.substring(componentLaunchPrefix.length).trim()
    val separatorIndex = flattened.indexOf('/')
    if (separatorIndex <= 0 || separatorIndex == flattened.lastIndex) return null

    val packageName = flattened.substring(0, separatorIndex).trim()
    val className = flattened.substring(separatorIndex + 1).trim()
    if (!packageNamePattern.matches(packageName)) return null

    val normalizedClassName = when {
        className.startsWith(".") -> "$packageName$className"
        "." !in className -> "$packageName.$className"
        else -> className
    }
    if (!classNamePattern.matches(normalizedClassName)) return null

    return AppLaunchComponent(
        packageName = packageName,
        className = normalizedClassName
    )
}

fun isComponentLaunchValue(value: String): Boolean =
    parseAppLaunchComponent(value) != null

fun isPackageLaunchValue(value: String): Boolean =
    packageNamePattern.matches(value.trim().removePrefix("package:").trim())

private const val componentLaunchPrefix = "component:"
private val classNamePattern = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")
private val packageNamePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
private val uriSchemePattern = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
