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
    fun checksumAndMirrorAddress() {
        val sha = "ab".repeat(32)
        val json = """{"tag_name":"v1.0.40","body":"x","assets":[{"name":"kazhdyidnevnik.apk","size":7000000,""" +
            """"digest":"sha256:$sha","browser_download_url":"https://github.com/Cmix7777/kazhdyidnevnik/releases/download/v1.0.40/kazhdyidnevnik.apk"}]}"""
        val release = requireNotNull(ReleaseParser.fromApi(json))
        assertEquals(sha, release.sha256)
        assertEquals("https://cdn.jsdelivr.net/gh/Cmix7777/kazhdyidnevnik@apk-v1.0.40/kazhdyidnevnik.apk", release.mirrorUrl)
        // Непонятная сумма не считается суммой: файл тогда проверяется только по размеру.
        assertNull(ReleaseParser.fromApi(json.replace("sha256:$sha", "md5:123"))!!.sha256)
        assertNull(ReleaseParser.fromApi(json.replace(sha, "abc"))!!.sha256)
    }

    @Test
    fun rejectsStrangeAnswers() {
        assertNull(ReleaseParser.fromApi("""{"message":"API rate limit exceeded"}"""))
        assertNull(ReleaseParser.fromApi("не json"))
        assertNull(ReleaseParser.fromTagUrl("https://github.com/Cmix7777/kazhdyidnevnik/releases"))
        assertNull(ReleaseParser.versionCode("latest"))
    }
}
