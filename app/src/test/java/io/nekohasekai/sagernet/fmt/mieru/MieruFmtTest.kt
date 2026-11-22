package io.nekohasekai.sagernet.fmt.mieru

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MieruFmtTest {

    @Test
    fun officialSimpleLinkSplitsEveryPortBinding() {
        val beans = parseMieruLinks(OFFICIAL_SIMPLE)
        assertEquals(4, beans.size)
        assertEquals(listOf("6666", "9998-9999", "6489", "4896"), beans.map { it.portToken() })
        assertEquals(listOf("TCP", "TCP", "UDP", "UDP"), beans.map { it.protocol })
        beans.forEach { bean ->
            assertEquals("1.2.3.4", bean.serverAddress)
            assertEquals("baozi", bean.username)
            assertEquals("manlianpenfen", bean.password)
            assertEquals("default", bean.profileName)
            assertEquals(1400, bean.mtu)
            assertEquals("MULTIPLEXING_HIGH", bean.multiplexing)
            assertEquals("HANDSHAKE_NO_WAIT", bean.handshakeMode)
            assertEquals(OFFICIAL_TRAFFIC, bean.trafficPattern)
        }
        assertEquals("default:6666/TCP", beans[0].name)
        assertEquals("default:9998-9999/TCP", beans[1].name)
        assertEquals(9998, beans[1].serverPort)
        assertEquals("9998-9999", beans[1].serverPorts)
    }

    @Test
    fun simpleLinkRoundTripPreservesFields() {
        val original = parseMieruLinks(OFFICIAL_SIMPLE)
        original.forEach { bean ->
            val again = parseMieruLinks(bean.toMierusLink()).single()
            assertEquals(bean.serverAddress, again.serverAddress)
            assertEquals(bean.serverPort, again.serverPort)
            assertEquals(bean.serverPorts, again.serverPorts)
            assertEquals(bean.protocol, again.protocol)
            assertEquals(bean.username, again.username)
            assertEquals(bean.password, again.password)
            assertEquals(bean.profileName, again.profileName)
            assertEquals(bean.name, again.name)
            assertEquals(bean.mtu, again.mtu)
            assertEquals(bean.multiplexing, again.multiplexing)
            assertEquals(bean.handshakeMode, again.handshakeMode)
            assertEquals(bean.trafficPattern, again.trafficPattern)
        }
    }

    @Test
    fun encodesUserInfoQueryAndIpv6LikeGo() {
        val bean = MieruBean().apply {
            name = "my node"
            profileName = "def ault"
            serverAddress = "2001:db8::1"
            serverPort = 6666
            serverPorts = ""
            protocol = "TCP"
            username = "user name"
            password = "p@ss:word/测试"
            mtu = 1400
            multiplexing = "MULTIPLEXING_HIGH"
            handshakeMode = "HANDSHAKE_NO_WAIT"
            trafficPattern = "CCoQARoECAEQCiIYCAMQASoIMDAwMTAyMDMqCDA0MDUwNjA3"
            initializeDefaultValues()
        }
        assertEquals(
            "mierus://user%20name:p%40ss%3Aword%2F%E6%B5%8B%E8%AF%95@[2001:db8::1]" +
                "?handshake-mode=HANDSHAKE_NO_WAIT&mtu=1400&multiplexing=MULTIPLEXING_HIGH" +
                "&port=6666&profile=def%20ault&protocol=TCP" +
                "&traffic-pattern=CCoQARoECAEQCiIYCAMQASoIMDAwMTAyMDMqCDA0MDUwNjA3" +
                "#my%20node",
            bean.toMierusLink(),
        )
        val parsed = parseMieruLinks(bean.toMierusLink()).single()
        assertEquals("user name", parsed.username)
        assertEquals("p@ss:word/测试", parsed.password)
        assertEquals("2001:db8::1", parsed.serverAddress)
        assertEquals("def ault", parsed.profileName)
        assertEquals("my node", parsed.name)
        assertEquals("CCoQARoECAEQCiIYCAMQASoIMDAwMTAyMDMqCDA0MDUwNjA3", parsed.trafficPattern)
        assertFalse(bean.toMierusLink().contains("profile=def+ault"))
        assertFalse(bean.toMierusLink().contains("%2520"))
    }

    @Test
    fun querySpacesArePercentEncodedAndPlusStaysPlus() {
        val bean = MieruBean().apply {
            name = "节点 A+B"
            profileName = "配置 名+称"
            serverAddress = "example.com"
            serverPort = 443
            protocol = "TCP"
            username = "名 字"
            password = "密 码+plus"
            initializeDefaultValues()
        }
        val link = bean.toMierusLink()
        val profile = link.substringAfter("profile=").substringBefore("&").substringBefore("#")
        assertFalse(profile.contains("+"))
        assertTrue(profile.contains("%20"))
        assertTrue(profile.contains("%2B"))
        assertTrue(link.substringAfter("mierus://").substringBefore("@").contains("%20"))
        assertTrue(link.contains("#"))
        assertTrue(link.substringAfter("#").contains("%20"))
        assertFalse(link.contains("%2520"))
        assertFalse(link.contains("%252B"))
        val parsed = parseMieruLinks(link).single()
        assertEquals("配置 名+称", parsed.profileName)
        assertEquals("节点 A+B", parsed.name)
        assertEquals("名 字", parsed.username)
        assertEquals("密 码+plus", parsed.password)
    }

    @Test
    fun queryPlusStaysPlusAndPercent20IsTheOnlySpace() {
        val rawPlus = "mierus://user:pass@example.com?port=443&profile=def+ault&protocol=TCP"
        val spacePct = "mierus://user:pass@example.com?port=443&profile=def%20ault&protocol=TCP"
        val literalPlus = "mierus://user:pass@example.com?port=443&profile=def%2Bault&protocol=TCP"
        assertEquals("def+ault", parseMieruLinks(rawPlus).single().profileName)
        assertEquals("def ault", parseMieruLinks(spacePct).single().profileName)
        assertEquals("def+ault", parseMieruLinks(literalPlus).single().profileName)

        val exported = MieruBean().apply {
            name = "def+ault"
            profileName = "def+ault"
            serverAddress = "example.com"
            serverPort = 443
            protocol = "TCP"
            username = "user"
            password = "pass"
            initializeDefaultValues()
        }.toMierusLink()
        assertTrue(exported.contains("profile=def%2Bault"))
        assertFalse(exported.contains("profile=def+ault"))
        assertEquals("def+ault", parseMieruLinks(exported).single().profileName)
    }

    @Test
    fun trafficPatternRoundTripEscapesBase64Alphabet() {
        val bean = MieruBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            protocol = "TCP"
            username = "user"
            password = "a+b c"
            profileName = "default"
            name = "default"
            trafficPattern = "YStiLw=="
            initializeDefaultValues()
        }
        val link = bean.toMierusLink()
        assertTrue(link.contains("traffic-pattern=YStiLw%3D%3D"))
        assertTrue(link.contains("user:a+b%20c@"))
        val parsed = parseMieruLinks(link).single()
        assertEquals("YStiLw==", parsed.trafficPattern)
        assertEquals("a+b c", parsed.password)
    }

    @Test
    fun passwordPlusIsNotASpace() {
        val link = "mierus://user:a+b%20c@[::1]?port=443&profile=default&protocol=UDP"
        val bean = parseMieruLinks(link).single()
        assertEquals("a+b c", bean.password)
        assertEquals("::1", bean.serverAddress)
        assertEquals("UDP", bean.protocol)
        assertNull(bean.mtu)
        assertEquals("", bean.multiplexing)
    }

    @Test
    fun omitsDefaultMultiplexingAndHandshakeOnExport() {
        val bean = MieruBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            protocol = "TCP"
            username = "user"
            password = "pass"
            profileName = "simple"
            name = "simple"
            multiplexing = "MULTIPLEXING_DEFAULT"
            handshakeMode = "HANDSHAKE_DEFAULT"
            initializeDefaultValues()
        }
        assertEquals(
            "mierus://user:pass@example.com?port=443&profile=simple&protocol=TCP",
            bean.toMierusLink(),
        )
    }

    @Test
    fun standardProtobufLink() {
        val bean = parseMieruLinks(STANDARD_TCP).single()
        assertEquals("default", bean.profileName)
        assertEquals("default", bean.name)
        assertEquals("1.2.3.4", bean.serverAddress)
        assertEquals(6666, bean.serverPort)
        assertEquals("", bean.serverPorts)
        assertEquals("TCP", bean.protocol)
        assertEquals("baozi", bean.username)
        assertEquals("manlianpenfen", bean.password)
        assertEquals(1400, bean.mtu)
        assertEquals("MULTIPLEXING_HIGH", bean.multiplexing)
        assertEquals("HANDSHAKE_NO_WAIT", bean.handshakeMode)
    }

    @Test
    fun standardProtobufDomainUdpRangeAndEncodedUser() {
        val bean = parseMieruLinks(STANDARD_DOMAIN_UDP).single()
        assertEquals("home", bean.profileName)
        assertEquals("example.com", bean.serverAddress)
        assertEquals("2000-2002", bean.serverPorts)
        assertEquals(2000, bean.serverPort)
        assertEquals("UDP", bean.protocol)
        assertEquals("u name", bean.username)
        assertEquals("p:a@b", bean.password)
        assertNull(bean.mtu)
    }

    @Test
    fun rejectsMalformedLinksWithoutEchoingSecrets() {
        val samples = listOf(
            "mierus://user@example.com?port=1&protocol=TCP&profile=default",
            "mierus://:secret@example.com?port=1&protocol=TCP&profile=default",
            "mierus://user:secret@example.com?protocol=TCP&profile=default",
            "mierus://user:secret@example.com?port=1&port=2&protocol=TCP&profile=default",
            "mierus://user:secret@example.com?port=70000&protocol=TCP&profile=default",
            "mierus://user:secret@example.com?port=9-1&protocol=TCP&profile=default",
            "mierus://user:secret@example.com?port=1&protocol=tcp&profile=default",
            "mierus://user:secret@example.com?port=1&protocol=TCP",
            "mierus://user:secret@example.com?port=1&protocol=TCP&profile=default&mtu=100",
            "mierus://user:secret@example.com?port=1&protocol=TCP&profile=default&multiplexing=HIGH",
            "mierus://user:secret@example.com?port=1&protocol=TCP&profile=default&traffic-pattern=%%%%",
            "mierus://user:secret@example.com:443?port=1&protocol=TCP&profile=default",
            "mierus://user:secret@example.com?port=1&protocol=TCP&profile=a&profile=b",
            "mieru://not base64!!!",
            "mieru://",
            "socks://127.0.0.1:1080",
        )
        samples.forEach { link ->
            try {
                parseMieruLinks(link)
                fail("accepted malformed link")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.startsWith("mieru") == true)
                assertTrue("message leaked a secret: ${e.message}", e.message?.contains("secret") != true)
            }
        }
    }

    @Test
    fun identicalCredentialsStayEqualAndDistinctCredentialsStayDistinct() {
        val same = credentialBean(username = "user-a", password = "user-a", profile = "home", name = "home")
        val sameLink = same.toMierusLink()
        assertEquals(
            "mierus://user-a:user-a@example.com?port=443&profile=home&protocol=TCP",
            sameLink,
        )
        val sameParsed = parseMieruLinks(sameLink).single()
        assertEquals("user-a", sameParsed.username)
        assertEquals("user-a", sameParsed.password)
        assertEquals("home", sameParsed.profileName)
        assertEquals("home", sameParsed.name)

        val distinct = credentialBean(
            username = "user-a",
            password = "other-value",
            profile = "default",
            name = "node",
        )
        val distinctLink = distinct.toMierusLink()
        assertTrue(distinctLink.endsWith("#node"))
        val distinctParsed = parseMieruLinks(distinctLink).single()
        assertEquals("user-a", distinctParsed.username)
        assertEquals("other-value", distinctParsed.password)
        assertEquals("default", distinctParsed.profileName)
        assertEquals("node", distinctParsed.name)
    }

    @Test
    fun emptyProfileExportsDisplayNameWithoutDroppingADistinctPassword() {
        val bean = credentialBean(
            username = "user-a",
            password = "other-value",
            profile = "",
            name = "home",
        )
        assertEquals(
            "mierus://user-a:other-value@example.com?port=443&profile=home&protocol=TCP",
            bean.toMierusLink(),
        )
        val parsed = parseMieruLinks(bean.toMierusLink()).single()
        assertEquals("other-value", parsed.password)
        assertEquals("home", parsed.profileName)
        assertEquals("home", parsed.name)
    }

    @Test
    fun editorStoresPasswordWithoutCopyingUsername() {
        val distinct = credentialBean(
            username = "user-a",
            password = "other-value",
            profile = "home",
            name = "home",
        )
        distinct.password = MieruEditor.passwordToStore(distinct.password)
        assertEquals("user-a", distinct.username)
        assertEquals("other-value", distinct.password)
        assertEquals(
            "mierus://user-a:other-value@example.com?port=443&profile=home&protocol=TCP",
            distinct.toMierusLink(),
        )

        val same = credentialBean(
            username = "user-a",
            password = "user-a",
            profile = "home",
            name = "home",
        )
        same.password = MieruEditor.passwordToStore(same.password)
        assertEquals("user-a", same.password)
    }

    @Test
    fun editorSaveUsesDisplayNameAsMieruProfile() {
        assertEquals("home", MieruEditor.profileFromDisplayName(" home "))
        assertEquals("", MieruEditor.profileFromDisplayName("   "))
        val edited = credentialBean(
            username = "user-a",
            password = "other-value",
            profile = "default",
            name = "node",
        )
        edited.profileName = MieruEditor.profileFromDisplayName(edited.name)
        assertEquals(
            "mierus://user-a:other-value@example.com?port=443&profile=node&protocol=TCP",
            edited.toMierusLink(),
        )
        assertEquals("other-value", edited.password)
    }

    private fun credentialBean(
        username: String,
        password: String,
        profile: String,
        name: String,
    ) = MieruBean().apply {
        serverAddress = "example.com"
        serverPort = 443
        protocol = "TCP"
        this.username = username
        this.password = password
        profileName = profile
        this.name = name
        initializeDefaultValues()
    }

    private fun MieruBean.portToken(): String = serverPorts.ifBlank { serverPort.toString() }

    companion object {
        private const val OFFICIAL_TRAFFIC =
            "CCoQARoECAEQCiIYCAMQASoIMDAwMTAyMDMqCDA0MDUwNjA3"
        private const val OFFICIAL_SIMPLE =
            "mierus://baozi:manlianpenfen@1.2.3.4?handshake-mode=HANDSHAKE_NO_WAIT&mtu=1400" +
                "&multiplexing=MULTIPLEXING_HIGH&port=6666&port=9998-9999&port=6489&port=4896" +
                "&profile=default&protocol=TCP&protocol=TCP&protocol=UDP&protocol=UDP" +
                "&traffic-pattern=$OFFICIAL_TRAFFIC"

        // Built from mieru v3 ClientConfig field numbers. Verified against
        // github.com/enfein/mieru/v3/pkg/appctl/appctlpb in the libcore test.
        private const val STANDARD_TCP =
            "mieru://CjwKB2RlZmF1bHQSFgoFYmFvemkSDW1hbmxpYW5wZW5mZW4aEAoHMS4yLjMuNBoFCIo0EAIg+AoqAggEMAISB2RlZmF1bHQ="
        private const val STANDARD_DOMAIN_UDP =
            "mieru://CjUKBGhvbWUSDwoGdSBuYW1lEgVwOmFAYhocEgtleGFtcGxlLmNvbRoNGgkyMDAwLTIwMDIQAQ=="
    }
}
