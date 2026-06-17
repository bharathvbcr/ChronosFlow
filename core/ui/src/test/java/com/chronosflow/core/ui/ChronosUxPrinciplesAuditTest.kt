package com.chronosflow.core.ui

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
 *  - bottom sheets go through [com.chronosflow.core.ui.shell.ChronosModalBottomSheet].
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
}
