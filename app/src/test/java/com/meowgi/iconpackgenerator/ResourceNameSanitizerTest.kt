package com.meowgi.iconpackgenerator

import com.meowgi.iconpackgenerator.util.ResourceNameSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceNameSanitizerTest {

    @Test
    fun `basic package and activity produces valid name`() {
        val result = ResourceNameSanitizer.sanitize(
            "com.example.app",
            "com.example.app.MainActivity"
        )
        assertEquals("com_example_app__com_example_app_mainactivity", result)
    }

    @Test
    fun `name starting with digit gets ic_ prefix`() {
        val result = ResourceNameSanitizer.sanitize(
            "123app",
            "Activity"
        )
        assertTrue(result.startsWith("ic_"))
    }

    @Test
    fun `special characters are removed`() {
        val result = ResourceNameSanitizer.sanitize(
            "com.example-app",
            "com.example.Main\$Inner"
        )
        assertFalse(result.contains("-"))
        assertFalse(result.contains("$"))
        assertTrue(result.matches(Regex("[a-z0-9_]+")))
    }

    @Test
    fun `long names are truncated with hash`() {
        val longPkg = "com." + "a".repeat(150)
        val result = ResourceNameSanitizer.sanitize(longPkg, "Activity")
        assertTrue(result.length <= 200)
    }

    @Test
    fun `empty input produces fallback name`() {
        // All chars stripped would result in empty -> fallback
        val result = ResourceNameSanitizer.sanitize("---", "!!!")
        assertEquals("ic_unknown", result)
    }

    @Test
    fun `deduplication appends counter`() {
        val names = listOf("icon_a", "icon_a", "icon_b", "icon_a")
        val deduped = ResourceNameSanitizer.deduplicateNames(names)
        assertEquals(listOf("icon_a", "icon_a_1", "icon_b", "icon_a_2"), deduped)
    }

    @Test
    fun `no deduplication when names are unique`() {
        val names = listOf("icon_a", "icon_b", "icon_c")
        val deduped = ResourceNameSanitizer.deduplicateNames(names)
        assertEquals(names, deduped)
    }

    @Test
    fun `result only contains valid resource characters`() {
        val testCases = listOf(
            "com.google.android.apps.maps" to "com.google.android.apps.maps.MapsActivity",
            "org.mozilla.firefox" to "org.mozilla.fenix.HomeActivity",
            "com.whatsapp" to "com.whatsapp.Main",
        )
        for ((pkg, activity) in testCases) {
            val result = ResourceNameSanitizer.sanitize(pkg, activity)
            assertTrue(
                "Invalid chars in: $result",
                result.matches(Regex("[a-z0-9_]+"))
            )
            assertFalse("Empty result for $pkg/$activity", result.isEmpty())
        }
    }
}
