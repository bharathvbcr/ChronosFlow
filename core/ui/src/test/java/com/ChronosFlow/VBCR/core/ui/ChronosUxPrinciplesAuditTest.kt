package com.ChronosFlow.VBCR.core.ui

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.streams.asStream
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-scanning lint that keeps the app aligned to docs/UX_PRINCIPLES.md.
 *
 * It enforces, across every feature/app/core:ui UI source file, that:
 *  - standalone Material buttons go through the Chronos wrappers (so every button carries the
 *    bouncy press-scale), and
 *  - bottom sheets go through [com.ChronosFlow.VBCR.core.ui.shell.ChronosModalBottomSheet].
 *
 * Glance widget sources are exempt (they use the unrelated androidx.glance button API), as is the
 * wrapper file itself.
 */
class ChronosUxPrinciplesAuditTest {

    private val scannedModules = listOf(
        "core/ui/src/main/java",
        "app/src/main/java",
        "feature/daydial/src/main/java",
        "feature/tasks/src/main/java",
        "feature/habits/src/main/java",
        "feature/medication/src/main/java",
        "feature/goals/src/main/java",
        "feature/focus/src/main/java"
    )

    // Raw Material button calls not preceded by a word char or '.', so `ChronosButton(` and
    // qualified references are not matched.
    private val rawButtonRegex =
        Regex("""(?<![\w.])(Button|TextButton|OutlinedButton|FilledTonalButton|ElevatedButton|IconButton)\(""")
    private val rawChipRegex = Regex("""(?<![\w.])(FilterChip|AssistChip)\(""")
    private val rawMenuItemRegex = Regex("""(?<![\w.])DropdownMenuItem\(""")
    private val rawToggleRegex = Regex("""(?<![\w.])(Switch|Checkbox)\(""")
    private val rawSegmentedButtonRegex = Regex("""(?<![\w.])SegmentedButton\(""")
    private val rawModalSheetRegex = Regex("""(?<![\w.])ModalBottomSheet\(""")
    private val rawTopBarRegex =
        Regex("""(?<![\w.])(TopAppBar|CenterAlignedTopAppBar|LargeTopAppBar|MediumTopAppBar)\(""")

    // Token-mapped spacing only (ChronosSpacing.Micro/Small/Compact/Standard/Medium).
    private val magicSpacingDpRegex = Regex("""\b(4|8|12|16|24)\.dp\b""")

    private val circularProgressIndicatorCallRegex =
        Regex("""(?<![\w.])CircularProgressIndicator\s*\(""")
    private val smallInlineBusySizeRegex =
        Regex("""\.(?:size|height|width)\s*\(\s*(\d+(?:\.\d+)?)\s*\.dp\s*\)""")

    private val a2SpacingScopes = listOf(
        A2SpacingScope(
            relativePath =
                "feature/daydial/src/main/java/com/ChronosFlow/VBCR/feature/daydial/ui/DailyActionStrip.kt",
            functionNames = emptyList()
        ),
        A2SpacingScope(
            relativePath =
                "feature/daydial/src/main/java/com/ChronosFlow/VBCR/feature/daydial/ui/DailyDialRingModels.kt",
            functionNames = listOf("DailyDialCenterOverlay")
        ),
        A2SpacingScope(
            relativePath =
                "feature/daydial/src/main/java/com/ChronosFlow/VBCR/feature/daydial/ui/TodayTab.kt",
            functionNames = listOf("TodayDialHero", "DialZoomControls")
        )
    )

