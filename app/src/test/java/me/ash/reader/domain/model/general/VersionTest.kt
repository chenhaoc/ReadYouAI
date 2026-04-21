package me.ash.reader.domain.model.general

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun keepsCustomSuffixInStringOutput() {
        assertEquals("0.16.1-custom.6", "0.16.1-custom.6".toVersion().toString())
    }

    @Test
    fun comparesCustomBuildNumbers() {
        assertTrue("0.16.1-custom.7".toVersion() > "0.16.1-custom.6".toVersion())
    }

    @Test
    fun treatsCustomBuildAsNewerThanBaseRelease() {
        assertTrue("0.16.1-custom.1".toVersion() > "0.16.1".toVersion())
    }

    @Test
    fun supportsGithubStyleVersionTags() {
        assertEquals("0.16.1-custom.6", "v0.16.1-custom.6".toVersion().toString())
    }
}
