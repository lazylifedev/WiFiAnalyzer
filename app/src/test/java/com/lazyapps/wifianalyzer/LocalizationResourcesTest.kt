package com.lazyapps.wifianalyzer

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalizationResourcesTest {
    private data class Entry(val kind: String, val quantities: Set<String>, val formats: List<String>)

    private fun entries(path: String): Map<String, Entry> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val result = linkedMapOf<String, Entry>()
        val children = document.documentElement.childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node.nodeName !in setOf("string", "plurals")) continue
            val name = node.attributes.getNamedItem("name").nodeValue
            val quantities = if (node.nodeName == "plurals") {
                (0 until node.childNodes.length).mapNotNull { childIndex ->
                    node.childNodes.item(childIndex).attributes?.getNamedItem("quantity")?.nodeValue
                }.toSet()
            } else emptySet()
            val formats = Regex("%(?:\\d+\\$)?[dfs]").findAll(node.textContent).map { it.value }.toList()
            result[name] = Entry(node.nodeName, quantities, formats)
        }
        return result
    }

    @Test
    fun englishAndJapaneseResourcesHaveMatchingContracts() {
        val english = entries("src/main/res/values/strings.xml")
        val japanese = entries("src/main/res/values-ja/strings.xml")
        assertEquals(english.keys, japanese.keys)
        english.forEach { (key, value) -> assertEquals(key, value, japanese[key]) }
    }

    @Test
    fun englishDefaultContainsNoJapaneseText() {
        val source = File("src/main/res/values/strings.xml").readText()
        assertFalse(Regex("[\\u3040-\\u30ff\\u3400-\\u9fff]").containsMatchIn(source))
    }

    @Test
    fun missingTranslationIsNotSuppressed() {
        listOf("src/main/res/values/strings.xml", "src/main/res/values-ja/strings.xml").forEach { path ->
            assertFalse(File(path).readText().contains("tools:ignore=\"MissingTranslation\""))
        }
    }
}
