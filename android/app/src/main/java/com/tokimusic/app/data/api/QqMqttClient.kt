package com.tokimusic.app.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** QQ音乐 App 扫码登录的事件（L-1124 QRCodeLoginEvents 的 MOBILE 子集）。 */
sealed interface QqMobileQrEvent {
    /** 已订阅，等待扫码。 */
    data object Waiting : QqMobileQrEvent
    /** 已扫码，等待在 QQ音乐 App 内确认。 */
    data object Scanned : QqMobileQrEvent
    /** 用户在 QQ音乐 App 内拒绝。 */
    data object Refused : QqMobileQrEvent
    /** 二维码过期 / 等待超时。 */
    data object Timeout : QqMobileQrEvent
    /** 登录完成（凭证已换取并落库）。 */
    data object Done : QqMobileQrEvent
}

// ---- MQTT 5.0 packet codec (pure functions; unit-tested in MqttPacketTest) ----

/** MQTT 变长整数编码（每字节 7 位 + 延续位；最大值 268435455）。 */
internal fun mqttVarIntBytes(value: Int): ByteArray {
    require(value in 0..268435455) { "MQTT 变长整数越界：$value" }
    val out = ByteArrayOutputStream()
    var v = value
    do {
        var b = v and 0x7F
        v = v ushr 7
        if (v > 0) b = b or 0x80
        out.write(b)
    } while (v > 0)
    return out.toByteArray()
}

/** MQTT 变长整数解码：返回（值，占用字节数）；不完整返回 null。 */
internal fun mqttReadVarInt(bytes: ByteArray, offset: Int): Pair<Int, Int>? {
    var value = 0
    var multiplier = 1
    var pos = offset
    while (pos < bytes.size && pos - offset < 4) {
        val b = bytes[pos].toInt() and 0xFF
        value += (b and 0x7F) * multiplier
        pos++
        if (b and 0x80 == 0) return value to (pos - offset)
        multiplier *= 128
    }
    return null
}

/** MQTT UTF-8 字符串编码（2 字节大端长度 + 内容）。 */
internal fun mqttEncodeString(s: String): ByteArray {
    val utf8 = s.toByteArray(Charsets.UTF_8)
    return byteArrayOf((utf8.size shr 8).toByte(), (utf8.size and 0xFF).toByte()) + utf8
}

/** MQTT UTF-8 字符串解码：返回（字符串，占用字节数）；不完整返回 null。 */
internal fun mqttReadString(bytes: ByteArray, offset: Int): Pair<String, Int>? {
    if (offset + 2 > bytes.size) return null
    val len = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    if (offset + 2 + len > bytes.size) return null
    return String(bytes, offset + 2, len, Charsets.UTF_8) to (2 + len)
}

/** MQTT 二进制数据解码：返回（字节，占用字节数）；不完整返回 null。 */
internal fun mqttReadBinary(bytes: ByteArray, offset: Int): Pair<ByteArray, Int>? {
    if (offset + 2 > bytes.size) return null
    val len = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    if (offset + 2 + len > bytes.size) return null
    return bytes.copyOfRange(offset + 2, offset + 2 + len) to (2 + len)
}

/** MQTT 5.0 属性项：值是 Int（字节/2 字节/4 字节/变长整数）、String、ByteArray（二进制）或 Pair<String, String>（UserProperty）。 */
internal data class MqttProperty(val id: Int, val value: Any)

