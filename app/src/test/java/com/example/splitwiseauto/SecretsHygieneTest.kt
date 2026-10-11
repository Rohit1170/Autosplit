package com.example.splitwiseauto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * SPL-SEC-001 regression: the Google Maps key must never sit in a tracked
 * build file. It belongs in local.properties (gitignored) or an env var.
 *
 * Unit tests run with the working directory at app/, so the project root
 * is one level up.
 */
class SecretsHygieneTest {

    private val root: File = File("..").canonicalFile
    private val googleKey = Regex("""AIza[0-9A-Za-z_\-]{35}""")

    /** Tracked files that the build reads or ships. */
    private val trackedConfig = listOf(
        "gradle.properties",
        "build.gradle.kts",
        "settings.gradle.kts",
        "app/build.gradle.kts",
        "app/src/main/AndroidManifest.xml",
        "app/proguard-rules.pro"
    )

    @Test
    fun noGoogleApiKeyInTrackedConfig() {
        trackedConfig.map { File(root, it) }.filter { it.exists() }.forEach { f ->
            assertFalse(
                "Google API key found in ${f.name} - move it to local.properties",
                googleKey.containsMatchIn(f.readText())
            )
        }
    }

    @Test
    fun gradlePropertiesDoesNotDefineMapsKey() {
        val gp = File(root, "gradle.properties")
        if (!gp.exists()) return
        assertFalse(
            "MAPS_API_KEY must not be defined in tracked gradle.properties",
            gp.readLines().any { it.trimStart().startsWith("MAPS_API_KEY") }
        )
    }

    @Test
    fun localPropertiesIsGitIgnored() {
        val ignore = File(root, ".gitignore").readLines().map { it.trim() }
        assertTrue(
            "local.properties must stay gitignored (it holds the Maps key)",
            "local.properties" in ignore || "/local.properties" in ignore
        )
    }

    @Test
    fun manifestTakesKeyFromPlaceholderOnly() {
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("\${MAPS_API_KEY}"))
    }
}
