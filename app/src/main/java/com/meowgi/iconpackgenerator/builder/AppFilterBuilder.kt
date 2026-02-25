package com.meowgi.iconpackgenerator.builder

import com.meowgi.iconpackgenerator.domain.AppInfo

object AppFilterBuilder {

    /**
     * Generates appfilter.xml content mapping components to drawable resource names.
     *
     * @param mappings List of pairs: (AppInfo, resourceName)
     */
    fun buildXml(mappings: List<Pair<AppInfo, String>>): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        sb.appendLine("<resources>")

        for ((appInfo, resourceName) in mappings) {
            val component = "ComponentInfo{${appInfo.packageName}/${appInfo.activityName}}"
            sb.appendLine("    <item component=\"$component\" drawable=\"$resourceName\" />")
        }

        sb.appendLine("</resources>")
        return sb.toString()
    }

    /**
     * Generates drawable.xml content for the launcher's manual icon picker.
     */
    fun buildDrawableXml(mappings: List<Pair<AppInfo, String>>): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        sb.appendLine("<resources>")
        sb.appendLine("    <category title=\"All Icons\" />")

        for ((appInfo, resourceName) in mappings) {
            sb.appendLine("    <item drawable=\"$resourceName\" />")
        }

        sb.appendLine("</resources>")
        return sb.toString()
    }
}
