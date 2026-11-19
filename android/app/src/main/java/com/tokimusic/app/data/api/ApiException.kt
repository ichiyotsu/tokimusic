package com.tokimusic.app.data.api

/** Structured API failure, so the UI can tell the user what actually went wrong. */
enum class ApiErrorKind {
    /** Server unreachable / DNS / timeout — the service is not there. */
    Unreachable,
    /** The configured address itself is malformed (no scheme/host). */
    InvalidAddress,
    /** HTTP status outside 2xx (body discarded). */
    HttpStatus,
    /** The service answered body code 301: the login has expired. */
    NotLoggedIn,
    /** Rate-limited / blocked by the service (HTTP 460/503, body -460). */
    RateLimited,
    /** Anything else (bad body, unexpected shape). */
    Unknown,
}

class ApiException(
    val kind: ApiErrorKind,
    val httpCode: Int = 0,
    val apiCode: Int = 0,
    message: String? = null,
) : IllegalStateException(message ?: kind.name)

/** One-line, actionable Chinese text for an [ApiException]. */
fun apiErrorText(e: ApiException): String = when (e.kind) {
    ApiErrorKind.Unreachable -> "连不上 API 服务：请确认电脑上的服务在运行、手机与电脑在同一 Wi-Fi、防火墙放行了端口"
    ApiErrorKind.InvalidAddress -> "API 地址格式不对：请填 http:// 开头的完整地址，例如 http://192.168.1.10:3000"
    ApiErrorKind.HttpStatus -> "API 服务返回了错误（HTTP ${e.httpCode}）"
    ApiErrorKind.NotLoggedIn -> "登录已过期，请重新扫码登录"
    ApiErrorKind.RateLimited -> "请求太频繁，被服务暂时限制了，稍后再试"
    ApiErrorKind.Unknown -> e.message?.takeIf { it.isNotBlank() } ?: "请求失败，请重试"
}
