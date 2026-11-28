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
 *
 * 0.1.21 日语字体修正（用户亲自追加，语气很重）：上面 0.1.19 的
 * “JP 子集排链首”方案有两个实测问题——① 包内 noto_sans_jp_bold.ttf
 * 旧文件只是 652 码位的子集（假名 + 半角片假名 + 兼容字），不含一般
 * 汉字：日文歌名/歌词里的汉字（歌・詞・愛…）逐字回退落到苹方 SC，
 * 以中文字形呈现，日语文本看着仍不对；② 链首 JP 子集对中文文本里
 * 与子集重合的码位（兼容字等）先中者胜，中文有被日文字形污染的
 * 风险。本轮改为“按文本语言选字体”：
 * - noto_sans_jp_bold.ttf 换成真 Noto Sans JP Bold 全量静态文件：
 *   由 Google Fonts 的 NotoSansJP 可变字体（~/workspace/work/
 *   cirro-round6/fonts/NotoSansJP-wght.ttf）在 wght 700 实例化 +
 *   按日语相关码位段子集化，15,369 码位（假名/半角片假名/一般汉字/
 *   兼容字全覆盖），5,558,848 B，usWeightClass 700、无 fvar/gvar、
 *   族名已正名 Noto Sans JP Bold；只带 Bold 一个字重（用户定）。
 *   不是系统 Noto Sans CJK 兜底，文件随包走。
 * - 通用回退链改回苹方第一（cjkFallbackOrder）：纯中文/汉字文本
 *   不再被 JP 抢字形；假名在苹方里也有，但含假名的文本整段走
 *   JpBoldFamily（见 containsKana / fontFamilyForText），不靠逐字
 *   回退碰运气。
 * - 含假名（平假名/片假名/半角片假名/片假名语音扩展）的文本判定
 *   为日语，整段用 JpBoldFamily（JP → 苹方 → KR）；其余文本字体
 *   链完全不动。接入点：SongRow 标题/副标题、PlaylistCard 名、
 *   DetailTitle、歌手页大标题、播放器/迷你播放器歌名歌手名、
 *   队列行、歌词原词行（逐行判定；翻译/罗马音行各自判定）。
 */
/** CJK 回退链顺序（0.1.21 改回苹方第一，可单测）：苹方 → JP → KR。
 * 0.1.19 曾把 JP 子集排第一让日文假名默认走 JP，但子集不含一般汉字、
 * 且对中文有字形污染风险；0.1.21 起日语文本由 fontFamilyForText
 * 整段选 JpBoldFamily，通用链回到苹方优先（0.1.18 的顺序）。 */
internal val cjkFallbackOrder = listOf(R.font.pingfang_bold, R.font.noto_sans_jp_bold, R.font.noto_sans_kr)

/**
 * 日语文本专用字体族（0.1.21）：内置 Noto Sans JP Bold 在第一位——
 * 含假名的文本整段经它渲染，汉字也取日文字形；JP 仍缺的字（如
 * FAD0/FAD6）才落苹方/KR。只声明 Bold 一个字重，与全局一致。
 */
val JpBoldFamily: FontFamily = FontFamily(
    Font(R.font.noto_sans_jp_bold, weight = FontWeight.Bold),
    Font(R.font.pingfang_bold, weight = FontWeight.Bold),
    Font(R.font.noto_sans_kr, weight = FontWeight.Bold),
)

/** 文本是否含假名（0.1.21，纯函数可单测）：平假名 U+3040–309F、
 * 片假名 U+30A0–30FF、片假名语音扩展 U+31F0–31FF、半角片假名
 * U+FF66–FF9D。含任一假名即按日语文本处理——纯汉字无法区分中日，
 * 按用户口径走中文字体链（不含假名的日文标题同理，属已知边界）。 */
fun containsKana(text: String): Boolean = text.any { ch ->
    val c = ch.code
    c in 0x3040..0x309F || c in 0x30A0..0x30FF || c in 0x31F0..0x31FF || c in 0xFF66..0xFF9D
}

/** 文本是否含谚文（本轮 0128 第 20 条扩展，纯函数可单测）：谚文
 * 音节 U+AC00–D7A3、谚文字母 U+1100–11FF、兼容字母 U+3130–318F、
 * 字母扩展 A U+A960–A97F / B U+D7B0–D7FB、半角字母 U+FFA0–FFDC。
 * 含任一谚文即按韩语文本处理（与假名判日语同口径）。 */
fun containsHangul(text: String): Boolean = text.any { ch ->
    val c = ch.code
    c in 0xAC00..0xD7A3 || c in 0x1100..0x11FF || c in 0x3130..0x318F ||
        c in 0xA960..0xA97F || c in 0xD7B0..0xD7FB || c in 0xFFA0..0xFFDC
}

/**
 * 韩语文本专用字体族（本轮 0128 第 20 条，与 JpBoldFamily 对称）：
 * 内置 Noto Sans KR（静态 Bold，包内既有文件）第一位——含谚文的
 * 文本整段经它渲染；KR 仍缺的字才落苹方/JP。只声明 Bold。
 */
val KrBoldFamily: FontFamily = FontFamily(
    Font(R.font.noto_sans_kr, weight = FontWeight.Bold),
    Font(R.font.pingfang_bold, weight = FontWeight.Bold),
    Font(R.font.noto_sans_jp_bold, weight = FontWeight.Bold),
)

/** 文本的字体判定结果（纯函数，单测覆盖判定本身，不碰 Font 构造）。 */
enum class TextFontKind { JP, KR, DEFAULT }

/** 按文本判定字体种类：含假名 → JP；否则含谚文 → KR；其余 DEFAULT。
 * 假名优先（与 0.1.21 既有口径一致；同含假名与谚文的文本极罕见）。 */
fun textFontKind(text: String): TextFontKind = when {
    containsKana(text) -> TextFontKind.JP
    containsHangul(text) -> TextFontKind.KR
    else -> TextFontKind.DEFAULT
}

/** 按文本语言选字体（0.1.21；本轮 0128 第 20 条扩韩语）：含假名 →
 * JpBoldFamily；含谚文 → KrBoldFamily；否则返回 [default]
 * （null = 用调用处原本的主题字体）。渲染歌名/歌手名/歌单名/
 * 歌词原词行时统一经它取值——韩语覆盖自动落在全部既有接入点。 */
fun fontFamilyForText(text: String, default: FontFamily? = null): FontFamily? =
    when (textFontKind(text)) {
        TextFontKind.JP -> JpBoldFamily
        TextFontKind.KR -> KrBoldFamily
        TextFontKind.DEFAULT -> default
    }

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
