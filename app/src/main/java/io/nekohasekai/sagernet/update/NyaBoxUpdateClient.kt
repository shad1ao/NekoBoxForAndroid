package io.nekohasekai.sagernet.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import libcore.Libcore
import moe.matsuri.nb4a.utils.Util

object NyaBoxUpdateClient {
    private val httpStatus = Regex("""HTTP (\d{3})""")
    private val knownAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    fun primaryAbi(): String {
        val supported = Build.SUPPORTED_ABIS ?: emptyArray()
        return supported.firstOrNull { it in knownAbis } ?: "arm64-v8a"
    }

    fun checkStable(currentVersion: String, abi: String): UpdateOutcome {
        val latest = get(AppUpdate.LATEST_RELEASE_URL) ?: return UpdateOutcome.Failed(FailureKind.NETWORK)
        if (latest.status == 403 || latest.status == 429) {
            return UpdateOutcome.Failed(FailureKind.RATE_LIMIT)
        }
        val latestIsStable = latest.status == 200 && runCatching {
            AppUpdate.parseRelease(latest.body).isStable
        }.getOrDefault(false)
        val list = if (latestIsStable) {
            null
        } else {
            get(AppUpdate.RELEASES_URL) ?: return UpdateOutcome.Failed(FailureKind.NETWORK)
        }
        val selection = AppUpdate.selectStable(latest.status, latest.body, list?.status, list?.body)
        return AppUpdate.outcome(selection, currentVersion, abi)
    }

    fun checkPreview(currentName: String, abi: String): UpdateOutcome {
        val reply = get(AppUpdate.PREVIEW_RELEASE_URL) ?: return UpdateOutcome.Failed(FailureKind.NETWORK)
        return AppUpdate.previewOutcome(reply.status, reply.body, currentName, abi)
    }

    private fun get(url: String): HttpReply? {
        val client = Libcore.newHttpClient().apply {
            modernTLS()
            trySocks5(DataStore.mixedPort)
        }
        return try {
            val response = client.newRequest().apply {
                setURL(url)
                setUserAgent("NyaBox")
                setHeader("Accept", "application/vnd.github+json")
            }.execute()
            HttpReply(200, Util.getStringBox(response.contentString))
        } catch (e: Exception) {
            val status = httpStatus.find(e.message.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (status == null) {
                Logs.w("update check request failed", e)
                null
            } else {
                HttpReply(status, e.message.orEmpty())
            }
        } finally {
            runCatching { client.close() }
        }
    }

    private data class HttpReply(val status: Int, val body: String)
}

object AppUpdatePrompt {
    fun show(context: Context, outcome: UpdateOutcome, manual: Boolean) {
        when (outcome) {
            is UpdateOutcome.Available -> {
                if (context is Activity && (context.isFinishing || context.isDestroyed)) return
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.update_dialog_title)
                    .setMessage(
                        context.getString(
                            R.string.update_dialog_message,
                            outcome.currentVersion,
                            outcome.availableVersion,
                        )
                    )
                    .setPositiveButton(R.string.yes) { _, _ ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, outcome.downloadUrl.toUri()))
                    }
                    .setNegativeButton(R.string.no, null)
                    .setCancelable(true)
                    .show()
            }
            UpdateOutcome.UpToDate -> {
                if (manual) {
                    Toast.makeText(context, R.string.check_update_no, Toast.LENGTH_SHORT).show()
                }
            }
            is UpdateOutcome.Failed -> {
                if (manual) {
                    Toast.makeText(context, R.string.check_update_unavailable, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
