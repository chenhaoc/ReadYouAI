package me.ash.reader.domain.service

import org.junit.Assert.assertTrue
import org.junit.Test
import me.ash.reader.BuildConfig

class AppServiceTest {

    @Test
    fun updateLinkMatchesCurrentFlavor() {
        val expectedRepository =
            if (BuildConfig.FLAVOR == "githubAi") "chenhaoc/ReadYouAI"
            else "ReadYouApp/ReadYou"

        assertTrue(BuildConfig.UPDATE_LINK.contains(expectedRepository))
    }
}
