package com.tokimusic.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.tokimusic.app.R

/**
 * Latin is Inter. Chinese is PingFang SC Bold. Japanese is Noto Sans JP Bold.
 * All three files are committed in src/main/res/font (spec 0.2.0-b1, see android/tokiui/SYNC.md).
 * Titles and labels use wght 700. Body and captions use wght 700 as well
 * (user 2026-11-21: global Bold; the TokiUI 0.2.0-b1 body-Regular rule is
 * intentionally not followed here).
 *
 * 0.1.15 第 8 项（日文粗体根因，已实测定位）：CJK 三个文件之前是可变字体，
 * 靠 FontVariation.weight(700) 取粗体——但 Compose 1.12.1 的
 * AndroidFontLoader 加载资源字体只走 ResourcesCompat.getFont(context,
 * resId)，variationSettings 根本不传（对 ui-text AAR 做 javap 确认：
 * AndroidFontLoader_androidKt.load 里只有 getFont 一行）。于是三个文件
 * 全按默认轴渲染：SC / JP / KR 的 fvar 默认都是 wght 100（族名就叫
 * "Noto Sans * Thin"，usWeightClass 100），日文按 Thin 出字、再叠一层
 * 很弱的合成粗体，看起来就是不粗；而 Inter 默认 wght 400（Regular），
 * 合成粗体后看着够粗，用户才只报日文。字体文件本身没问题
 * （fontTools instancer 在 wght 100 与 700 下实例化出的轮廓确实不同）。
 * 修法：三个 CJK 文件改用 fontTools 从可变字体在 wght 700 处实例化出的
 * 静态 Bold（同名替换，SC 17.7MB → 10.6MB、JP 566KB → 335KB、
 * KR 4.6MB → 2.7MB，总体还小了约 9MB；族名已正名为 Noto Sans SC/JP/KR、
 * usWeightClass 700、无 fvar/gvar），声明时不再传 variationSettings——
 * 静态文件不需要它。Inter 仍是可变字体（拉丁文 400 + 合成粗体看着够粗，
 * 用户没报，暂不动）。
 *
 * 0.1.18 第 3 项（用户定全局字体 Inter + PingFangSC + NotoJP，默认 bold）：
 * 中文从思源黑体 SC 换成苹方 SC Bold（pingfang_bold.ttf，10,923,212 B，
 * 从 0.1.16 的 git 历史原样恢复，见 NOTES.md 第 3 项）。fontTools 实测：
 * 族名 "PingFang SC Bold"、42,199 字形、无 fvar/gvar；假名・繁体・辻・凪
 * 都在内，兼容字 FA10 塚 也在内（SC 没有它）；不含谚文、半角片假名、
 * FA6D 与 FAD0/FAD6。注意该文件 OS/2.usWeightClass 写的是 400（族名虽
 * 是 Bold，元数据仍标 Regular）——文件未做任何改动：声明沿用 0.1.15/
 * 0.1.16 歌词字体预设同款 `Font(R.font.pingfang_bold, FontWeight.Bold)`，
 * 用户当时已看过这个文件的渲染效果，沿用即是他要的样子。
 *
 * 日 / 韩 / 繁中回退分工（0.1.18 第 3 项定链，0.1.19 第 3 项换序）：
 * 0.1.18 时链序是苹方 → JP → KR，逐字回退“先中者胜”——假名在苹方里
 * 也有，于是日文假名实际走苹方字形，不是 JP。0.1.19 第 3 项（用户：
 * 日语字体默认且仅用粗体）把 JP 提到第一位：
 * - 假名、半角片假名、F900–FAFF、CJK 兼容字（2F800–2FA1F）——即
 *   noto_sans_jp_bold.ttf 子集的 1,074 字形——默认走 Noto JP Bold。
 * - 一般汉字不在 JP 子集里，仍落苹方 SC Bold：中文不受影响（汉字
 *   照旧苹方形状）；日文文本里的一般汉字同理落苹方，这是子集字库 +
 *   逐字回退机制下能做到的范围（JP 完整版字体不在包内）。
 * - 谚文音节 JP 与苹方都没有，由 noto_sans_kr.ttf 补（排最后）。
 *   注意：者 U+FAD6 与 﨑 U+FAD0 在苹方与全部 Noto 字体里都没有，
 *   这两字仍走系统回退。
 * 三个文件全部保留在包内、且只以 FontWeight.Bold 声明（见
 * cjkBoldFonts）——CJK 全程只有粗体，全仓没有任何常规字重的 JP
 * 字体引用（0.1.19 改前 grep 核实：AppTypeface 之外无 FontFamily
 * 声明、无 noto_sans_jp 的其它引用）。
 */
/** CJK 回退链顺序（0.1.19 第 3 项：JP 第一、苹方第二、KR 最后，可单测）。
 * 0.1.18 时是苹方 → JP → KR，日文假名被苹方抢走；换序后日文默认 JP。 */
internal val cjkFallbackOrder = listOf(R.font.noto_sans_jp_bold, R.font.pingfang_bold, R.font.noto_sans_kr)

/** CJK 静态粗体（0.1.15 第 8 项）：三个文件本身就是 Bold 字形（Noto 两个
 * usWeightClass 700；PingFang 的 OS/2 写 400 但族名与轮廓都是 Bold，见上），
 * 声明时只给 weight，不传 variationSettings——静态文件没有 fvar 可调，
 * 传了也会被资源字体加载路径忽略。 */
private fun cjkBoldFonts(): List<Font> =
    cjkFallbackOrder.map { Font(it, weight = FontWeight.Bold) }

private fun latinVariableFont(resId: Int, weight: FontWeight): Font {
    val variation = FontVariation.Settings(FontVariation.weight(weight.weight))
    return Font(resId, weight = weight, variationSettings = variation)
}

/**
 * CJK 粗体优先的字体族（0.1.17 第 2 项）：只有三个 CJK 静态 Bold 文件，
 * 不含 Inter——给 CJK 为主的标题用（如推荐歌单卡片标题），绕开
 * AndroidFontListTypeface 的 firstOrNull 匹配（见 NOTES.md 第 2 项：
 * InterFamily 里 Inter 条目排在 CJK 之前，Bold 请求永远解析成 Inter，
 * CJK 静态 Bold 文件从未被选中）。标题里的少量拉丁文走 PingFang SC Bold 的拉丁字形。
 */
val CjkBoldFamily: FontFamily = FontFamily(cjkBoldFonts())

/**
 * 第五轮第 2 项（用户 2026-11-23 定稿“Google Inter 字体”）；0.1.18 第 3 项
 * 用户定全局字体搭配 = Google Inter（Google Fonts 上的 Inter）+ 苹方 SC
 * 粗体 + 思源黑体 JP 粗体。西文全局继续用 Inter，不改用 Google Sans——
 * 歌词字体预设已在 0.1.17 第 8 项删除，其 Bold 字体文件随之删除。
 * 日 / 韩 / 繁中回退链见文件头注释。
 */
val InterFamily: FontFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
        .map { latinVariableFont(R.font.inter, it) } +
        // 0.1.15 第 8 项：CJK 只声明一次 Bold（静态文件本身就是 Bold，
        // 任意字重请求都落到它，全局粗体）。
        cjkBoldFonts(),
)