/** 解析属性块：返回（属性列表，下一个字节偏移）。未知属性无法安全跳过，解析到此为止。 */
internal fun mqttParseProperties(bytes: ByteArray, offset: Int): Pair<List<MqttProperty>, Int> {
    val (propLen, propLenSize) = mqttReadVarInt(bytes, offset) ?: return emptyList<MqttProperty>() to offset
    var pos = offset + propLenSize
    val end = (pos + propLen).coerceAtMost(bytes.size)
    val out = mutableListOf<MqttProperty>()
    while (pos < end) {
        val id = bytes[pos].toInt() and 0xFF
        pos++
        when (id) {
            // 1 字节：Payload Format Indicator / Request Problem Info / Request Response Info /
            // Maximum QoS / Retain Available / Wildcard / Subscription Identifiers / Shared Subscription
            0x01, 0x17, 0x19, 0x24, 0x25, 0x28, 0x29, 0x2A -> {
                if (pos >= end) break
                out += MqttProperty(id, bytes[pos].toInt() and 0xFF); pos++
            }
            // 2 字节：Server Keep Alive / Receive Maximum / Topic Alias Maximum / Topic Alias
            0x13, 0x21, 0x22, 0x23 -> {
                if (pos + 2 > end) break
                out += MqttProperty(id, ((bytes[pos].toInt() and 0xFF) shl 8) or (bytes[pos + 1].toInt() and 0xFF)); pos += 2
            }
            // 4 字节：Message Expiry / Session Expiry / Will Delay / Maximum Packet Size
            0x02, 0x11, 0x18, 0x27 -> {
                if (pos + 4 > end) break
                var v = 0
                repeat(4) { v = (v shl 8) or (bytes[pos].toInt() and 0xFF); pos++ }
                out += MqttProperty(id, v)
            }
            // 变长整数：Subscription Identifier
            0x0B -> {
                val (v, n) = mqttReadVarInt(bytes, pos) ?: break
                out += MqttProperty(id, v); pos += n
            }
            // 字符串：Content Type / Response Topic / Assigned Client ID / Auth Method /
            // Response Information / Server Reference / Reason String
            0x03, 0x08, 0x12, 0x15, 0x1A, 0x1C, 0x1F -> {
                val (s, n) = mqttReadString(bytes, pos) ?: break
                out += MqttProperty(id, s); pos += n
            }
            // 二进制：Correlation Data / Authentication Data
            0x09, 0x16 -> {
                val (b, n) = mqttReadBinary(bytes, pos) ?: break
                out += MqttProperty(id, b); pos += n
            }
            // UserProperty：字符串对
            0x26 -> {
                val (k, n1) = mqttReadString(bytes, pos) ?: break
                val (v, n2) = mqttReadString(bytes, pos + n1) ?: break
                out += MqttProperty(id, k to v); pos += n1 + n2
            }
            else -> break
        }
    }
    return out to end
}

/** CONNECT 报文（L-1124 _connect_mobile_mqtt 的属性：AuthMethod=pass + 5 个 UserProperty）。 */
internal fun mqttBuildConnect(clientId: String, keepAliveSeconds: Int, userProperties: List<Pair<String, String>>): ByteArray {
    val body = ByteArrayOutputStream()
    body.write(mqttEncodeString("MQTT"))
    body.write(5) // 协议级别 5
    body.write(0x02) // Clean Start；无 Will、无用户名/密码
    body.write(keepAliveSeconds shr 8)
    body.write(keepAliveSeconds and 0xFF)
    val props = ByteArrayOutputStream()
    props.write(0x15) // Authentication Method
    props.write(mqttEncodeString("pass"))
    for ((k, v) in userProperties) {
        props.write(0x26) // User Property
        props.write(mqttEncodeString(k))
        props.write(mqttEncodeString(v))
    }
    val propsBytes = props.toByteArray()
    body.write(mqttVarIntBytes(propsBytes.size))
    body.write(propsBytes)
    body.write(mqttEncodeString(clientId))
    val payload = body.toByteArray()
    return byteArrayOf(0x10.toByte()) + mqttVarIntBytes(payload.size) + payload
}

/** CONNACK 解析结果。 */
internal data class MqttConnack(
    val sessionPresent: Boolean,
    val reasonCode: Int,
    val serverReference: String?,
    val serverKeepAlive: Int?,
)

