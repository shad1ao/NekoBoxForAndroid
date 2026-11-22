/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.fmt.mieru

import com.google.common.net.InetAddresses
import moe.matsuri.nb4a.SingBoxOptions
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet6Address
import java.net.URI
import java.util.Base64

/**
 * Official mieru share links, from `mieru export config` / `mieru export config simple`.
 *
 * Spec and encoder: https://github.com/enfein/mieru/blob/v3.28.0/pkg/appctl/url.go
 * Human-readable description: https://github.com/enfein/mieru/blob/main/docs/client-install.md#sharing-client-settings
 * mihomo splits one simple link into one proxy per port binding:
 * https://github.com/MetaCubeX/mihomo/pull/2657
 *
 * Supported import:
 * - mierus://username:password@host?profile=&port=&protocol=...  (simple, one link per server)
 * - mieru://BASE64  (standard, protobuf ClientConfig from mieru v3)
 *
 * Export uses mierus:// only. A full mieru:// link is a client config (socks port, RPC port,
 * logging) which a proxy profile does not have. Each NyaBox profile is one port binding,
 * because mbox's mieru outbound has a single transport for every port.
 */
fun parseMieruLinks(link: String): List<MieruBean> {
    val text = link.trim()
    return when {
        text.startsWith("mierus://") -> parseMierus(text)
        text.startsWith("mieru://") -> parseMieruStandard(text)
        else -> throw IllegalArgumentException("mieru link: unsupported scheme")
    }
}

fun MieruBean.toMierusLink(): String {
    initializeDefaultValues()
    if (serverAddress.isBlank()) throw IllegalArgumentException("mieru link: server is empty")
    if (username.isBlank()) throw IllegalArgumentException("mieru link: username is empty")
    if (password.isBlank()) throw IllegalArgumentException("mieru link: password is empty")
    // Official share links require `profile`. An empty stored profile follows the
    // display name; "default" is only the fallback when that name is empty too.
    val profile = profileName.trim().ifBlank { name.trim() }.ifBlank { "default" }
    val portValue = serverPorts.ifBlank { serverPort?.toString().orEmpty() }
    if (portValue.isBlank()) throw IllegalArgumentException("mieru link: port is empty")
    validatePortToken(portValue)
    validateProtocol(protocol)
    validateOptionalFields(mtu, multiplexing, handshakeMode, trafficPattern)

    val query = mutableListOf<Pair<String, String>>()
    if (handshakeMode.isNotBlank() && handshakeMode != "HANDSHAKE_DEFAULT") {
        query += "handshake-mode" to handshakeMode
    }
    if (mtu != null) query += "mtu" to mtu.toString()
    if (multiplexing.isNotBlank() && multiplexing != "MULTIPLEXING_DEFAULT") {
        query += "multiplexing" to multiplexing
    }
    query += "port" to portValue
    query += "profile" to profile
    query += "protocol" to protocol
    if (trafficPattern.isNotBlank()) query += "traffic-pattern" to trafficPattern

    val user = goEscape(username, EscapeMode.USER) + ":" + goEscape(password, EscapeMode.USER)
    val host = decorateIPv6(serverAddress)
    val url = "mierus://$user@$host?${encodeGoQuery(query)}"
    return if (name.isNotBlank() && name != profile) {
        url + "#" + goEscape(name, EscapeMode.FRAGMENT)
    } else {
        url
    }
}

/**
 * Editor rules for the Mieru settings screen.
 *
 * mita stores `users.name` and `users.password` as independent fields
 * (https://github.com/enfein/mieru/blob/v3.28.0/docs/server-install.zh_CN.md).
 * The client profile does the same for `user.name` and `user.password`
 * (https://github.com/enfein/mieru/blob/v3.28.0/docs/client-install.zh_CN.md).
 * They are copied together only when the user asks, or when the stored values
 * are already equal. Saving the settings screen stores the display name as
 * the share-link `profile`. An imported bean keeps its stored profile until
 * that save, so an unedited config still exports the official value.
 */
internal object MieruEditor {
    fun passwordMatchesUsername(username: String, password: String): Boolean = username == password

    fun passwordToStore(username: String, password: String, sameAsUsername: Boolean): String =
        if (sameAsUsername) username else password

    fun profileFromDisplayName(displayName: String): String = displayName.trim()
}

