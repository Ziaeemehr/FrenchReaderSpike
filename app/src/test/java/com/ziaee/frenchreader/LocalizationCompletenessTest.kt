package com.ziaee.frenchreader

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the localization foundation laid in the implementation plan's
 * Task 1: every resource set must define the same string keys, and no new
 * Persian UI literal may sneak back into Compose source instead of a
 * string resource -- Persian happens to be the only script every
 * already-hardcoded literal in this codebase used before migration, so a
 * literal starting with a Persian/Arabic character is exactly what "not
 * yet localized" looks like here. Source-provided French article content
 * is never a Kotlin string literal (it's RSS/Vikidia data held in
 * variables), so no allowlist has been needed so far.
 */
class LocalizationCompletenessTest {
    @Test
    fun `every locale resource set defines the same string names`() {
        val default = stringNames("src/main/res/values/strings.xml")
        val persian = stringNames("src/main/res/values-fa/strings.xml")
        val french = stringNames("src/main/res/values-fr/strings.xml")

        assertTrue(
            "values-fa differs from values: missing=${default - persian}, extra=${persian - default}",
            persian == default
        )
        assertTrue(
            "values-fr differs from values: missing=${default - french}, extra=${french - default}",
            french == default
        )
    }

    @Test
    fun `no hardcoded Persian UI literal remains in Compose source`() {
        val hardcodedLiteral = Regex("""(Text\(|contentDescription\s*=\s*|showSnackbar\()"[؀-ۿ]""")
        val violations = kotlinSourceFiles("src/main/java/com/ziaee/frenchreader").flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (hardcodedLiteral.containsMatchIn(line)) "${file.path}:${index + 1}: ${line.trim()}" else null
            }
        }
        assertTrue("Hardcoded Persian UI literals found:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    private fun kotlinSourceFiles(root: String): List<File> =
        File(root).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun stringNames(path: String): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }.toSet()
    }
}
