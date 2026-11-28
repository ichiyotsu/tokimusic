package com.tokimusic.app.data

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import java.util.Locale

/**
 * 本轮（0127）第 9 条：App 语言（简体中文 / 繁體中文 / English /
 * 日本語）。走系统 per-app locales（LocaleManager，API 33+；
 * 本应用 minSdk 36 恒可用）：
 * - 选择由系统持久化（不进 SettingsStore，进程重启/重装设置页
 *   仍能读回——重装后系统设置随 App 数据清除而复位，符合预期）；
 * - setApplicationLocales 后系统自动重建 Activity，Compose 的
 *   stringResource 与 [AppStrings] 随之全部换语言；
 * - 未设置过（applicationLocales 为空）时默认简体中文——本 App
 *   的 values/ 默认目录即简体中文，直接读默认资源即可，但为让
 *   设置页有确定选中项，currentTag 在为空时返回 zh-CN。
 */
object AppLocale {
    const val ZH_CN = "zh-CN"
    const val ZH_TW = "zh-TW"
    const val EN = "en"
    const val JA = "ja"

    /** 设置页选项：tag → 该语言自身的名字（不随当前语言变化）。 */
    val OPTIONS: List<Pair<String, String>> = listOf(
        ZH_CN to "简体中文",
        ZH_TW to "繁體中文",
        EN to "English",
        JA to "日本語",
    )

    fun currentTag(context: Context): String {
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
        if (locales == null || locales.isEmpty) return ZH_CN
        return normalize(locales.get(0))
    }

    fun set(context: Context, tag: String) {
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        manager.setApplicationLocales(LocaleList.forLanguageTags(tag))
    }

    /** 把系统返回的 Locale 归一到四个受支持 tag 之一（纯函数，可单测）。 */
    fun normalize(locale: Locale): String = when {
        locale.language == "zh" && (locale.script == "Hant" || locale.country == "TW" ||
            locale.country == "HK" || locale.country == "MO") -> ZH_TW
        locale.language == "zh" -> ZH_CN
        locale.language == "ja" -> JA
        locale.language == "en" -> EN
        else -> ZH_CN
    }
}
