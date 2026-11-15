package pro.joaosousa.queuedisplay.tv.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuildInfoTest {

    @Test
    fun `label includes build type for non-release builds`() {
        val info = BuildInfo(versionName = "0.1.0", versionCode = 7, buildType = "debug")

        assertEquals("0.1.0 (7) debug", info.label)
    }

    @Test
    fun `label omits build type for release builds`() {
        val info = BuildInfo(versionName = "1.2.3", versionCode = 42, buildType = "release")

        assertEquals("1.2.3 (42)", info.label)
    }
}