fun buildSingBoxOutboundMieruBean(bean: MieruBean): SingBoxOptions.Outbound_MieruOptions {
    bean.initializeDefaultValues()
    if (bean.protocol == "UDP" && !bean.serverAddress.isIpAddress()) {
        throw IllegalArgumentException("mbox mieru UDP requires an IP server address")
    }
    return SingBoxOptions.Outbound_MieruOptions().apply {
        type = "mieru"
        server = bean.serverAddress
        if (bean.serverPorts.isNotBlank()) {
            server_ports = listOf(bean.serverPorts)
        } else {
            server_port = bean.serverPort
        }
        transport = bean.protocol
        username = bean.username
        password = bean.password
        if (bean.multiplexing.isNotBlank() && bean.multiplexing != "MULTIPLEXING_DEFAULT") {
            multiplexing = bean.multiplexing
        }
        if (bean.trafficPattern.isNotBlank()) traffic_pattern = bean.trafficPattern
        if (bean.mtu != null) mtu = bean.mtu
        if (bean.handshakeMode.isNotBlank() && bean.handshakeMode != "HANDSHAKE_DEFAULT") {
            handshake_mode = bean.handshakeMode
        }
    }
}

internal fun parseClashMieru(proxy: Map<String, Any?>): MieruBean {
    val server = proxy["server"]?.toString().orEmpty()
    val port = proxy["port"]?.toString()?.takeIf { it.isNotBlank() && it != "null" && it != "0" }
    val portRange = (proxy["port-range"] ?: proxy["port_range"])?.toString()
        ?.takeIf { it.isNotBlank() && it != "null" }
    if (port != null && portRange != null) {
        throw IllegalArgumentException("mieru proxy: port and port-range cannot both be set")
    }
    return buildBean(
        server = server,
        portToken = portRange ?: port.orEmpty(),
        protocol = proxy["transport"]?.toString() ?: "",
        username = proxy["username"]?.toString().orEmpty(),
        password = proxy["password"]?.toString().orEmpty(),
        profile = proxy["name"]?.toString().orEmpty().ifBlank { "default" },
        name = proxy["name"]?.toString().orEmpty(),
        mtu = proxy["mtu"]?.toString(),
        multiplexing = proxy["multiplexing"]?.toString(),
        handshake = (proxy["handshake-mode"] ?: proxy["handshake_mode"])?.toString(),
        trafficPattern = (proxy["traffic-pattern"] ?: proxy["traffic_pattern"])?.toString(),
    )
}

internal fun parseSingBoxMieruOutbound(outbound: JSONObject): List<MieruBean> {
    val server = outbound.optString("server")
    val username = outbound.optString("username")
    val password = outbound.optString("password")
    val protocol = outbound.optString("transport")
    val name = outbound.optString("tag")
    val mtu = if (outbound.has("mtu")) outbound.opt("mtu")?.toString() else null
    val multiplexing = outbound.optString("multiplexing").ifBlank { null }
    val handshake = outbound.optString("handshake_mode").ifBlank { null }
    val traffic = outbound.optString("traffic_pattern").ifBlank { null }
    val ports = mutableListOf<String>()
    if (outbound.has("server_port") && !outbound.isNull("server_port")) {
        ports += outbound.opt("server_port")?.toString().orEmpty()
    }
    val ranges = outbound.opt("server_ports")
    when (ranges) {
        is JSONArray -> {
            for (i in 0 until ranges.length()) ports += ranges.opt(i)?.toString().orEmpty()
        }

        is String -> if (ranges.isNotBlank()) ports += ranges
    }
    if (ports.isEmpty()) throw IllegalArgumentException("mieru outbound: port is empty")
    return ports.map { portToken ->
        buildBean(
            server = server,
            portToken = portToken,
            protocol = protocol,
            username = username,
            password = password,
            profile = name.ifBlank { "default" },
            name = name,
            mtu = mtu,
            multiplexing = multiplexing,
            handshake = handshake,
            trafficPattern = traffic,
        )
    }
}

