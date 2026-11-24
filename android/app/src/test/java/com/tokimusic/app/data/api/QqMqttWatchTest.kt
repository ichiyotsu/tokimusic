package com.tokimusic.app.data.api

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Collections

/**
 * QQ音乐 App 扫码的 MQTT 会话集成测试：MockWebServer 扮演 mu.y.qq.com
 *（wss 握手 + CONNECT/CONNACK + SUBSCRIBE/SUBACK + PUBLISH 事件）
 *与 Login CGI，验证 [QqMqttClient.watchMobileQrLogin] 整条循环离线可走通。
 *
 * NOT covered here（需真机 + 真实服务端）：真实 mu.y.qq.com 是否接受
 * 该握手、PUBLISH 的 type 属性与 payload 格式是否与 L-1124 一致。
 */
class QqMqttWatchTest {
    private lateinit var server: MockWebServer
    private lateinit var settings: FakeSettings
    private lateinit var qqClient: QqClient
    private lateinit var mqttClient: QqMqttClient
    private var wsListener: ScriptedWsListener? = null

    private class FakeSettings : QqSettings {
        override var qqMusicId: String = ""
        override var qqMusicKey: String = ""
        override var qqDeviceId: String = "0123456789abcdef0123456789abcdef"
        override var qqDeviceId2: String = "fedcba9876543210fedcba9876543210"
        override var qqAndroidId: String = "a1b2c3d4e5f6a1b2"
        override var qqFirstLoginMs: Long = 0L
        override var neteaseEnabled: Boolean = true
        override var qqEnabled: Boolean = false
    }

    /** 服务端按剧本回包的 WebSocket 监听器：CONNECT→CONNACK，SUBSCRIBE→SUBACK+剧本。 */
    private class ScriptedWsListener(
        private val afterSubscribe: (WebSocket) -> Unit,
    ) : WebSocketListener() {
        val connectPackets = Collections.synchronizedList(mutableListOf<ByteArray>())
        val subscribePackets = Collections.synchronizedList(mutableListOf<ByteArray>())
        @Volatile
        var serverSocket: WebSocket? = null

        override fun onOpen(webSocket: WebSocket, response: Response) {
            serverSocket = webSocket
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {

            val packet = bytes.toByteArray()
            // 按包类型位匹配：SUBSCRIBE 定长头是 0x82（含保留标志位），类型位是 0x80。
            when (packet[0].toInt() and 0xF0) {
                0x10 -> {
                    connectPackets += packet
                    // CONNACK：sessionPresent 0，reason 0，属性长度 0。
                    webSocket.send(byteArrayOf(0x20, 0x03, 0x00, 0x00, 0x00).toByteString()) // 剩余长度 3 = 会话标志 + 原因码 + 属性长度
                }
                0x80 -> {
                    subscribePackets += packet
                    // SUBACK：packet id 1，属性长度 0，reason 0x00。
                    webSocket.send(byteArrayOf(0x90.toByte(), 0x04, 0x00, 0x01, 0x00, 0x00).toByteString())
                    afterSubscribe(webSocket)
                }
            }
        }
    }

    private fun publishPacket(type: String, payloadJson: String): ByteArray {
        val propsBody = byteArrayOf(0x26) + mqttEncodeString("type") + mqttEncodeString(type)
        val props = mqttVarIntBytes(propsBody.size) + propsBody
        val body = mqttEncodeString("management.qrcode_login/QR1") + props + payloadJson.toByteArray(Charsets.UTF_8)
        return byteArrayOf(0x30) + mqttVarIntBytes(body.size) + body
    }

    private fun loginCgiResponse(): MockResponse =
        MockResponse().setBody("""{"code":0,"req_0":{"code":0,"data":{"musicid":987654,"musickey":"KEY987","str_musicid":"987654"}}}""")
            .addHeader("Content-Type", "application/json")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        settings = FakeSettings()
        val base = server.url("/").toString().trimEnd('/')
        qqClient = QqClient(settings, QqHosts(base, base, base, base, base))
        val wsBase = base.replace("http://", "ws://")
        mqttClient = QqMqttClient(qqClient, wsBase)
    }