    @Test
    fun `standalone buttons go through the chronos wrappers`() {
        val offenders = scanUi { path, line ->
            rawButtonRegex.containsMatchIn(line)
        }
        assertTrue(
            "Use ChronosButton/ChronosTextButton/ChronosOutlinedButton/ChronosFilledTonalButton/" +
                "ChronosElevatedButton (they add the bouncy press-scale) instead of the raw Material " +
                "buttons:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun `chips go through the chronos wrappers`() {
        val offenders = scanUi { path, line ->
            rawChipRegex.containsMatchIn(line) && path.name != "ChronosChips.kt"
        }
        assertTrue(
            "Use ChronosFilterChip / ChronosAssistChip (they add the bouncy press-scale and the " +
                "Confirm haptic) instead of the raw Material chips:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun `segmented buttons go through the chronos wrapper`() {
        val offenders = scanUi { path, line ->
            rawSegmentedButtonRegex.containsMatchIn(line) && path.name != "ChronosSegmentedButton.kt"
        }
        assertTrue(
            "Use ChronosSegmentedButton (it adds the Confirm haptic) instead of the raw Material " +
                "SegmentedButton:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun `toggles go through the chronos wrappers`() {
        val offenders = scanUi { path, line ->
            rawToggleRegex.containsMatchIn(line) && path.name != "ChronosToggles.kt"
        }
        assertTrue(
            "Use ChronosSwitch / ChronosCheckbox (they add the Confirm haptic on flip) instead of " +
                "the raw Material Switch / Checkbox:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun `dropdown menu items go through the chronos wrapper`() {
        val offenders = scanUi { path, line ->
            rawMenuItemRegex.containsMatchIn(line) && path.name != "ChronosMaterialComponents.kt"
        }
        assertTrue(
            "Use ChronosDropdownMenuItem (it adds the Confirm haptic) instead of the raw Material " +
                "DropdownMenuItem:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun `bottom sheets go through ChronosModalBottomSheet`() {
        val offenders = scanUi { path, line ->
            rawModalSheetRegex.containsMatchIn(line) && path.name != "ChronosModalBottomSheet.kt"
        }
        assertTrue(
            "Use ChronosModalBottomSheet instead of a raw ModalBottomSheet:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun `top bars go through the chronos glass top bar`() {
        val offenders = scanUi { path, line ->
            rawTopBarRegex.containsMatchIn(line) && path.name != "ChronosGlassTopBar.kt"
        }
        assertTrue(
            "Use ChronosTopBar / ChronosGlassTopBar / ChronosScreenScaffold instead of a raw Material " +
                "TopAppBar (keeps the liquid-glass chrome consistent):\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    /**
     * Bans unconstrained / full-area [CircularProgressIndicator] as list/page first paint in
     * feature and app UI. Small inline busy indicators (`.size` / `.height` / `.width` ≤ 24.dp
     * in the call's modifier chain) remain allowed for button/action busy states.
     */
    @Test
    fun `unconstrained CircularProgressIndicator is banned in feature and app UI`() {
        val root = repoRoot()
        val featureAppModules = scannedModules.filter {
            it.startsWith("feature/") || it.startsWith("app/")
        }
        val offenders = featureAppModules
            .map { root.resolve(it) }
            .filter { it.exists() }
            .flatMap { moduleRoot ->
                Files.walk(moduleRoot).use { paths ->
                    paths
                        .filter { it.isRegularFile() && it.name.endsWith(".kt") }
                        .filter { !it.name.contains("Preview") }
                        .flatMap { path ->
                            val text = path.readText()
                            if ("import androidx.glance" in text) {
                                return@flatMap emptyList<String>().stream()
                            }
                            findUnconstrainedCircularProgressIndicators(text)
                                .map { line ->
                                    "${path.relativeTo(root)}:$line"
                                }
                                .stream()
                        }
                        .toList()
                }
            }
        assertTrue(
            "Use ChronosShimmerPlaceholder / ChronosSkeleton for list/page first paint, or constrain " +
                "CircularProgressIndicator with .size/.height/.width ≤ 24.dp for inline busy states:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    /**
     * Scoped spacing lint for Workstream A2 touchpoints only. Bans token-mapped spacing
     * literals (`4/8/12/16/24.dp`) in [DailyActionStrip], [DailyDialCenterOverlay],
     * [TodayDialHero], and [DialZoomControls] — not a whole-repo magic-dp ban.
     */
    @Test
    fun `a2 dial spacing uses ChronosSpacing tokens`() {
        val root = repoRoot()
        val offenders = a2SpacingScopes.flatMap { scope ->
            val path = root.resolve(scope.relativePath)
            if (!path.exists() || !path.isRegularFile()) {
                return@flatMap listOf("missing scoped file: ${scope.relativePath}")
            }
            val text = path.readText()
            val regions = if (scope.functionNames.isEmpty()) {
                listOf("file" to text)
            } else {
                scope.functionNames.map { name ->
                    name to (extractKotlinFunctionBody(text, name)
                        ?: return@flatMap listOf("${scope.relativePath}: missing function $name"))
                }
            }
            regions.flatMap { (regionName, regionText) ->
                regionText.lineSequence().mapIndexedNotNull { index, line ->
                    val code = line.substringBefore("//")
                    if (!magicSpacingDpRegex.containsMatchIn(code)) return@mapIndexedNotNull null
                    "${scope.relativePath}:$regionName:${index + 1}: ${line.trim()}"
                }
            }
        }
        assertTrue(
            "A2 dial spacing should use ChronosSpacing (Micro/Small/Compact/Standard/Medium) " +
                "instead of bare 4/8/12/16/24.dp:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    private fun scanUi(predicate: (Path, String) -> Boolean): List<String> {
        val root = repoRoot()
        return scannedModules
            .map { root.resolve(it) }
            .filter { it.exists() }
            .flatMap { moduleRoot ->
                Files.walk(moduleRoot).use { paths ->
                    paths
                        .filter { it.isRegularFile() && it.name.endsWith(".kt") }
                        .filter { it.name != "ChronosButtons.kt" }
                        .filter { !it.name.contains("Preview") }
                        .flatMap { path ->
                            val text = path.readText()
                            if ("import androidx.glance" in text) return@flatMap emptyList<String>().stream()
                            text.lineSequence()
                                .mapIndexedNotNull { index, line ->
                                    val code = line.substringBefore("//")
                                    if (predicate(path, code)) {
                                        "${path.relativeTo(root)}:${index + 1}: ${line.trim()}"
                                    } else {
                                        null
                                    }
                                }
                                .asStream()
                        }
                        .toList()
                }
            }
    }

    private fun repoRoot(): Path {
        var dir: Path? = Path.of("").absolute()
        while (dir != null) {
            if (dir.resolve("settings.gradle.kts").exists() || dir.resolve("settings.gradle").exists()) {
                return dir
            }
            dir = dir.parent
        }
        // Fallback: module cwd is core/ui, so two levels up is the repo root.
        return Path.of("").absolute().parent.parent
    }

    private fun findUnconstrainedCircularProgressIndicators(source: String): List<String> {
        val offenders = mutableListOf<String>()
        var searchFrom = 0
        while (true) {
            val match = circularProgressIndicatorCallRegex.find(source, searchFrom) ?: break
            val openParen = source.indexOf('(', match.range.first)
            if (openParen < 0) {
                searchFrom = match.range.last + 1
                continue
            }
            val callEnd = matchingParenIndex(source, openParen) ?: break
            val callText = source.substring(match.range.first, callEnd + 1)
            val lineNumber = source.substring(0, match.range.first).count { it == '\n' } + 1
            if (!hasSmallInlineBusyConstraint(callText)) {
                val firstLine = callText.lineSequence().first().trim()
                offenders += "$lineNumber: $firstLine"
            }
            searchFrom = callEnd + 1
        }
        return offenders
    }

    private fun hasSmallInlineBusyConstraint(callText: String): Boolean {
        return smallInlineBusySizeRegex.findAll(callText).any { match ->
            val value = match.groupValues[1].toDoubleOrNull() ?: return@any false
            value <= 24.0
        }
    }

    private fun matchingParenIndex(source: String, openParen: Int): Int? {
        var depth = 0
        var i = openParen
        while (i < source.length) {
            when (source[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return null
    }

    private fun extractKotlinFunctionBody(source: String, functionName: String): String? {
        val signature = Regex("""\bfun\s+$functionName\s*\(""")
        val match = signature.find(source) ?: return null
        val openParen = source.indexOf('(', match.range.first)
        if (openParen < 0) return null
        var depth = 0
        var i = openParen
        while (i < source.length) {
            when (source[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) break
                }
            }
            i++
        }
        if (i >= source.length) return null
        var j = i + 1
        while (j < source.length && source[j].isWhitespace()) j++
        if (j >= source.length || source[j] != '{') return null
        val bodyStart = j
        depth = 0
        while (j < source.length) {
            when (source[j]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return source.substring(bodyStart, j + 1)
                    }
                }
            }
            j++
        }
        return null
    }

    private data class A2SpacingScope(
        val relativePath: String,
        val functionNames: List<String>
    )
}
