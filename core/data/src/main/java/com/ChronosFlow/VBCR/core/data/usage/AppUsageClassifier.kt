package com.ChronosFlow.VBCR.core.data.usage

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.ChronosFlow.VBCR.core.domain.model.UsageCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default productive/distracting/neutral classification for a foreground app, derived from the
 * app's declared [ApplicationInfo.category]. Productivity/maps/news count as focused work;
 * games/social/video count as distractions; everything else (and uncategorised apps) is neutral.
 *
 * Kept deliberately simple and overridable — a later pass can layer user reclassification on top.
 */
@Singleton
class AppUsageClassifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val packageManager: PackageManager get() = context.packageManager

    fun categoryFor(packageName: String): UsageCategory {
        val declared = applicationInfoOrNull(packageName)?.category ?: ApplicationInfo.CATEGORY_UNDEFINED
        // A declared category is the app developer's own signal — always trust it.
        if (declared != ApplicationInfo.CATEGORY_UNDEFINED) return categoryForAppCategory(declared)
        // Most apps declare nothing, which would dump everything into NEUTRAL and make the
        // focused-vs-distracting split useless. Fall back to a known-package heuristic; the
        // user can still re-tag any app, and an override always wins over this.
        return knownPackageCategory(packageName) ?: UsageCategory.NEUTRAL
    }

    fun labelFor(packageName: String): String {
        val info = applicationInfoOrNull(packageName) ?: return packageName
        return runCatching { packageManager.getApplicationLabel(info).toString() }.getOrDefault(packageName)
    }

    private fun applicationInfoOrNull(packageName: String): ApplicationInfo? =
        runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull()

    private fun categoryForAppCategory(category: Int): UsageCategory = when (category) {
        ApplicationInfo.CATEGORY_PRODUCTIVITY,
        ApplicationInfo.CATEGORY_MAPS,
        ApplicationInfo.CATEGORY_NEWS -> UsageCategory.PRODUCTIVE

        ApplicationInfo.CATEGORY_GAME,
        ApplicationInfo.CATEGORY_SOCIAL,
        ApplicationInfo.CATEGORY_VIDEO -> UsageCategory.DISTRACTING

        else -> UsageCategory.NEUTRAL
    }
}

/**
 * Best-effort productivity guess for apps that declare no [ApplicationInfo.category] (the common
 * case), keyed off well-known package names so the focused/distracting split is meaningful before
 * the user re-tags anything. Returns null for unrecognised packages, so the caller can fall back to
 * NEUTRAL. A package matches either exactly or as a parent prefix (so `com.microsoft.office` also
 * catches `com.microsoft.office.word`). Pure and Context-free so it can be unit-tested directly.
 */
internal fun knownPackageCategory(packageName: String): UsageCategory? {
    val pkg = packageName.lowercase()
    fun matches(known: Set<String>) = known.any { pkg == it || pkg.startsWith("$it.") }
    return when {
        matches(KNOWN_DISTRACTING_PACKAGES) -> UsageCategory.DISTRACTING
        matches(KNOWN_PRODUCTIVE_PACKAGES) -> UsageCategory.PRODUCTIVE
        else -> null
    }
}

/** Social, short-video, streaming, dating, and a few mega-popular games (lowercase). */
private val KNOWN_DISTRACTING_PACKAGES = setOf(
    "com.instagram.android",
    "com.facebook.katana",
    "com.facebook.lite",
    "com.zhiliaoapp.musically", // TikTok
    "com.ss.android.ugc.trill", // TikTok (intl)
    "com.snapchat.android",
    "com.twitter.android",
    "com.reddit.frontpage",
    "com.google.android.youtube",
    "com.netflix.mediaclient",
    "com.pinterest",
    "tv.twitch.android.app",
    "com.discord",
    "com.tinder",
    "com.bumble.app",
    "com.king.candycrushsaga",
    "com.supercell.clashofclans",
    "com.roblox.client",
    "com.mojang.minecraftpe"
)

/** Productivity, docs, work comms, dev, and learning apps (lowercase). */
private val KNOWN_PRODUCTIVE_PACKAGES = setOf(
    "com.google.android.gm", // Gmail
    "com.google.android.apps.docs", // Drive + Docs/Sheets/Slides editors
    "com.google.android.keep",
    "com.google.android.calendar",
    "com.google.android.apps.tasks",
    "com.microsoft.office", // Word/Excel/PowerPoint/OneNote
    "com.microsoft.office.outlook",
    "com.microsoft.teams",
    "com.microsoft.todos",
    "com.slack",
    "com.todoist",
    "com.notion.id",
    "com.anydo",
    "com.evernote",
    "us.zoom.videomeetings",
    "com.trello",
    "com.asana.app",
    "com.atlassian.android.jira.core",
    "com.github.android",
    "com.duolingo"
)
