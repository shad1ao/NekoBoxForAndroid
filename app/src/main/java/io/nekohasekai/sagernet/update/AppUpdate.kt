package io.nekohasekai.sagernet.update

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Public GitHub Releases lookup for NyaBox. No token is used. A newer
 * release is offered and can be dismissed; an old install is never blocked.
 */
object AppUpdate {
    const val LATEST_RELEASE_URL = "https://api.github.com/repos/shad1ao/NyaBox/releases/latest"
    const val RELEASES_URL = "https://api.github.com/repos/shad1ao/NyaBox/releases?per_page=20"
    const val PREVIEW_RELEASE_URL = "https://api.github.com/repos/shad1ao/NyaBox/releases/tags/preview"

    const val AUTO_CHECK_INTERVAL_MILLIS = 14L * 24 * 60 * 60 * 1000

    fun shouldCheck(nowMillis: Long, lastSuccessMillis: Long, manual: Boolean): Boolean {
        if (manual) return true
        if (lastSuccessMillis <= 0L) return true
        return nowMillis - lastSuccessMillis >= AUTO_CHECK_INTERVAL_MILLIS
    }

    /** Failures stay unstamped so a dead network does not wait out the interval. */
    fun rememberSuccessfulCheck(outcome: UpdateOutcome): Boolean = outcome !is UpdateOutcome.Failed

    fun parseRelease(json: String): GithubRelease {
        val element = JsonParser.parseString(json)
        if (!element.isJsonObject) throw IllegalArgumentException("release is not an object")
        return releaseFrom(element.asJsonObject)
    }

    fun parseReleaseList(json: String): List<GithubRelease> {
        val element = JsonParser.parseString(json)
        if (!element.isJsonArray) throw IllegalArgumentException("release list is not an array")
        return element.asJsonArray.mapNotNull { item ->
            if (!item.isJsonObject) null else releaseFrom(item.asJsonObject)
        }
    }

    fun selectStable(
        latestStatus: Int,
        latestBody: String,
        listStatus: Int?,
        listBody: String?,
    ): ReleaseSelection {
        if (latestStatus == 403 || latestStatus == 429) return ReleaseSelection.RateLimited
        if (latestStatus == 200) {
            val release = runCatching { parseRelease(latestBody) }.getOrNull()
            if (release != null && release.isStable) return ReleaseSelection.Found(release)
            if (release == null && listStatus == null) return ReleaseSelection.Unavailable
        }
        if (listStatus == null) return ReleaseSelection.Unavailable
        if (listStatus == 403 || listStatus == 429) return ReleaseSelection.RateLimited
        if (listStatus != 200) {
            return if (latestStatus == 404) ReleaseSelection.None else ReleaseSelection.Unavailable
        }
        val releases = runCatching { parseReleaseList(listBody.orEmpty()) }.getOrNull()
            ?: return ReleaseSelection.Unavailable
        val stable = releases.firstOrNull { it.isStable } ?: return ReleaseSelection.None
        return ReleaseSelection.Found(stable)
    }

    fun outcome(selection: ReleaseSelection, currentVersion: String, abi: String): UpdateOutcome {
        return when (selection) {
            is ReleaseSelection.Found -> {
                if (!isNewer(selection.release.version, currentVersion)) {
                    UpdateOutcome.UpToDate
                } else {
                    UpdateOutcome.Available(
                        currentVersion = currentVersion,
                        availableVersion = selection.release.version,
                        downloadUrl = downloadUrl(selection.release, abi),
                    )
                }
            }
            ReleaseSelection.None -> UpdateOutcome.UpToDate
            ReleaseSelection.RateLimited -> UpdateOutcome.Failed(FailureKind.RATE_LIMIT)
            ReleaseSelection.Unavailable -> UpdateOutcome.Failed(FailureKind.NETWORK)
        }
    }

    fun previewOutcome(status: Int, body: String, currentName: String, abi: String): UpdateOutcome {
        if (status == 403 || status == 429) return UpdateOutcome.Failed(FailureKind.RATE_LIMIT)
        if (status == 404) return UpdateOutcome.UpToDate
        if (status != 200) return UpdateOutcome.Failed(FailureKind.NETWORK)
        val release = runCatching { parseRelease(body) }.getOrNull()
            ?: return UpdateOutcome.Failed(FailureKind.NETWORK)
        if (release.version.isBlank() || release.version == currentName) return UpdateOutcome.UpToDate
        return UpdateOutcome.Available(
            currentVersion = currentName,
            availableVersion = release.version,
            downloadUrl = downloadUrl(release, abi),
        )
    }