    @After
    fun tearDown() {
        // 先关服务端 WebSocket，否则 MockWebServer.shutdown 会因未关闭的
        // 连接一直等下去（"Gave up waiting for queue to shut down"）。
        runCatching { wsListener?.serverSocket?.close(1000, null) }
        server.shutdown()
    }

    private fun installWsDispatcher(afterSubscribe: (WebSocket) -> Unit): ScriptedWsListener {
        val listener = ScriptedWsListener(afterSubscribe)
        wsListener = listener
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when {
                    request.path!!.startsWith("/ws/handshake") ->
                        MockResponse().withWebSocketUpgrade(listener)
                    request.path!!.startsWith("/cgi-bin/musicu.fcg") -> loginCgiResponse()
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        return listener
    }

    @Test
    fun watchReturnsCredentialOnCookiesEvent() = runBlocking {
        val listener = installWsDispatcher { ws ->
            ws.send(publishPacket("scanned", "{}").toByteString())
            ws.send(
                publishPacket(
                    "cookies",
                    """{"cookies":{"qqmusic_uin":{"value":"987654"},"qqmusic_key":{"value":"KEY987"}}}""",
                ).toByteString(),
            )
        }
        val events = Collections.synchronizedList(mutableListOf<QqMobileQrEvent>())
        val credential = withTimeout(15_000) {
            mqttClient.watchMobileQrLogin("QR1", timeoutMs = 10_000) { events += it }
        }
        assertEquals("987654", credential?.musicId)
        assertEquals("KEY987", credential?.musicKey)
        assertEquals("987654", settings.qqMusicId)
        assertEquals("KEY987", settings.qqMusicKey)
        assertEquals(listOf(QqMobileQrEvent.Waiting, QqMobileQrEvent.Scanned, QqMobileQrEvent.Done), events)
        // CONNECT 带了 L-1124 的鉴权属性（hashTag/userID = qrcodeID）。
        val connect = listener.connectPackets.single()
        val (_, varIntLen) = mqttReadVarInt(connect, 1)!!
        var pos = 1 + varIntLen
        val (_, n1) = mqttReadString(connect, pos)!!
        pos += n1 + 4 // 协议级别(1) + 连接标志(1) + keep alive(2)
        val (props, _) = mqttParseProperties(connect, pos)
        val userProps = props.filter { it.id == 0x26 }.map { it.value as Pair<String, String> }.toMap()
        assertEquals("qqmusic", userProps["tmeAppID"])
        assertEquals("QR1", userProps["hashTag"])
        assertEquals("QR1", userProps["userID"])
        // SUBSCRIBE 的主题与属性。
        val subscribe = listener.subscribePackets.single()
        val (_, v2) = mqttReadVarInt(subscribe, 1)!!
        val topicPos = 1 + v2 + 2 // packet id
        val (subProps, next) = mqttParseProperties(subscribe, topicPos)
        val subUserProps = subProps.filter { it.id == 0x26 }.map { it.value as Pair<String, String> }.toMap()
        assertEquals("tmelogin", subUserProps["authorization"])
        assertEquals("unicast", subUserProps["pubsub"])
        val (topic, _) = mqttReadString(subscribe, next)!!
        assertEquals("management.qrcode_login/QR1", topic)
    }

    @Test
    fun watchReturnsNullOnCanceled() = runBlocking {
        installWsDispatcher { ws -> ws.send(publishPacket("canceled", "{}").toByteString()) }
        val events = Collections.synchronizedList(mutableListOf<QqMobileQrEvent>())
        val credential = withTimeout(15_000) {
            mqttClient.watchMobileQrLogin("QR1", timeoutMs = 10_000) { events += it }
        }
        assertNull(credential)
        assertEquals(listOf(QqMobileQrEvent.Waiting, QqMobileQrEvent.Refused), events)
    }

    @Test
    fun watchReturnsNullOnDeadlineTimeout() = runBlocking {
        installWsDispatcher { _ -> /* 不发任何事件，等 deadline */ }
        val events = Collections.synchronizedList(mutableListOf<QqMobileQrEvent>())
        val credential = withTimeout(15_000) {
            mqttClient.watchMobileQrLogin("QR1", timeoutMs = 1_500) { events += it }
        }
        assertNull(credential)
        assertEquals(listOf(QqMobileQrEvent.Waiting, QqMobileQrEvent.Timeout), events)
    }
}
