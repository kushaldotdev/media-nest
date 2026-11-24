package com.example.medianest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the accessibility contract of [com.example.medianest.ui.components.MediaNestSwitch]:
 * a raw material3 `Switch` dumps to uiautomator as `checkable=true text='' content-desc=''`,
 * so every screen switch must go through the MediaNest component and pass a `label`.
 */
class SwitchAccessibilityNamingTest {

    private val rawSwitchCall = Regex("""(?<![A-Za-z])Switch\(""")

    private fun screensDir(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            for (candidate in listOf(
                File(dir, "app/src/main/java/com/example/medianest/ui/screens"),
                File(dir, "src/main/java/com/example/medianest/ui/screens")
            )) {
                if (candidate.isDirectory) return candidate
            }
            dir = dir.parentFile
        }
        throw IllegalStateException("cannot locate ui/screens from ${System.getProperty("user.dir")}")
    }

    private fun screenSources(): List<File> = screensDir()
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .sortedBy { it.name }
        .toList()

    /** Argument text of every `MediaNestSwitch(...)` call, from the open paren to its match. */
    private fun mediaNestSwitchCallArguments(source: String): List<String> {
        val marker = "MediaNestSwitch("
        val calls = mutableListOf<String>()
        var index = source.indexOf(marker)
        while (index >= 0) {
            var depth = 0
            var cursor = index + marker.length - 1
            while (cursor < source.length) {
                when (source[cursor]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) break
                    }
                }
                cursor++
            }
            calls.add(source.substring(index + marker.length, cursor))
            index = source.indexOf(marker, cursor)
        }
        return calls
    }

    @Test
    fun noScreenCallsRawMaterial3Switch() {
        val offenders = screenSources().filter { rawSwitchCall.containsMatchIn(it.readText()) }
        assertEquals(
            "raw material3 Switch produces an unnamed checkbox in uiautomator; use MediaNestSwitch",
            emptyList<String>(),
            offenders.map { it.name }
        )
    }

    @Test
    fun everyMediaNestSwitchCallPassesALabel() {
        val unnamed = screenSources().flatMap { file ->
            mediaNestSwitchCallArguments(file.readText())
                .filterNot { it.contains("label =") }
                .map { "${file.name}: MediaNestSwitch($it)" }
        }
        assertTrue("MediaNestSwitch call without a label: $unnamed", unnamed.isEmpty())
    }
}