    fun isNewer(releaseVersion: String, currentVersion: String): Boolean {
        return compareVersions(currentVersion, releaseVersion) < 0
    }

    fun compareVersions(left: String, right: String): Int {
        val a = versionParts(left)
        val b = versionParts(right)
        val size = maxOf(a.size, b.size)
        for (index in 0 until size) {
            val delta = a.getOrElse(index) { 0 } - b.getOrElse(index) { 0 }
            if (delta != 0) return delta
        }
        return 0
    }

    fun downloadUrl(release: GithubRelease, abi: String): String {
        val apks = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val match = apks.firstOrNull { abiInName(it.name, abi) }
        val assetUrl = match?.browserDownloadUrl
        if (assetUrl != null && assetUrl.startsWith("https://")) return assetUrl
        return release.htmlUrl
    }

    /** True when [abi] is a whole filename token, so x86 does not match x86_64. */
    fun abiInName(name: String, abi: String): Boolean {
        val index = name.indexOf(abi, ignoreCase = true)
        if (index < 0) return false
        val after = index + abi.length
        if (index > 0 && name[index - 1].isAbiChar()) return false
        if (after < name.length && name[after].isAbiChar()) return false
        return true
    }

    private fun Char.isAbiChar(): Boolean = isLetterOrDigit() || this == '_'

    private fun versionParts(raw: String): List<Int> {
        val cleaned = raw.trim().removePrefix("v").substringBefore('-').substringBefore('+')
        if (cleaned.isBlank()) return emptyList()
        return cleaned.split('.').map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
    }

    private fun releaseFrom(obj: JsonObject): GithubRelease {
        val tag = obj.str("tag_name")
        val name = obj.str("name")
        val version = tag.ifBlank { name }
        if (version.isBlank()) throw IllegalArgumentException("release has no version")
        val assetsElement = obj.get("assets")
        val assets = if (assetsElement != null && assetsElement.isJsonArray) {
            assetsFrom(assetsElement.asJsonArray)
        } else {
            emptyList()
        }
        return GithubRelease(
            version = version,
            htmlUrl = obj.str("html_url"),
            prerelease = obj.bool("prerelease"),
            draft = obj.bool("draft"),
            assets = assets,
        )
    }

    private fun assetsFrom(array: JsonArray): List<ReleaseAsset> {
        val assets = ArrayList<ReleaseAsset>(array.size())
        for (element in array) {
            if (!element.isJsonObject) continue
            val obj = element.asJsonObject
            val name = obj.str("name")
            val url = obj.str("browser_download_url")
            if (name.isBlank() || url.isBlank()) continue
            assets.add(ReleaseAsset(name, url))
        }
        return assets
    }

    private fun JsonObject.str(key: String): String {
        val element = get(key) ?: return ""
        if (element.isJsonNull || !element.isJsonPrimitive) return ""
        return element.asString
    }

    private fun JsonObject.bool(key: String): Boolean {
        val element = get(key) ?: return false
        if (element.isJsonNull || !element.isJsonPrimitive) return false
        return element.asBoolean
    }
}

data class GithubRelease(
    val version: String,
    val htmlUrl: String,
    val prerelease: Boolean,
    val draft: Boolean,
    val assets: List<ReleaseAsset>,
) {
    val isStable: Boolean get() = !draft && !prerelease
}

data class ReleaseAsset(
    val name: String,
    val browserDownloadUrl: String,
)

sealed class ReleaseSelection {
    data class Found(val release: GithubRelease) : ReleaseSelection()
    object None : ReleaseSelection()
    object RateLimited : ReleaseSelection()
    object Unavailable : ReleaseSelection()
}

sealed class UpdateOutcome {
    data class Available(
        val currentVersion: String,
        val availableVersion: String,
        val downloadUrl: String,
    ) : UpdateOutcome()

    object UpToDate : UpdateOutcome()
    data class Failed(val kind: FailureKind) : UpdateOutcome()
}

enum class FailureKind {
    RATE_LIMIT,
    NETWORK,
}