internal fun mqttParseConnack(packet: ByteArray): MqttConnack {
    val (_, varIntLen) = mqttReadVarInt(packet, 1) ?: throw IllegalArgumentException("CONNACK 长度解析失败")
    var pos = 1 + varIntLen
    val sessionPresent = (packet[pos].toInt() and 0x01) != 0
    pos++
    val reasonCode = packet[pos].toInt() and 0xFF
    pos++
    val (props, _) = mqttParseProperties(packet, pos)
    val serverReference = props.firstOrNull { it.id == 0x1C }?.value as? String
    val serverKeepAlive = props.firstOrNull { it.id == 0x13 }?.value as? Int
    return MqttConnack(sessionPresent, reasonCode, serverReference, serverKeepAlive)
}

/** SUBSCRIBE 报文（QoS 0；属性只带 UserProperty）。 */
internal fun mqttBuildSubscribe(packetId: Int, topic: String, userProperties: List<Pair<String, String>>): ByteArray {
    val body = ByteArrayOutputStream()
    body.write(packetId shr 8)
    body.write(packetId and 0xFF)
    val props = ByteArrayOutputStream()
    for ((k, v) in userProperties) {
        props.write(0x26)
        props.write(mqttEncodeString(k))
        props.write(mqttEncodeString(v))
    }
    val propsBytes = props.toByteArray()
    body.write(mqttVarIntBytes(propsBytes.size))
    body.write(propsBytes)
    body.write(mqttEncodeString(topic))
    body.write(0x00) // 订阅选项：QoS 0
    val payload = body.toByteArray()
    return byteArrayOf(0x82.toByte()) + mqttVarIntBytes(payload.size) + payload
}

/** SUBACK 解析结果。 */
internal data class MqttSuback(val packetId: Int, val reasonCodes: List<Int>)

internal fun mqttParseSuback(packet: ByteArray): MqttSuback {
    val (_, varIntLen) = mqttReadVarInt(packet, 1) ?: throw IllegalArgumentException("SUBACK 长度解析失败")
    var pos = 1 + varIntLen
    val packetId = ((packet[pos].toInt() and 0xFF) shl 8) or (packet[pos + 1].toInt() and 0xFF)
    pos += 2
    val (_, next) = mqttParseProperties(packet, pos)
    pos = next
    val codes = (pos until packet.size).map { packet[it].toInt() and 0xFF }
    return MqttSuback(packetId, codes)
}

/** PUBLISH 解析结果（UserProperty 折成 map；payload 原样返回）。 */
internal data class MqttPublish(val topic: String, val userProperties: Map<String, String>, val payload: ByteArray)

internal fun mqttParsePublish(packet: ByteArray): MqttPublish {
    val flags = packet[0].toInt() and 0x0F
    val qos = (flags shr 1) and 0x03
    val (_, varIntLen) = mqttReadVarInt(packet, 1) ?: throw IllegalArgumentException("PUBLISH 长度解析失败")
    var pos = 1 + varIntLen
    val (topic, n) = mqttReadString(packet, pos) ?: throw IllegalArgumentException("PUBLISH 主题解析失败")
    pos += n
    // 服务端按订阅 QoS 0 下发；QoS 1/2 时多 2 字节 packet id（不回 PUBACK，本客户端只订阅 QoS 0）。
    if (qos > 0) pos += 2
    val (props, next) = mqttParseProperties(packet, pos)
    pos = next
    val userProps = props.filter { it.id == 0x26 }.mapNotNull { it.value as? Pair<String, String> }.toMap()
    return MqttPublish(topic, userProps, packet.copyOfRange(pos, packet.size))
}

/** PINGREQ / DISCONNECT（固定 2 字节）。 */
internal fun mqttBuildPingreq(): ByteArray = byteArrayOf(0xC0.toByte(), 0x00)
internal fun mqttBuildDisconnect(): ByteArray = byteArrayOf(0xE0.toByte(), 0x00)

/** 服务端重定向后的握手路径（L-1124 _build_redirect_path）：末段含冒号时替换末段，否则追加。 */
internal fun mqttRedirectPath(path: String, serverReference: String): String {
    val parts = path.trimEnd('/').split("/").toMutableList()
    if (parts.isNotEmpty() && ":" in parts.last()) {
        parts[parts.lastIndex] = serverReference
        return parts.joinToString("/")
    }
    return "${path.trimEnd('/')}/$serverReference"
}