private fun parseMierus(text: String): List<MieruBean> {
    val uri = try {
        URI(text)
    } catch (e: Exception) {
        throw IllegalArgumentException("mieru link: invalid URL")
    }
    if (uri.scheme != "mierus") throw IllegalArgumentException("mieru link: unsupported scheme")
    if (uri.rawUserInfo.isNullOrEmpty()) throw IllegalArgumentException("mieru link: missing user info")
    val userInfo = try {
        uri.userInfo ?: throw IllegalArgumentException("mieru link: missing user info")
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: Exception) {
        throw IllegalArgumentException("mieru link: invalid user info")
    }
    val colon = userInfo.indexOf(':')
    if (colon <= 0 || colon == userInfo.lastIndex) {
        throw IllegalArgumentException("mieru link: username and password are required")
    }
    val username = userInfo.substring(0, colon)
    val password = userInfo.substring(colon + 1)
    if (username.isEmpty() || password.isEmpty()) {
        throw IllegalArgumentException("mieru link: username and password are required")
    }
    if (uri.port != -1) {
        throw IllegalArgumentException("mieru link: put ports in the port query, not the host")
    }
    val server = uri.host?.trim().orEmpty()
    if (server.isEmpty()) throw IllegalArgumentException("mieru link: server is empty")

    val query = parseGoQuery(uri.rawQuery)
    fun once(key: String): String? {
        val values = query[key] ?: return null
        if (values.size != 1) throw IllegalArgumentException("mieru link: $key must appear once")
        return values[0]
    }

    val profile = once("profile")
    if (profile.isNullOrBlank()) throw IllegalArgumentException("mieru link: profile is required")
    val ports = query["port"] ?: emptyList()
    val protocols = query["protocol"] ?: emptyList()
    if (ports.isEmpty()) throw IllegalArgumentException("mieru link: port is required")
    if (ports.size != protocols.size) {
        throw IllegalArgumentException("mieru link: port and protocol counts differ")
    }
    val mtu = once("mtu")
    val multiplexing = once("multiplexing")
    val handshake = once("handshake-mode")
    val traffic = once("traffic-pattern")
    val fragment = uri.fragment?.takeIf { it.isNotBlank() }
    val baseName = fragment ?: profile

    return ports.mapIndexed { index, portToken ->
        val protocol = protocols[index]
        val single = ports.size == 1
        buildBean(
            server = server,
            portToken = portToken,
            protocol = protocol,
            username = username,
            password = password,
            profile = profile,
            name = if (single) baseName else "$baseName:$portToken/$protocol",
            mtu = mtu,
            multiplexing = multiplexing,
            handshake = handshake,
            trafficPattern = traffic,
        )
    }
}

private fun parseMieruStandard(text: String): List<MieruBean> {
    val payload = text.removePrefix("mieru://").trim()
    if (payload.isEmpty() || payload.contains('?') || payload.contains('#')) {
        throw IllegalArgumentException("mieru:// link: expected standard base64, not a simple link")
    }
    val bytes = try {
        Base64.getDecoder().decode(payload)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("mieru:// link: invalid base64")
    }
    val config = ProtoReader(bytes).readFields()
    val profiles = config.filter { it.field == 1 && it.wire == 2 }
    if (profiles.isEmpty()) throw IllegalArgumentException("mieru:// link: no profiles")
    val active = config.firstOrNull { it.field == 2 && it.wire == 2 }?.bytes?.toString(Charsets.UTF_8)
    val selected = profiles.map { parseProfile(it.bytes) }.let { all ->
        if (active.isNullOrEmpty()) all else all.filter { it.profile == active }.ifEmpty {
            throw IllegalArgumentException("mieru:// link: active profile was not found")
        }
    }
    val beans = selected.flatMap { it.toBeans() }
    if (beans.isEmpty()) throw IllegalArgumentException("mieru:// link: no servers")
    return beans
}

private data class ParsedProfile(
    val profile: String,
    val username: String,
    val password: String,
    val mtu: String?,
    val multiplexing: String?,
    val handshake: String?,
    val trafficPattern: String?,
    val servers: List<ParsedServer>,
)

private data class ParsedServer(
    val address: String,
    val bindings: List<Pair<String, String>>,
)

