package com.tokimusic.app.data

import android.content.Context
import androidx.annotation.StringRes

/**
 * 本轮（0127）第 9 条（四语言）：非 Compose 层（ViewModel / 数据层 /
 * 播放器）取本地化文案的统一入口。Compose 层直接用 stringResource，
 * 不走这里。
 *
 * 原理：保存 Application Context，getString 时由系统按 App 当前语言
 * （见 [AppLocale]，走 LocaleManager 的 per-app locales）解析
 * 各语言目录下的 strings.xml。语言切换由系统重建 Activity，之后所有
 * getString 自然返回新语言；已拼进状态流的旧文案在下一次状态
 * 刷新时更新（与既有错误文案的生命周期一致）。
 */
/**
 * 约定（0127 第 9 条）：非 Compose 层一律用 [getOrFallback] 并传入
 * 简体中文原文作兜底模板——JVM 单测环境下本对象未 init，兜底保证
 * 被单测锁定的中文输出逐字不变；真实运行时走当前语言资源。
 * [get] 只留给确信不被单测触达、且未 init 时可接受空串的场景。
 */
object AppStrings {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun get(@StringRes id: Int, vararg args: Any): String {
        val ctx = appContext ?: return ""
        return if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)
    }

    /**
     * 带兜底模板的版本：给「纯函数 + JVM 单测锁定简体中文输出」的
     * 格式化函数用（如 formatTotalDurationMs / downloadProgressText）。
     * 已 init（真实运行）时按当前语言取资源模板格式化；未 init
     * （JVM 单测环境）时用传入的简体中文默认模板格式化，输出与
     * 本轮改造前逐字一致，单测不受影响。
     */
    fun getOrFallback(@StringRes id: Int, fallbackTemplate: String, vararg args: Any): String {
        val ctx = appContext ?: return String.format(java.util.Locale.US, fallbackTemplate, *args)
        val template = ctx.getString(id)
        return if (args.isEmpty()) template else String.format(currentLocale(ctx), template, *args)
    }

    private fun currentLocale(ctx: Context): java.util.Locale {
        val locales = ctx.resources.configuration.locales
        return if (locales.isEmpty) java.util.Locale.getDefault() else locales.get(0)
    }
}
