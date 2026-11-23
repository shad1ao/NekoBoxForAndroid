package io.nekohasekai.sagernet.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {

    @Test
    fun realPrereleaseListIsNotAStableRelease() {
        val selection = AppUpdate.selectStable(
            404,
            fixture("github/nyabox-latest-404.json"),
            200,
            fixture("github/nyabox-releases.json"),
        )
        assertTrue(selection is ReleaseSelection.None)
        assertTrue(AppUpdate.outcome(selection, "1.4.3.3", "arm64-v8a") is UpdateOutcome.UpToDate)
    }

    @Test
    fun realAssetsParseAndX86DoesNotMatchX86_64() {
        val releases = AppUpdate.parseReleaseList(fixture("github/nyabox-releases.json"))
        assertEquals("1.4.3.3", releases[0].version)
        assertTrue(releases.all { it.prerelease && !it.draft })
        assertEquals(
            "https://github.com/shad1ao/NyaBox/releases/download/1.4.3.3/NyaBox-1.4.3.3-x86_64.apk",
            AppUpdate.downloadUrl(releases[0], "x86_64"),
        )
        assertEquals(
            "https://github.com/shad1ao/NyaBox/releases/download/1.4.3.3/NyaBox-1.4.3.3-x86.apk",
            AppUpdate.downloadUrl(releases[0], "x86"),
        )
        assertFalse(AppUpdate.abiInName("NyaBox-1.4.3.3-x86_64.apk", "x86"))
        assertTrue(AppUpdate.abiInName("NyaBox-1.4.3.3-x86.apk", "x86"))
        assertTrue(AppUpdate.abiInName("NyaBox-1.4.3.3-arm64-v8a.apk", "arm64-v8a"))
        assertTrue(AppUpdate.abiInName("NyaBox-1.4.3.3-armeabi-v7a.apk", "armeabi-v7a"))
    }

    @Test
    fun stableReleaseIsDiscoverableAndDoesNotForceOldVersions() {
        val body = fixture("github/nyabox-1.4.4-stable.json")
        val selection = AppUpdate.selectStable(200, body, null, null)
        val found = selection as ReleaseSelection.Found
        assertEquals("1.4.4", found.release.version)
        assertFalse(found.release.prerelease)
        assertFalse(found.release.draft)

        val fromPatch = AppUpdate.outcome(selection, "1.4.3.3", "arm64-v8a") as UpdateOutcome.Available
        assertEquals("1.4.3.3", fromPatch.currentVersion)
        assertEquals("1.4.4", fromPatch.availableVersion)
        assertEquals(
            "https://github.com/shad1ao/NyaBox/releases/download/1.4.4/NyaBox-1.4.4-arm64-v8a.apk",
            fromPatch.downloadUrl,
        )

        val fromOld = AppUpdate.outcome(selection, "1.0.0", "armeabi-v7a") as UpdateOutcome.Available
        assertTrue(fromOld.downloadUrl.endsWith("NyaBox-1.4.4-armeabi-v7a.apk"))
        assertTrue(AppUpdate.outcome(selection, "1.4.4", "x86") is UpdateOutcome.UpToDate)

        val fromList = AppUpdate.selectStable(
            404,
            fixture("github/nyabox-latest-404.json"),
            200,
            fixture("github/nyabox-releases-with-stable.json"),
        ) as ReleaseSelection.Found
        assertEquals("1.4.4", fromList.release.version)
        assertTrue(fromList.release.isStable)
    }

    @Test
    fun versionCompareDoesNotUseSubstring() {
        assertTrue(AppUpdate.isNewer("1.4.3.3", "1.4.3"))
        assertFalse(AppUpdate.isNewer("1.4.3", "1.4.3.3"))
        assertTrue(AppUpdate.isNewer("1.4.10", "1.4.4"))
        assertFalse(AppUpdate.isNewer("1.4.4", "1.4.10"))
        assertFalse(AppUpdate.isNewer("1.4.4", "1.4.4"))
        assertEquals(0, AppUpdate.compareVersions("1.4.4", "v1.4.4.0"))
    }

    @Test
    fun rateLimitAndNetworkFailuresStayOptional() {
        val limited = AppUpdate.selectStable(403, """{"message":"API rate limit exceeded"}""", null, null)
        assertTrue(limited is ReleaseSelection.RateLimited)
        val limitedOutcome = AppUpdate.outcome(limited, "1.4.4", "arm64-v8a") as UpdateOutcome.Failed
        assertEquals(FailureKind.RATE_LIMIT, limitedOutcome.kind)

        val tooMany = AppUpdate.selectStable(429, "HTTP 429", null, null)
        assertTrue(tooMany is ReleaseSelection.RateLimited)

        val malformed = AppUpdate.selectStable(200, "not-json", null, null)
        assertTrue(malformed is ReleaseSelection.Unavailable)
        assertEquals(
            FailureKind.NETWORK,
            (AppUpdate.outcome(malformed, "1.4.3.3", "arm64-v8a") as UpdateOutcome.Failed).kind,
        )

        assertTrue(AppUpdate.previewOutcome(404, fixture("github/nyabox-latest-404.json"), "pre-1.4.3", "arm64-v8a") is UpdateOutcome.UpToDate)
        assertEquals(
            FailureKind.RATE_LIMIT,
            (AppUpdate.previewOutcome(403, "rate", "pre-1.4.3", "x86") as UpdateOutcome.Failed).kind,
        )

        assertFalse(AppUpdate.rememberSuccessfulCheck(UpdateOutcome.Failed(FailureKind.NETWORK)))
        assertFalse(AppUpdate.rememberSuccessfulCheck(UpdateOutcome.Failed(FailureKind.RATE_LIMIT)))
        assertTrue(AppUpdate.rememberSuccessfulCheck(UpdateOutcome.UpToDate))
    }

    @Test
    fun automaticCheckWaitsFourteenDaysAndManualDoesNot() {
        val interval = AppUpdate.AUTO_CHECK_INTERVAL_MILLIS
        assertEquals(14L * 24 * 60 * 60 * 1000, interval)
        val last = 1_700_000_000_000L
        assertTrue(AppUpdate.shouldCheck(last, 0L, manual = false))
        assertFalse(AppUpdate.shouldCheck(last + interval - 1, last, manual = false))
        assertTrue(AppUpdate.shouldCheck(last + interval, last, manual = false))
        assertTrue(AppUpdate.shouldCheck(last + interval + 1, last, manual = false))
        assertTrue(AppUpdate.shouldCheck(last, last, manual = true))
        assertTrue(AppUpdate.shouldCheck(last + 1, last, manual = true))
    }

    @Test
    fun publicReleaseUrlsCarryNoToken() {
        for (url in listOf(AppUpdate.LATEST_RELEASE_URL, AppUpdate.RELEASES_URL, AppUpdate.PREVIEW_RELEASE_URL)) {
            assertTrue(url.startsWith("https://api.github.com/repos/shad1ao/NyaBox/"))
            assertFalse(url.contains("token", ignoreCase = true))
            assertFalse(url.contains("authorization", ignoreCase = true))
        }
    }

    private fun fixture(name: String): String {
        return javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().use { it.readText() }
    }
}
