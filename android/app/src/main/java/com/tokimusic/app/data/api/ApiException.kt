package com.tokimusic.app.data.api
import com.tokimusic.app.data.AppStrings
import com.tokimusic.app.R

/** Structured API failure, so the UI can tell the user what actually went wrong. */
enum class ApiErrorKind {
    /** NetEase unreachable / DNS / timeout. */
    Unreachable,
    /** HTTP status outside 2xx (body discarded). */
    HttpStatus,
    /** The service answered body code 301: the login has expired. */
    NotLoggedIn,
    /** Rate-limited / blocked by NetEase (HTTP 460/503, body -460). */
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
    ApiErrorKind.Unreachable -> AppStrings.getOrFallback(R.string.err_netease_unreachable, "连不上网易云：请检查网络连接后重试")
    ApiErrorKind.HttpStatus -> AppStrings.getOrFallback(R.string.err_netease_http, "网易云返回了错误（HTTP %1\$d）", e.httpCode)
    ApiErrorKind.NotLoggedIn -> AppStrings.getOrFallback(R.string.err_login_expired, "登录已过期，请重新扫码登录")
    ApiErrorKind.RateLimited -> AppStrings.getOrFallback(R.string.err_netease_rate_limited, "请求太频繁，被网易暂时限制了，稍后再试")
    ApiErrorKind.Unknown -> e.message?.takeIf { it.isNotBlank() } ?: AppStrings.getOrFallback(R.string.err_request_failed, "请求失败，请重试")
}
