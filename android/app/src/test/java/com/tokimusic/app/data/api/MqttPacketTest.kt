package com.tokimusic.app.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MQTT 5.0 最小子集编解码测试（QqMqttClient 的纯函数部分）：
 * 变长整数、字符串、CONNECT/SUBSCRIBE 构造、CONNACK/SUBACK/PUBLISH
 * 解析、帧缓冲、重定向路径、cookies 事件解析。全部离线，不碰网络。
 *
 * NOT covered here（需真机 + 真实服务端）：wss 握手是否接受子协议
 * mqtt、CONNACK 是否带 ServerReference、PUBLISH 的 type 属性与
 * payload 格式是否与 L-1124 一致。
 */
class MqttPacketTest {

    /** 定长头 + 剩余长度 + 载荷拼成完整报文。 */
    private fun packet(fixedHeader: Int, payload: ByteArray): ByteArray =
        byteArrayOf(fixedHeader.toByte()) + mqttVarIntBytes(payload.size) + payload

    private fun propsBytes(vararg entries: ByteArray): ByteArray {
        val body = entries.fold(byteArrayOf()) { acc, b -> acc + b }
        return mqttVarIntBytes(body.size) + body
    }

    private fun userProp(key: String, value: String): ByteArray =
        byteArrayOf(0x26) + mqttEncodeString(key) + mqttEncodeString(value)

    @Test
    fun varIntRoundTripAtBoundaries() {
        for (v in listOf(0, 127, 128, 16383, 16384, 2097151, 2097152, 268435455)) {
            val encoded = mqttVarIntBytes(v)
            assertEquals(v to encoded.size, mqttReadVarInt(encoded, 0))
        }
    }

    @Test
    fun varIntDecodeReturnsNullWhenIncomplete() {
        // 0x80 开头表示还有后续字节，但没有后续字节。
        assertNull(mqttReadVarInt(byteArrayOf(0x80.toByte()), 0))
        assertNull(mqttReadVarInt(byteArrayOf(), 0))
    }

    @Test
    fun stringRoundTripWithMultibyte() {
        val s = "management.qrcode_login/中文"
        val encoded = mqttEncodeString(s)
        assertEquals(s to encoded.size, mqttReadString(encoded, 0))
        assertNull(mqttReadString(byteArrayOf(0x00, 0x05, 0x61, 0x62), 0))
    }

    @Test
    fun connectBuildsMqtt5PacketWithAuthProperties() {
        val bytes = mqttBuildConnect(
            "17000000000001234",
            45,
            listOf("tmeAppID" to "qqmusic", "business" to "management", "hashTag" to "QR1", "clientTag" to "management.user", "userID" to "QR1"),
        )
        assertEquals(0x10, bytes[0].toInt() and 0xFF)
        val (remainingLength, varIntLen) = mqttReadVarInt(bytes, 1)!!
        assertEquals(bytes.size - 1 - varIntLen, remainingLength)
        var pos = 1 + varIntLen
        val (protocol, n1) = mqttReadString(bytes, pos)!!
        assertEquals("MQTT", protocol)
        pos += n1
        assertEquals(5, bytes[pos].toInt() and 0xFF) // 协议级别
        pos++
        assertEquals(0x02, bytes[pos].toInt() and 0xFF) // Clean Start
        pos++
        assertEquals(45, ((bytes[pos].toInt() and 0xFF) shl 8) or (bytes[pos + 1].toInt() and 0xFF))
        pos += 2
        val (props, next) = mqttParseProperties(bytes, pos)
        val authMethod = props.firstOrNull { it.id == 0x15 }?.value as? String
        assertEquals("pass", authMethod)
        val userProps = props.filter { it.id == 0x26 }.map { it.value as Pair<String, String> }.toMap()
        assertEquals("qqmusic", userProps["tmeAppID"])
        assertEquals("management", userProps["business"])
        assertEquals("QR1", userProps["hashTag"])
        assertEquals("management.user", userProps["clientTag"])
        assertEquals("QR1", userProps["userID"])
        pos = next
        val (clientId, _) = mqttReadString(bytes, pos)!!
        assertEquals("17000000000001234", clientId)
    }

    @Test
    fun connackParsesReasonAndServerProperties() {
        val props = propsBytes(
            byteArrayOf(0x13, 0x00, 0x2D), // Server Keep Alive = 45
            byteArrayOf(0x1C) + mqttEncodeString("mu2.y.qq.com:443"), // Server Reference
        )
        val payload = byteArrayOf(0x00, 0x00) + props
        val connack = mqttParseConnack(packet(0x20, payload))
        assertEquals(false, connack.sessionPresent)
        assertEquals(0x00, connack.reasonCode)
        assertEquals(45, connack.serverKeepAlive)
        assertEquals("mu2.y.qq.com:443", connack.serverReference)
    }

    @Test
    fun connackParsesRedirectReason() {
        val props = propsBytes(byteArrayOf(0x1C) + mqttEncodeString("mu3.y.qq.com:443"))
        val connack = mqttParseConnack(packet(0x20, byteArrayOf(0x00, 0x9D.toByte()) + props))
        assertEquals(0x9D, connack.reasonCode)
        assertEquals("mu3.y.qq.com:443", connack.serverReference)
    }

