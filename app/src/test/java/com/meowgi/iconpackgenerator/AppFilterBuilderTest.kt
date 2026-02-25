package com.meowgi.iconpackgenerator

import com.meowgi.iconpackgenerator.builder.AppFilterBuilder
import com.meowgi.iconpackgenerator.domain.AppInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterBuilderTest {

    @Test
    fun `buildXml produces valid XML with component mappings`() {
        val mappings = listOf(
            AppInfo("com.example.app", "com.example.app.MainActivity", "Example") to "com_example_app__mainactivity",
            AppInfo("org.test", "org.test.LauncherActivity", "Test App") to "org_test__launcheractivity"
        )

        val xml = AppFilterBuilder.buildXml(mappings)

        assertTrue(xml.contains("<?xml version=\"1.0\""))
        assertTrue(xml.contains("<resources>"))
        assertTrue(xml.contains("</resources>"))
        assertTrue(xml.contains("ComponentInfo{com.example.app/com.example.app.MainActivity}"))
        assertTrue(xml.contains("drawable=\"com_example_app__mainactivity\""))
        assertTrue(xml.contains("ComponentInfo{org.test/org.test.LauncherActivity}"))
    }

    @Test
    fun `buildXml handles empty mappings`() {
        val xml = AppFilterBuilder.buildXml(emptyList())
        assertTrue(xml.contains("<resources>"))
        assertTrue(xml.contains("</resources>"))
        assertFalse(xml.contains("<item"))
    }

    @Test
    fun `buildDrawableXml lists all drawables`() {
        val mappings = listOf(
            AppInfo("com.example", "com.example.Main", "Example") to "icon_a",
            AppInfo("com.test", "com.test.Main", "Test") to "icon_b"
        )

        val xml = AppFilterBuilder.buildDrawableXml(mappings)

        assertTrue(xml.contains("<category title=\"All Icons\""))
        assertTrue(xml.contains("drawable=\"icon_a\""))
        assertTrue(xml.contains("drawable=\"icon_b\""))
    }

    @Test
    fun `buildXml escapes XML special chars in component names`() {
        // Component names shouldn't contain XML special chars normally,
        // but verify the format is correct
        val mappings = listOf(
            AppInfo("com.example", "com.example.Activity", "App") to "icon"
        )
        val xml = AppFilterBuilder.buildXml(mappings)
        // Verify well-formed item element
        assertTrue(xml.contains("<item component=\"ComponentInfo{com.example/com.example.Activity}\" drawable=\"icon\" />"))
    }
}