/**
 * WebSocket 二进制帧 → MQTT 报文的缓冲：一个帧可能含多个报文，也可能只含
 * 半个报文；攒够一个完整报文（定长头 + 变长剩余长度 + 载荷）才交出。
 */
internal class MqttFrameBuffer {
    private var pending = ByteArray(0)

    fun feed(bytes: ByteArray): List<ByteArray> {
        pending += bytes
        val out = mutableListOf<ByteArray>()
        while (true) {
            if (pending.size < 2) break
            val (remainingLength, varIntLen) = mqttReadVarInt(pending, 1) ?: break
            val total = 1 + varIntLen + remainingLength
            if (pending.size < total) break
            out += pending.copyOfRange(0, total)
            pending = pending.copyOfRange(total, pending.size)
        }
        return out
    }
}

/** MQTT cookies 事件的 payload → (uin, key)；缺失或格式错误返回 null。 */
internal fun parseMobileQrCookies(payload: ByteArray): Pair<String, String>? {
    val root = runCatching { JSONObject(String(payload, Charsets.UTF_8)) }.getOrNull() ?: return null
    val cookies = root.optJSONObject("cookies") ?: return null
    val uin = cookies.optJSONObject("qqmusic_uin")?.optString("value", "").orEmpty()
    val key = cookies.optJSONObject("qqmusic_key")?.optString("value", "").orEmpty()
    if (uin.isBlank() || key.isBlank()) return null
    return uin to key
}

// ---- WebSocket session ----

private sealed interface WsEvent {
    data object Opened : WsEvent
    data class Bytes(val data: ByteArray) : WsEvent
    data class Closed(val code: Int, val reason: String) : WsEvent
    data class Failed(val error: Throwable) : WsEvent
}

private sealed interface WatchOutcome {
    data class Done(val credential: QqCredential) : WatchOutcome
    /** 拒绝 / 超时：外层按事件决定重取还是报错。 */
    data object Terminal : WatchOutcome
    /** CONNACK 0x9C/0x9D：服务端要求换节点。 */
    data class Redirect(val serverReference: String) : WatchOutcome
}

/**
 * QQ音乐 App 扫码的 MQTT 客户端（L-1124 utils/mqtt.py + login.py
 * checking_mobile_qrcode 的 Kotlin 移植，不引入 Paho 依赖）：
 * OkHttp WebSocket 做传输层（wss://mu.y.qq.com:443/ws/handshake，
 * 子协议 mqtt），手写 MQTT 5.0 最小子集——CONNECT（含鉴权属性）、
 * CONNACK（含重定向）、SUBSCRIBE/SUBACK、PUBLISH（type 属性分发）、
 * PINGREQ/PINGRESP 保活。
 *
 * 未验证（需真机）：服务端是否接受该握手（子协议、鉴权属性）、
 * CONNACK 是否带 ServerReference、PUBLISH 的 type 属性与 payload
 * 格式是否与 L-1124 一致。任一步与实测不符都属预期内风险，见 NOTES。
 */