private fun parseProfile(bytes: ByteArray): ParsedProfile {
    val fields = ProtoReader(bytes).readFields()
    val profile = fields.firstOrNull { it.field == 1 && it.wire == 2 }?.bytes?.toString(Charsets.UTF_8).orEmpty()
    if (profile.isBlank()) throw IllegalArgumentException("mieru:// link: profile name is empty")
    val user = fields.firstOrNull { it.field == 2 && it.wire == 2 }?.bytes
        ?: throw IllegalArgumentException("mieru:// link: user is missing")
    val userFields = ProtoReader(user).readFields()
    val username = userFields.firstOrNull { it.field == 1 && it.wire == 2 }?.bytes?.toString(Charsets.UTF_8).orEmpty()
    val password = userFields.firstOrNull { it.field == 2 && it.wire == 2 }?.bytes?.toString(Charsets.UTF_8).orEmpty()
    if (username.isEmpty() || password.isEmpty()) {
        throw IllegalArgumentException("mieru:// link: username and password are required")
    }
    val servers = fields.filter { it.field == 3 && it.wire == 2 }.map { parseServer(it.bytes) }
    if (servers.isEmpty()) throw IllegalArgumentException("mieru:// link: server is missing")
    val mtu = fields.firstOrNull { it.field == 4 && it.wire == 0 }?.varint?.toString()
    val mux = fields.firstOrNull { it.field == 5 && it.wire == 2 }?.bytes?.let { muxBytes ->
        val level = ProtoReader(muxBytes).readFields().firstOrNull { it.field == 1 && it.wire == 0 }?.varint
        multiplexingName(level)
    }
    val handshake = fields.firstOrNull { it.field == 6 && it.wire == 0 }?.varint?.let { handshakeName(it) }
    val traffic = fields.firstOrNull { it.field == 7 && it.wire == 2 }?.bytes?.let {
        Base64.getEncoder().encodeToString(it)
    }
    return ParsedProfile(profile, username, password, mtu, mux, handshake, traffic, servers)
}

private fun parseServer(bytes: ByteArray): ParsedServer {
    val fields = ProtoReader(bytes).readFields()
    val ip = fields.firstOrNull { it.field == 1 && it.wire == 2 }?.bytes?.toString(Charsets.UTF_8).orEmpty()
    val domain = fields.firstOrNull { it.field == 2 && it.wire == 2 }?.bytes?.toString(Charsets.UTF_8).orEmpty()
    val address = domain.ifBlank { ip }
    if (address.isBlank()) throw IllegalArgumentException("mieru:// link: server address is empty")
    val bindings = fields.filter { it.field == 3 && it.wire == 2 }.map { binding ->
        val parts = ProtoReader(binding.bytes).readFields()
        val port = parts.firstOrNull { it.field == 1 && it.wire == 0 }?.varint
        val protocol = parts.firstOrNull { it.field == 2 && it.wire == 0 }?.varint
        val range = parts.firstOrNull { it.field == 3 && it.wire == 2 }?.bytes?.toString(Charsets.UTF_8).orEmpty()
        val token = when {
            range.isNotBlank() && port != null -> throw IllegalArgumentException("mieru:// link: port binding has both port and port range")
            range.isNotBlank() -> range
            port != null -> port.toString()
            else -> throw IllegalArgumentException("mieru:// link: port binding is empty")
        }
        token to protocolName(protocol)
    }
    if (bindings.isEmpty()) throw IllegalArgumentException("mieru:// link: port binding is empty")
    return ParsedServer(address, bindings)
}

private fun ParsedProfile.toBeans(): List<MieruBean> {
    return servers.flatMap { server ->
        val multiple = servers.size > 1 || server.bindings.size > 1
        server.bindings.map { (portToken, protocol) ->
            buildBean(
                server = server.address,
                portToken = portToken,
                protocol = protocol,
                username = username,
                password = password,
                profile = profile,
                name = if (multiple) "$profile:$portToken/$protocol" else profile,
                mtu = mtu,
                multiplexing = multiplexing,
                handshake = handshake,
                trafficPattern = trafficPattern,
            )
        }
    }
}

private fun buildBean(
    server: String,
    portToken: String,
    protocol: String,
    username: String,
    password: String,
    profile: String,
    name: String,
    mtu: String?,
    multiplexing: String?,
    handshake: String?,
    trafficPattern: String?,
): MieruBean {
    if (server.isBlank()) throw IllegalArgumentException("mieru link: server is empty")
    if (username.isBlank() || password.isBlank()) {
        throw IllegalArgumentException("mieru link: username and password are required")
    }
    validateProtocol(protocol)
    validatePortToken(portToken)
    validateOptionalFields(
        mtu = mtu?.toIntOrNull().also {
            if (!mtu.isNullOrBlank() && it == null) throw IllegalArgumentException("mieru link: invalid mtu")
        },
        multiplexing = multiplexing,
        handshake = handshake,
        trafficPattern = trafficPattern,
    )
    return MieruBean().apply {
        serverAddress = if (server.startsWith("[") && server.endsWith("]")) {
            server.substring(1, server.length - 1)
        } else {
            server
        }
        if (portToken.contains('-')) {
            serverPorts = portToken
            serverPort = portToken.substringBefore('-').toInt()
        } else {
            serverPorts = ""
            serverPort = portToken.toInt()
        }
        this.protocol = protocol
        this.username = username
        this.password = password
        this.profileName = profile
        this.name = name
        this.mtu = mtu?.toInt()
        this.multiplexing = multiplexing.orEmpty()
        this.handshakeMode = handshake.orEmpty()
        this.trafficPattern = trafficPattern.orEmpty()
        initializeDefaultValues()
    }
}

