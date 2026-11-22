package io.nekohasekai.sagernet.fmt.trojan

import io.nekohasekai.sagernet.fmt.v2ray.parseDuckSoft
import io.nekohasekai.sagernet.ktx.linkQueryParameter
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun parseTrojan(server: String): TrojanBean {

    val link = server.replace("trojan://", "https://").toHttpUrlOrNull()
        ?: error("invalid trojan link $server")

    return TrojanBean().apply {
        parseDuckSoft(link)
        link.linkQueryParameter("allowInsecure")
            ?.apply { if (this == "1" || this == "true") allowInsecure = true }
        link.linkQueryParameter("peer")?.apply { if (this.isNotBlank()) sni = this }
    }

}
