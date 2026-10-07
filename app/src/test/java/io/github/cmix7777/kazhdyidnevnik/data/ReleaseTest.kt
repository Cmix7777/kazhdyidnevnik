package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseTest {

    @Test
    fun readsLatestReleaseFromApi() {
        val json = requireNotNull(javaClass.getResource("/github/latest.json")).readText()
        val release = requireNotNull(ReleaseParser.fromApi(json))
        assertEquals(12, release.versionCode)
        assertEquals("1.0.12", release.versionName)
        assertEquals("Погода в утренней сводке, обновление из приложения", release.notes)
        assertEquals(21_500_267L, release.sizeBytes)
        assertEquals(
            "https://github.com/Cmix7777/kazhdyidnevnik/releases/download/v1.0.12/kazhdyidnevnik.apk",
            release.apkUrl,
        )
    }

    @Test
    fun readsVersionFromRedirect() {
        val release = requireNotNull(
            ReleaseParser.fromTagUrl("https://github.com/Cmix7777/kazhdyidnevnik/releases/tag/v1.0.9"),
        )
        assertEquals(9, release.versionCode)
        assertEquals("1.0.9", release.versionName)
        assertEquals(
            "https://github.com/Cmix7777/kazhdyidnevnik/releases/download/v1.0.9/kazhdyidnevnik.apk",
            release.apkUrl,
        )
    }

    @Test
    fun rejectsStrangeAnswers() {
        assertNull(ReleaseParser.fromApi("""{"message":"API rate limit exceeded"}"""))
        assertNull(ReleaseParser.fromApi("не json"))
        assertNull(ReleaseParser.fromTagUrl("https://github.com/Cmix7777/kazhdyidnevnik/releases"))
        assertNull(ReleaseParser.versionCode("latest"))
    }
}