private fun validateProtocol(protocol: String) {
    if (protocol != "TCP" && protocol != "UDP") {
        throw IllegalArgumentException("mieru link: protocol must be TCP or UDP")
    }
}

private fun validatePortToken(token: String) {
    val parts = if (token.contains('-')) token.split('-') else listOf(token)
    if (parts.size > 2 || parts.any { it.isEmpty() }) {
        throw IllegalArgumentException("mieru link: invalid port")
    }
    val numbers = parts.map { part ->
        part.toIntOrNull() ?: throw IllegalArgumentException("mieru link: invalid port")
    }
    if (numbers.any { it !in 1..65535 }) throw IllegalArgumentException("mieru link: port out of range")
    if (numbers.size == 2 && numbers[0] > numbers[1]) {
        throw IllegalArgumentException("mieru link: port range is reversed")
    }
}

private val MULTIPLEXING = setOf(
    "MULTIPLEXING_DEFAULT",
    "MULTIPLEXING_OFF",
    "MULTIPLEXING_LOW",
    "MULTIPLEXING_MIDDLE",
    "MULTIPLEXING_HIGH",
)

private val HANDSHAKE = setOf(
    "HANDSHAKE_DEFAULT",
    "HANDSHAKE_STANDARD",
    "HANDSHAKE_NO_WAIT",
)

private fun validateOptionalFields(
    mtu: Int?,
    multiplexing: String?,
    handshake: String?,
    trafficPattern: String?,
) {
    if (mtu != null && mtu !in 1280..1400) {
        throw IllegalArgumentException("mieru link: mtu must be between 1280 and 1400")
    }
    if (!multiplexing.isNullOrBlank() && multiplexing !in MULTIPLEXING) {
        throw IllegalArgumentException("mieru link: unknown multiplexing level")
    }
    if (!handshake.isNullOrBlank() && handshake !in HANDSHAKE) {
        throw IllegalArgumentException("mieru link: unknown handshake mode")
    }
    if (!trafficPattern.isNullOrBlank()) {
        try {
            val decoded = Base64.getDecoder().decode(trafficPattern)
            if (decoded.isEmpty()) throw IllegalArgumentException("mieru link: empty traffic pattern")
        } catch (e: IllegalArgumentException) {
            if (e.message?.startsWith("mieru link:") == true) throw e
            throw IllegalArgumentException("mieru link: invalid traffic pattern")
        }
    }
}

private fun protocolName(value: Long?): String = when (value) {
    1L -> "UDP"
    2L -> "TCP"
    else -> throw IllegalArgumentException("mieru:// link: unknown transport")
}

private fun multiplexingName(value: Long?): String? = when (value) {
    null -> null
    0L -> "MULTIPLEXING_DEFAULT"
    1L -> "MULTIPLEXING_OFF"
    2L -> "MULTIPLEXING_LOW"
    3L -> "MULTIPLEXING_MIDDLE"
    4L -> "MULTIPLEXING_HIGH"
    else -> throw IllegalArgumentException("mieru:// link: unknown multiplexing level")
}

private fun handshakeName(value: Long): String = when (value) {
    0L -> "HANDSHAKE_DEFAULT"
    1L -> "HANDSHAKE_STANDARD"
    2L -> "HANDSHAKE_NO_WAIT"
    else -> throw IllegalArgumentException("mieru:// link: unknown handshake mode")
}

private fun decorateIPv6(address: String): String {
    if (address.startsWith("[") && address.endsWith("]")) return address
    if (InetAddresses.isInetAddress(address) && InetAddresses.forString(address) is Inet6Address) {
        return "[$address]"
    }
    return address
}

private fun String.isIpAddress(): Boolean {
    val host = trim().removePrefix("[").removeSuffix("]")
    return InetAddresses.isInetAddress(host)
}

private enum class EscapeMode { USER, QUERY, FRAGMENT }

private fun goEscape(value: String, mode: EscapeMode): String {
    val bytes = value.toByteArray(Charsets.UTF_8)
    val out = StringBuilder(bytes.size)
    for (raw in bytes) {
        val c = raw.toInt() and 0xFF
        if (c == ' '.code && mode == EscapeMode.QUERY) {
            out.append('+')
        } else if (shouldEscape(c, mode)) {
            out.append('%')
            out.append(HEX[c ushr 4])
            out.append(HEX[c and 0x0F])
        } else {
            out.append(c.toChar())
        }
    }
    return out.toString()
}