class QqMqttClient(
    private val qqClient: QqClient,
    /** WebSocket 根地址（测试用 MockWebServer 时指向本地 ws:// 地址）。 */
    private val baseWsUrl: String = "wss://mu.y.qq.com",
) {
    companion object {
        private const val KEEP_ALIVE_SECONDS = 45
        private const val MAX_REDIRECTS = 3
        /** Paho websockets 传输默认的子协议；服务端按 MQTT over WebSocket 识别。 */
        private const val WS_SUBPROTOCOL = "mqtt"
        private const val WS_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
    }

    private val wsHttp = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // WebSocket 不设读超时，靠 MQTT 保活 + deadline
        .build()

    /**
     * 订阅 management.qrcode_login/{qrcodeId}，把服务端事件通过 [onEvent]
     * 推出；收到 cookies 事件后内部调 [QqClient.exchangeMobileQrLogin]
     * 换凭证，成功返回凭证（并先推 Done 事件）；用户拒绝或超时返回 null。
     * 网络错误抛 [ApiException]。超时默认 5 分钟（与 QQ 扫码的二维码有效期同量级）。
     */
    suspend fun watchMobileQrLogin(
        qrcodeId: String,
        timeoutMs: Long = 300_000,
        onEvent: (QqMobileQrEvent) -> Unit,
    ): QqCredential? {
        val deadlineMs = System.currentTimeMillis() + timeoutMs
        var path = "/ws/handshake"
        var redirects = 0
        while (true) {
            when (val outcome = watchOnce(qrcodeId, path, deadlineMs, onEvent)) {
                is WatchOutcome.Done -> return outcome.credential
                is WatchOutcome.Terminal -> return null
                is WatchOutcome.Redirect -> {
                    if (redirects >= MAX_REDIRECTS) {
                        throw ApiException(ApiErrorKind.Unknown, message = "QQ音乐扫码登录失败：MQTT 多次重定向")
                    }
                    redirects++
                    path = mqttRedirectPath(path, outcome.serverReference)
                }
            }
        }
    }

    private enum class SessionState { AwaitOpen, AwaitConnack, AwaitSuback, Running }

    private suspend fun watchOnce(
        qrcodeId: String,
        path: String,
        deadlineMs: Long,
        onEvent: (QqMobileQrEvent) -> Unit,
    ): WatchOutcome = withContext(Dispatchers.IO) {
        val events = Channel<WsEvent>(Channel.UNLIMITED)
        val clientId = "${System.currentTimeMillis()}${Random.nextInt(1000, 9999)}"
        val request = Request.Builder()
            .url("$baseWsUrl$path")
            .header("Origin", "https://y.qq.com")
            .header("Referer", "https://y.qq.com/")
            .header("User-Agent", WS_UA)
            .header("Sec-WebSocket-Protocol", WS_SUBPROTOCOL)
            .build()
        val socket = wsHttp.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                events.trySend(WsEvent.Opened)
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                events.trySend(WsEvent.Bytes(bytes.toByteArray()))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                events.trySend(WsEvent.Closed(code, reason))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                events.trySend(WsEvent.Closed(code, reason))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                events.trySend(WsEvent.Failed(t))
            }
        })
        try {
            val connectProps = listOf(
                "tmeAppID" to "qqmusic",
                "business" to "management",
                "hashTag" to qrcodeId,
                "clientTag" to "management.user",
                "userID" to qrcodeId,
            )
            val connectPacket = mqttBuildConnect(clientId, KEEP_ALIVE_SECONDS, connectProps)
            val topic = "management.qrcode_login/$qrcodeId"
            val subscribeProps = listOf("authorization" to "tmelogin", "pubsub" to "unicast")
            val subscribePacket = mqttBuildSubscribe(1, topic, subscribeProps)
            val frames = MqttFrameBuffer()
            var state = SessionState.AwaitOpen
            var keepAliveSeconds = KEEP_ALIVE_SECONDS
            var lastRxMs = System.currentTimeMillis()
            while (true) {
                val remaining = deadlineMs - System.currentTimeMillis()
                if (remaining <= 0) {
                    onEvent(QqMobileQrEvent.Timeout)
                    return@withContext WatchOutcome.Terminal
                }
                // 保活：一个 keepAlive 周期没收到任何报文就发 PINGREQ。
                if (state == SessionState.Running && System.currentTimeMillis() - lastRxMs > keepAliveSeconds * 1000L) {
                    socket.send(mqttBuildPingreq().toByteString())
                    lastRxMs = System.currentTimeMillis()
                }
                val ev = withTimeoutOrNull(minOf(remaining, 15_000)) { events.receive() } ?: continue
                when (ev) {
                    is WsEvent.Opened -> {
                        if (state == SessionState.AwaitOpen) {
                            socket.send(connectPacket.toByteString())
                            state = SessionState.AwaitConnack
                        }
                    }
                    is WsEvent.Bytes -> {
                        lastRxMs = System.currentTimeMillis()
                        for (packet in frames.feed(ev.data)) {
                            when (packet[0].toInt() and 0xF0) {
                                0x20 -> { // CONNACK
                                    if (state != SessionState.AwaitConnack) continue
                                    val connack = mqttParseConnack(packet)
                                    if (connack.reasonCode == 0x00) {
                                        connack.serverKeepAlive?.let { keepAliveSeconds = it }
                                        socket.send(subscribePacket.toByteString())
                                        state = SessionState.AwaitSuback
                                    } else if (connack.reasonCode in setOf(0x9C, 0x9D) && !connack.serverReference.isNullOrBlank()) {
                                        return@withContext WatchOutcome.Redirect(connack.serverReference)
                                    } else {
                                        throw ApiException(
                                            ApiErrorKind.Unknown,
                                            message = "QQ音乐扫码登录失败：MQTT 连接被拒（0x${connack.reasonCode.toString(16)}）",
                                        )
                                    }
                                }
                                0x90 -> { // SUBACK
                                    if (state != SessionState.AwaitSuback) continue
                                    val suback = mqttParseSuback(packet)
                                    if (suback.reasonCodes.any { it >= 0x80 }) {
                                        throw ApiException(ApiErrorKind.Unknown, message = "QQ音乐扫码登录失败：MQTT 订阅被拒")
                                    }
                                    state = SessionState.Running
                                    onEvent(QqMobileQrEvent.Waiting)
                                }
                                0x30 -> { // PUBLISH
                                    if (state != SessionState.Running) continue
                                    val pub = mqttParsePublish(packet)
                                    when (pub.userProperties["type"]) {
                                        "scanned" -> onEvent(QqMobileQrEvent.Scanned)
                                        "canceled" -> {
                                            onEvent(QqMobileQrEvent.Refused)
                                            return@withContext WatchOutcome.Terminal
                                        }
                                        "timeout" -> {
                                            onEvent(QqMobileQrEvent.Timeout)
                                            return@withContext WatchOutcome.Terminal
                                        }
                                        "loginFailed" -> throw ApiException(ApiErrorKind.Unknown, message = "QQ音乐扫码登录失败")
                                        "cookies" -> {
                                            val (uin, key) = parseMobileQrCookies(pub.payload)
                                                ?: throw ApiException(ApiErrorKind.Unknown, message = "QQ音乐扫码登录失败：缺少凭证")
                                            val credential = qqClient.exchangeMobileQrLogin(qrcodeId, uin, key)
                                            onEvent(QqMobileQrEvent.Done)
                                            return@withContext WatchOutcome.Done(credential)
                                        }
                                        else -> { /* 未知 type，忽略，继续等 */ }
                                    }
                                }
                                0xD0 -> { /* PINGRESP */ }
                                0xE0 -> throw ApiException(ApiErrorKind.Unknown, message = "QQ音乐扫码登录失败：MQTT 连接已断开")
                                else -> { /* 未知报文，忽略 */ }
                            }
                        }
                    }
                    is WsEvent.Closed -> throw ApiException(ApiErrorKind.Unknown, message = "QQ音乐扫码登录失败：MQTT 连接已关闭")
                    is WsEvent.Failed -> throw ApiException(
                        ApiErrorKind.Unknown,
                        message = "QQ音乐扫码登录失败：网络错误（${ev.error.message ?: ev.error.javaClass.simpleName}）",
                    )
                }
            }
        } finally {
            // 大排查 D9：断开前发 DISCONNECT 包再关 socket——之前只
            // 关 socket，mqttBuildDisconnect 只有单测在用；服务端按
            // 正常断开处理，不再当异常掉线记。
            runCatching { socket.send(mqttBuildDisconnect().toByteString()) }
            runCatching { socket.close(1000, null) }
            events.close()
        }
        // 循环只经 return@withContext / 抛错退出；走到这里说明状态机出错。
        throw IllegalStateException("MQTT 会话意外结束")
    }
}
