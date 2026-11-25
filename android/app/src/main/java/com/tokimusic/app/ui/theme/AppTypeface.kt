package com.tokimusic.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.tokimusic.app.R

/**
 * Latin is Inter. Chinese is Source Han Sans, bundled as official Noto Sans SC.
 * Both files are committed in src/main/res/font (spec 0.2.0-b1, see android/tokiui/SYNC.md).
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
 * 日 / 韩 / 繁中一律思源黑体 Bold：
 * - noto_sans_sc.ttf 是完整版（假名、全部 CJK 统一汉字（含繁体专用字如
 *   說、日文汉字如 辻）都在内；旧的 4.1MB 子集缺了大半汉字与全部谚文，
 *   已换成完整版（Google Fonts，NotoSansSC[wght].ttf）。
 * - 思源黑体 SC 连完整版也不含谚文音节，韩文由 noto_sans_kr.ttf 补——
 *   Noto Sans KR 的谚文子集（音节 11,172 全 + 谚文字母）。
 *   回退顺序永远是 SC 在前、KR 在后，共用字形只取 SC 的形状。
 * - 第六轮第 4 项：SC 只有 122 个 CJK 兼容表意字（F900–FAFF），日文
 *   数据里常见的兼容字（如 FA10 塚、FA6D）会落到系统字体；JP 子集
 *   noto_sans_jp_bold.ttf（假名 + 半角片假名 + F900–FAFF + 2F800–2FA1F
 *   共 1,074 字形）补在 SC 之后、KR 之前，共用字形仍取 SC 的形状。
 *   注意：者 U+FAD6 与 﨑 U+FAD0 在全部 Noto 字体里都没有（Google Fonts
 *   变量版与 Adobe 完整 OTF 均实测缺失），这两字仍走系统回退；JP 子集
 *   修的是其余 240 个 SC 所缺的兼容字。
 */
/** CJK 回退链顺序（第六轮第 4 项，可单测）：SC 在前、JP 在后、KR 最后。 */
internal val cjkFallbackOrder = listOf(R.font.noto_sans_sc, R.font.noto_sans_jp_bold, R.font.noto_sans_kr)

/** CJK 静态粗体（0.1.15 第 8 项）：三个文件本身就是 Bold（usWeightClass 700），
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
 * CJK 静态 Bold 文件从未被选中）。标题里的少量拉丁文走 SC Bold 的拉丁字形。
 */
val CjkBoldFamily: FontFamily = FontFamily(cjkBoldFonts())

/**
 * 第五轮第 2 项（用户 2026-11-23 定稿“Google Inter 字体”）：全局字体搭配 =
 * Google Inter（Google Fonts 上的 Inter）+ 思源黑体粗体。西文全局继续用
 * Inter，不改用 Google Sans——0.1.17 第 8 项起歌词字体预设已删，
 * 该字体文件只留在 res/font 里未被引用（见 NOTES.md 第 8 项）。
 * 日 / 韩 / 繁中一律思源黑体 Bold，见下面的 variableFonts 回退链。
 */
val InterFamily: FontFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
        .map { latinVariableFont(R.font.inter, it) } +
        // 0.1.15 第 8 项：CJK 只声明一次 Bold（静态文件本身就是 Bold，
        // 任意字重请求都落到它，全局粗体）。
        cjkBoldFonts(),
)
