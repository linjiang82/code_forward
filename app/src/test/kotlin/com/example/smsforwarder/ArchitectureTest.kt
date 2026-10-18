package com.example.smsforwarder

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Enforces the dependency rule by scanning imports: data and presentation depend on domain,
 * domain depends on nothing else in the app and on no Android API. Only `di` may see everything.
 */
class ArchitectureTest {
    private companion object {
        const val ROOT = "com.example.smsforwarder"

        // Unit tests run with the module directory as the working directory.
        val sourceRoot = File("src/main/kotlin/com/example/smsforwarder")
    }

    @Test
    fun `domain depends only on itself and on no Android API`() {
        assertOnlyImports("domain", allowedProjectPackages = listOf("domain"), allowAndroid = false)
    }

    @Test
    fun `data depends only on domain`() {
        assertOnlyImports("data", allowedProjectPackages = listOf("domain", "data"))
    }

    @Test
    fun `presentation depends only on domain`() {
        assertOnlyImports("ui", allowedProjectPackages = listOf("domain", "ui"), allowedClasses = listOf("SmsForwarderApp"))
    }

    @Test
    fun `background entry points depend only on domain`() {
        assertOnlyImports(
            "background",
            allowedProjectPackages = listOf("domain", "background"),
            allowedClasses = listOf("SmsForwarderApp"),
        )
    }

    private fun assertOnlyImports(
        layer: String,
        allowedProjectPackages: List<String>,
        allowedClasses: List<String> = emptyList(),
        allowAndroid: Boolean = true,
    ) {
        val files = File(sourceRoot, layer).walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("No sources found for layer '$layer' in ${sourceRoot.absolutePath}", files.isNotEmpty())

        val violations =
            files.flatMap { file ->
                file.readLines()
                    .map { it.trim() }
                    .filter { it.startsWith("import ") }
                    .map { it.removePrefix("import ").substringBefore(" as ") }
                    .filterNot { isAllowed(it, allowedProjectPackages, allowedClasses, allowAndroid) }
                    .map { "${file.name}: import $it" }
            }

        assertTrue("Layer '$layer' breaks the dependency rule:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    private fun isAllowed(
        import: String,
        allowedProjectPackages: List<String>,
        allowedClasses: List<String>,
        allowAndroid: Boolean,
    ): Boolean =
        when {
            import.startsWith("android.") || import.startsWith("androidx.") -> allowAndroid
            import.startsWith("$ROOT.") -> {
                val rest = import.removePrefix("$ROOT.")
                allowedProjectPackages.any { rest.startsWith("$it.") } || allowedClasses.any { rest == it }
            }
            else -> true
        }
}
