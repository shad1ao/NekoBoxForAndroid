package io.nekohasekai.sagernet.ktx

import moe.matsuri.nb4a.utils.NGUtil
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkQueryTest {

    @Test
    fun queryPlusIsNotASpace() {
        val url = "https://example.com/?path=/a+b%20c&name=%E8%8A%82%E7%82%B9+A&plus=%2B".toHttpUrl()
        assertEquals("/a+b c", url.linkQueryParameter("path"))
        assertEquals("节点+A", url.linkQueryParameter("name"))
        assertEquals("+", url.linkQueryParameter("plus"))
        assertNull(url.linkQueryParameter("missing"))
    }

    @Test
    fun percentDecodeKeepsPlusAndDecodesSpaceOnce() {
        assertEquals("a+b c", NGUtil.percentDecode("a+b%20c"))
        assertEquals("a+b", NGUtil.percentDecode("a%2Bb"))
        assertEquals("a+b", NGUtil.urlDecode("a%2Bb"))
        assertEquals("a b", NGUtil.urlDecode("a%20b"))
        assertEquals("a+b", NGUtil.urlDecode("a+b"))
        assertEquals("节点 A", NGUtil.percentDecode("%E8%8A%82%E7%82%B9%20A"))
        assertEquals("%zz", NGUtil.percentDecode("%zz"))
        assertEquals("a%20b", NGUtil.percentDecode("a%2520b"))
    }
}
