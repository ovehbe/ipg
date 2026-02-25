package com.meowgi.iconpackgenerator.util

object ResourceNameSanitizer {

    private const val MAX_LENGTH = 200
    private val VALID_CHARS = Regex("[^a-z0-9_]")

    /**
     * Converts a component string like "com.example.app/com.example.app.MainActivity"
     * to a valid Android resource name like "com_example_app__com_example_app_mainactivity".
     */
    fun sanitize(packageName: String, activityName: String): String {
        val raw = "${packageName}__${activityName}"
            .lowercase()
            .replace('.', '_')
            .replace('/', '_')
            .replace('$', '_')
            .replace(VALID_CHARS, "")

        val trimmed = raw.trim('_')
        val result = if (trimmed.isEmpty()) {
            "ic_unknown"
        } else if (trimmed[0].isDigit()) {
            "ic_$trimmed"
        } else {
            trimmed
        }

        return if (result.length > MAX_LENGTH) {
            val hash = result.hashCode().toUInt().toString(16)
            result.substring(0, MAX_LENGTH - hash.length - 1) + "_" + hash
        } else {
            result
        }
    }

    /**
     * Deduplicate resource names by appending a counter suffix when collisions occur.
     */
    fun deduplicateNames(names: List<String>): List<String> {
        val seen = mutableMapOf<String, Int>()
        return names.map { name ->
            val count = seen.getOrDefault(name, 0)
            seen[name] = count + 1
            if (count == 0) name else "${name}_$count"
        }
    }
}