    @Test
    fun subscribeBuildsPacketWithTopicAndUserProperties() {
        val bytes = mqttBuildSubscribe(
            1,
            "management.qrcode_login/QR1",
            listOf("authorization" to "tmelogin", "pubsub" to "unicast"),
        )
        assertEquals(0x82, bytes[0].toInt() and 0xFF)
        val (_, varIntLen) = mqttReadVarInt(bytes, 1)!!
        var pos = 1 + varIntLen
        assertEquals(1, ((bytes[pos].toInt() and 0xFF) shl 8) or (bytes[pos + 1].toInt() and 0xFF))
        pos += 2
        val (props, next) = mqttParseProperties(bytes, pos)
        val userProps = props.filter { it.id == 0x26 }.map { it.value as Pair<String, String> }.toMap()
        assertEquals("tmelogin", userProps["authorization"])
        assertEquals("unicast", userProps["pubsub"])
        pos = next
        val (topic, n) = mqttReadString(bytes, pos)!!
        assertEquals("management.qrcode_login/QR1", topic)
        pos += n
        assertEquals(0x00, bytes[pos].toInt() and 0xFF) // 订阅选项 QoS 0
    }

    @Test
    fun subackParsesPacketIdAndReasonCodes() {
        val payload = byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x80.toByte())
        val suback = mqttParseSuback(packet(0x90, payload))
        assertEquals(1, suback.packetId)
        assertEquals(listOf(0x00, 0x80), suback.reasonCodes)
    }

    @Test
    fun publishParsesTopicUserPropertiesAndPayload() {
        val payloadJson = """{"cookies":{"qqmusic_uin":{"value":"123456"},"qqmusic_key":{"value":"KEY1"}}}"""
        val props = propsBytes(userProp("type", "cookies"))
        val payload = mqttEncodeString("management.qrcode_login/QR1") + props + payloadJson.toByteArray(Charsets.UTF_8)
        val pub = mqttParsePublish(packet(0x30, payload))
        assertEquals("management.qrcode_login/QR1", pub.topic)
        assertEquals("cookies", pub.userProperties["type"])
        assertEquals(payloadJson, String(pub.payload, Charsets.UTF_8))
    }

    @Test
    fun publishSkipsPacketIdWhenQosIsOne() {
        val props = propsBytes(userProp("type", "scanned"))
        val payload = mqttEncodeString("t") + byteArrayOf(0x00, 0x07) + props + "{}".toByteArray(Charsets.UTF_8)
        val pub = mqttParsePublish(packet(0x32, payload)) // 0x32 = PUBLISH QoS 1
        assertEquals("t", pub.topic)
        assertEquals("scanned", pub.userProperties["type"])
        assertEquals("{}", String(pub.payload, Charsets.UTF_8))
    }

    @Test
    fun frameBufferSplitsAcrossFeedsAndCoalescedFrames() {
        val p1 = packet(0x20, byteArrayOf(0x00, 0x00, 0x00))
        val p2 = packet(0x90, byteArrayOf(0x00, 0x01, 0x00, 0x00))
        val buf = MqttFrameBuffer()
        // 半个报文：什么都不出。
        assertTrue(buf.feed(p1.copyOfRange(0, 3)).isEmpty())
        // 补齐第一个报文的剩余部分 + 第二个报文一次性进来。
        val out = buf.feed(p1.copyOfRange(3, p1.size) + p2)
        assertEquals(2, out.size)
        assertTrue(out[0].contentEquals(p1))
        assertTrue(out[1].contentEquals(p2))
    }

    @Test
    fun frameBufferWaitsForIncompleteVarint() {
        // 剩余长度 300 需要两个变长字节；只给第一个字节时不出报文。
        val big = packet(0x30, ByteArray(300) { 0x61 })
        val buf = MqttFrameBuffer()
        assertTrue(buf.feed(big.copyOfRange(0, 2)).isEmpty())
        val out = buf.feed(big.copyOfRange(2, big.size))
        assertEquals(1, out.size)
        assertTrue(out[0].contentEquals(big))
    }

    @Test
    fun redirectPathReplacesHostPortSegmentOrAppends() {
        assertEquals("/ws/handshake/mu2.y.qq.com:443", mqttRedirectPath("/ws/handshake", "mu2.y.qq.com:443"))
        assertEquals("/ws/mu3.y.qq.com:443", mqttRedirectPath("/ws/mu2.y.qq.com:443", "mu3.y.qq.com:443"))
    }

    @Test
    fun parseMobileQrCookiesExtractsUinAndKey() {
        val payload = """{"cookies":{"qqmusic_uin":{"value":"123456"},"qqmusic_key":{"value":"KEY1"}}}""".toByteArray(Charsets.UTF_8)
        assertEquals("123456" to "KEY1", parseMobileQrCookies(payload))
    }

    @Test
    fun parseMobileQrCookiesReturnsNullWhenIncomplete() {
        assertNull(parseMobileQrCookies("""{"cookies":{"qqmusic_uin":{"value":"123456"}}}""".toByteArray(Charsets.UTF_8)))
        assertNull(parseMobileQrCookies("""{"cookies":{}}""".toByteArray(Charsets.UTF_8)))
        assertNull(parseMobileQrCookies("not json".toByteArray(Charsets.UTF_8)))
        assertNull(parseMobileQrCookies(ByteArray(0)))
    }

    @Test
    fun pingreqAndDisconnectAreTwoBytes() {
        assertTrue(mqttBuildPingreq().contentEquals(byteArrayOf(0xC0.toByte(), 0x00)))
        assertTrue(mqttBuildDisconnect().contentEquals(byteArrayOf(0xE0.toByte(), 0x00)))
    }
}
