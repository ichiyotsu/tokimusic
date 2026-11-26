package com.tokimusic.app.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import kotlin.random.Random

/** 0.1.20 第 12 项：QIMEI 注册的纯函数部分（md5/AES 已知向量、beacon 结构、IMEI、响应解析）。 */
class QqQimeiTest {

    @Test
    fun md5KnownVector() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", QqQimei.md5Hex("abc"))
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", QqQimei.md5Hex(""))
    }

    @Test
    fun aesCbcKnownVector() {
        // AES-128 全零 key/iv 加密全零块的标准向量（首块）。
        val out = Base64.getDecoder().decode(
            QqQimei.aesCbcBase64(ByteArray(16), ByteArray(16), ByteArray(16)),
        )
        val firstBlock = out.copyOf(16).joinToString("") { "%02x".format(it) }
        assertEquals("66e94bd4ef8a2c3b884cfa59ca342b2e", firstBlock)
    }

    @Test
    fun deviceTokenIsDeterministic() {
        assertEquals(QqQimei.deviceToken("abc123"), QqQimei.deviceToken("abc123"))
    }

    @Test
    fun beaconIdHas40SegmentsAndFixedK3() {
        val beacon = QqQimei.beaconIdSrc(1762000000000L, Random(42))
        val segments = beacon.split(";").filter { it.isNotEmpty() }
        assertEquals(40, segments.size)
        assertTrue(segments[0].startsWith("k1:"))
        assertEquals("k3:0000000000000000", segments[2])
        assertTrue(segments[3].startsWith("k4:"))
    }

    @Test
    fun imeiIs15DigitsWithValidLuhn() {
        val imei = QqQimei.generateImei(Random(7))
        assertEquals(15, imei.length)
        assertTrue(imei.all { it.isDigit() })
        var sum = 0
        imei.forEachIndexed { i, ch ->
            var d = ch.digitToInt()
            if (i % 2 == 1) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        assertEquals(0, sum % 10)
    }

    @Test
    fun parseResponseHandlesNestedShapes() {
        // 探针实测形状：外层 data 是 JSON 字符串，内层 data 再套一层。
        val nested = """{"code":0,"data":"{\"code\":0,\"data\":{\"q16\":\"aaa\",\"q36\":\"bbb\"}}"}"""
        assertEquals("aaa" to "bbb", QqQimei.parseResponse(nested))
        val stringInner = """{"data":"{\"data\":\"{\\\"q16\\\":\\\"x\\\",\\\"q36\\\":\\\"y\\\"}\"}"}"""
        assertEquals("x" to "y", QqQimei.parseResponse(stringInner))
        assertNull(QqQimei.parseResponse("""{"code":1}"""))
        assertNull(QqQimei.parseResponse("not json"))
        assertNotNull(QqQimei.parseResponse(nested))
    }
}