private fun shouldEscape(c: Int, mode: EscapeMode): Boolean {
    if (c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code) return false
    if (c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code) return false
    if (c == '$'.code || c == '&'.code || c == '+'.code || c == ','.code || c == '/'.code ||
        c == ':'.code || c == ';'.code || c == '='.code || c == '?'.code || c == '@'.code
    ) {
        return when (mode) {
            EscapeMode.USER -> c == '@'.code || c == '/'.code || c == '?'.code || c == ':'.code
            EscapeMode.QUERY -> true
            EscapeMode.FRAGMENT -> false
        }
    }
    if (mode == EscapeMode.FRAGMENT && (c == '!'.code || c == '('.code || c == ')'.code || c == '*'.code)) {
        return false
    }
    return true
}

private const val HEX = "0123456789ABCDEF"

private fun encodeGoQuery(pairs: List<Pair<String, String>>): String {
    val grouped = linkedMapOf<String, MutableList<String>>()
    for ((key, value) in pairs) grouped.getOrPut(key) { mutableListOf() }.add(value)
    return grouped.keys.sorted().flatMap { key ->
        grouped.getValue(key).map { value ->
            goEscape(key, EscapeMode.QUERY) + "=" + goEscape(value, EscapeMode.QUERY)
        }
    }.joinToString("&")
}

private fun parseGoQuery(raw: String?): Map<String, List<String>> {
    if (raw.isNullOrEmpty()) return emptyMap()
    val grouped = linkedMapOf<String, MutableList<String>>()
    for (part in raw.split('&')) {
        if (part.isEmpty()) continue
        val eq = part.indexOf('=')
        val key = queryUnescape(if (eq < 0) part else part.substring(0, eq))
        val value = queryUnescape(if (eq < 0) "" else part.substring(eq + 1))
        grouped.getOrPut(key) { mutableListOf() }.add(value)
    }
    return grouped
}

private fun queryUnescape(value: String): String {
    val plus = value.replace("+", " ")
    val bytes = ArrayList<Byte>(plus.length)
    var i = 0
    while (i < plus.length) {
        val c = plus[i]
        if (c == '%') {
            if (i + 2 >= plus.length) throw IllegalArgumentException("mieru link: invalid percent-encoding")
            val hex = plus.substring(i + 1, i + 3)
            val byte = hex.toIntOrNull(16) ?: throw IllegalArgumentException("mieru link: invalid percent-encoding")
            bytes.add(byte.toByte())
            i += 3
        } else {
            val encoded = c.toString().toByteArray(Charsets.UTF_8)
            encoded.forEach { bytes.add(it) }
            i++
        }
    }
    return bytes.toByteArray().toString(Charsets.UTF_8)
}

private class ProtoField(val field: Int, val wire: Int, val varint: Long = 0, val bytes: ByteArray = ByteArray(0))

private class ProtoReader(private val data: ByteArray) {
    private var index = 0

    fun readFields(): List<ProtoField> {
        val fields = mutableListOf<ProtoField>()
        while (index < data.size) {
            val key = readVarint()
            val field = (key ushr 3).toInt()
            val wire = (key and 0x7).toInt()
            if (field <= 0) throw IllegalArgumentException("mieru:// link: invalid protobuf field")
            when (wire) {
                0 -> fields += ProtoField(field, wire, varint = readVarint())
                1 -> skip(8)
                2 -> fields += ProtoField(field, wire, bytes = readBytes())
                5 -> skip(4)
                else -> throw IllegalArgumentException("mieru:// link: unsupported protobuf wire type")
            }
        }
        return fields
    }

    private fun skip(count: Int) {
        if (index + count > data.size) throw IllegalArgumentException("mieru:// link: truncated protobuf")
        index += count
    }

    private fun readBytes(): ByteArray {
        val length = readVarint().toInt()
        if (length < 0 || index + length > data.size) {
            throw IllegalArgumentException("mieru:// link: truncated protobuf")
        }
        val out = data.copyOfRange(index, index + length)
        index += length
        return out
    }

    private fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (index >= data.size) throw IllegalArgumentException("mieru:// link: truncated protobuf")
            val byte = data[index++].toInt() and 0xFF
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
            if (shift > 63) throw IllegalArgumentException("mieru:// link: varint too long")
        }
    }
}
