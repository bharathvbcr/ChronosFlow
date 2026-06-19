package com.ChronosFlow.VBCR.core.ui.theme

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.io.path.readText
import kotlin.streams.asStream
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronosColorTokenAuditTest {

    @Test
    fun `core ui hard coded color literals stay in design tokens`() {
        val sourceRoot = findCoreUiSourceRoot()
        val offenders = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { it.isRegularFile() && it.name.endsWith(".kt") }
                .filter { path ->
                    path.relativeTo(sourceRoot).toString().replace('\\', '/') !=
                        "com/ChronosFlow/VBCR/core/ui/theme/DesignTokens.kt"
                }
                .flatMap { path ->
                    path.readText()
                        .lineSequence()
                        .mapIndexedNotNull { index, line ->
                            if ("Color(0x" in line) {
                                "${path.relativeTo(sourceRoot)}:${index + 1}: ${line.trim()}"
                            } else {
                                null
                            }
                        }
                        .asStream()
                }
                .toList()
        }

        assertTrue(
            "Raw Color(0x...) literals must be declared in DesignTokens.kt only:\n" +
                offenders.joinToString(separator = "\n"),
            offenders.isEmpty()
        )
    }

    private fun findCoreUiSourceRoot(): Path {
        val cwd = Path.of("").absolute()
        val moduleLocal = cwd.resolve("src/main/java")
        if (moduleLocal.exists()) return moduleLocal
        return cwd.resolve("core/ui/src/main/java")
    }
}
