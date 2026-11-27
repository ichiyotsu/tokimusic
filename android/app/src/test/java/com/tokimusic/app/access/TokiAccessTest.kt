package com.tokimusic.app.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本轮（0127）第 20 条（最终规格：Toki ID 绑定设备 SSAID）：
 * 派生稳定性/相异性、格式合法性（大量采样）、回退格式、身份集、
 * 名单解析与匹配、ID 覆盖（含按平台身份命中、坏值忽略、匹配
 * 覆盖派生 + 生效双 ID）。
 */
class TokiAccessTest {

    // ---------- SHA-256 ----------

    @Test
    fun sha256KnownVector() {
        // FIPS 180-4 标准向量："abc"。
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc"),
        )
    }

    // ---------- 设备派生 ----------

    @Test
    fun deriveIsStableForSameSsaid() {
        val a = TokiId.deriveFromDevice("9774d56d682e549c")
        val b = TokiId.deriveFromDevice("9774d56d682e549c")
        assertEquals(a, b)
    }

    @Test
    fun deriveMatchesFrozenV1Mapping() {
        // 冻结口径：输入串与字节→区间映射永不更改（升版只能双轨）。
        val ssaid = "9774d56d682e549c"
        val hex = sha256Hex("tokimusic-tokiid-v1|device|$ssaid")
        fun byteAt(i: Int) = hex.substring(i * 2, i * 2 + 2).toInt(16)
        val expected = TokiId.formatDigits(
            byteAt(0) % 100, byteAt(1) % 12 + 1, byteAt(2) % 28 + 1,
            byteAt(3) % 24, byteAt(4) % 100,
        )
        assertEquals(expected, TokiId.deriveFromDevice(ssaid))
    }

    @Test
    fun distinctSsaidsGiveDistinctIds() {
        val ids = (1..50).map { TokiId.deriveFromDevice("ssaid-$it") }.toSet()
        assertEquals(50, ids.size)
    }

    @Test
    fun derivedFormatIsAlwaysValid() {
        repeat(2_000) { i ->
            val id = TokiId.deriveFromDevice("device-$i-${i * 7919}")
            assertTrue("bad format: $id", TokiId.isValidFormat(id))
        }
    }

    @Test
    fun fallbackFormatIsValid() {
        // 确定性伪随机源（LCG）驱动回退生成，格式须恒合法。
        var state = 42L
        val next: (Int) -> Int = { bound ->
            state = state * 6364136223846793005L + 1442695040888963407L
            ((state ushr 33) % bound).toInt()
        }
        repeat(500) {
            assertTrue(TokiId.isValidFormat(TokiId.randomFallback(next)))
        }
    }

    @Test
    fun formatValidationRejectsBadValues() {
        assertFalse(TokiId.isValidFormat("2613250107")) // 月 13
        assertFalse(TokiId.isValidFormat("2611000107")) // 月 00
        assertFalse(TokiId.isValidFormat("2611290107")) // 日 29（上限 28）
        assertFalse(TokiId.isValidFormat("2611252407")) // 时 24
        assertFalse(TokiId.isValidFormat("26112501")) // 只有 8 位
        assertFalse(TokiId.isValidFormat("2611250a07")) // 非数字
        assertTrue(TokiId.isValidFormat("2611250107"))
        assertTrue(TokiId.isValidFormat("0001010000"))
    }

    // ---------- 身份集 ----------

    @Test
    fun normalizedIdentitiesComposition() {
        assertEquals(
            listOf("toki|2611250107", "netease|123", "qq|456"),
            normalizedIdentities("2611250107", "123", "456"),
        )
        assertEquals(
            listOf("toki|2611250107"),
            normalizedIdentities("2611250107", null, ""),
        )
        assertEquals(emptyList<String>(), normalizedIdentities(null, null, null))
    }

    @Test
    fun matchingSetIncludesDerivedAndEffective() {
        // 无 override：生效 = 派生，集合就是基础集。
        assertEquals(
            listOf("toki|1111111111", "netease|9"),
            matchingIdentitySet("1111111111", "1111111111", "9", null),
        )
        // 有 override：派生 ID 仍在集合里（不能靠覆盖溜走）。
        assertEquals(
            listOf("toki|2222222222", "netease|9", "toki|1111111111"),
            matchingIdentitySet("1111111111", "2222222222", "9", null),
        )
    }

    // ---------- 名单解析与匹配 ----------

    @Test
    fun parseBlocklistSkipsMalformed() {
        val hash = sha256Hex("toki|2611250107")
        val json = """{"version":1,"entries":[
            {"hash":"$hash","action":"block"},
            {"hash":"nothex","action":"block"},
            {"hash":"${sha256Hex("qq|1")}"},
            {"action":"block"}
        ]}"""
        val entries = parseBlocklist(json)
        assertEquals(1, entries.size)
        assertEquals(hash, entries[0].hash)
        assertEquals("block", entries[0].action)
        assertTrue(parseBlocklist("").isEmpty())
        assertTrue(parseBlocklist("not json").isEmpty())
        assertTrue(parseBlocklist("""{"version":1}""").isEmpty())
    }

    @Test
    fun blockMatchingHitAndMiss() {
        val entries = listOf(BlocklistEntry(sha256Hex("netease|12345"), "block"))
        assertTrue(isBlocked(listOf("toki|2611250107", "netease|12345"), entries))
        assertFalse(isBlocked(listOf("toki|2611250107", "netease|999"), entries))
        assertFalse(isBlocked(emptyList(), entries))
        assertFalse(isBlocked(listOf("netease|12345"), emptyList()))
        // 非 block 动作不封。
        val warn = listOf(BlocklistEntry(sha256Hex("netease|12345"), "warn"))
        assertFalse(isBlocked(listOf("netease|12345"), warn))
    }

    @Test
    fun blockMatchingCoversDerivedAndEffectiveIds() {
        val derived = "1111111111"
        val effective = "2222222222"
        val set = matchingIdentitySet(derived, effective, null, null)
        // 封派生 ID → 仍命中（override 不能洗白）。
        assertTrue(isBlocked(set, listOf(BlocklistEntry(sha256Hex("toki|$derived"), "block"))))
        // 封生效（签发）ID → 命中。
        assertTrue(isBlocked(set, listOf(BlocklistEntry(sha256Hex("toki|$effective"), "block"))))
        // 封无关 ID → 不命中。
        assertFalse(isBlocked(set, listOf(BlocklistEntry(sha256Hex("toki|3333333333"), "block"))))
    }

    // ---------- ID 覆盖 ----------

    @Test
    fun overrideAppliesByDerivedIdHash() {
        val derived = TokiId.deriveFromDevice("ssaid-A")
        val overrides = listOf(TokiIdOverride(sha256Hex("toki|$derived"), "2611250199"))
        val identities = normalizedIdentities(derived, null, null)
        assertEquals("2611250199", effectiveTokiId(derived, identities, overrides))
    }

    @Test
    fun overrideAppliesByPlatformIdHash() {
        val derived = TokiId.deriveFromDevice("ssaid-B")
        val overrides = listOf(TokiIdOverride(sha256Hex("netease|777"), "2501010055"))
        val identities = normalizedIdentities(derived, "777", null)
        assertEquals("2501010055", effectiveTokiId(derived, identities, overrides))
        // 未登录该平台时不命中，仍用派生 ID。
        val anonIdentities = normalizedIdentities(derived, null, null)
        assertEquals(derived, effectiveTokiId(derived, anonIdentities, overrides))
    }

    @Test
    fun overrideMissKeepsDerived() {
        val derived = TokiId.deriveFromDevice("ssaid-C")
        val overrides = listOf(TokiIdOverride(sha256Hex("toki|9999999999"), "2611250199"))
        assertEquals(derived, effectiveTokiId(derived, normalizedIdentities(derived, null, null), overrides))
        assertEquals(derived, effectiveTokiId(derived, normalizedIdentities(derived, null, null), emptyList()))
    }

    @Test
    fun parseOverridesSkipsMalformed() {
        val good = sha256Hex("toki|2611250107")
        val json = """{"version":1,"overrides":[
            {"hash":"$good","tokiId":"2611250199"},
            {"hash":"${sha256Hex("qq|1")}","tokiId":"2613250199"},
            {"hash":"xyz","tokiId":"2611250199"},
            {"hash":"${sha256Hex("qq|2")}"}
        ]}"""
        // 第二条 tokiId 月 13 非法、第三条 hash 非法、第四条缺 tokiId → 只剩第一条。
        val parsed = parseOverrides(json)
        assertEquals(1, parsed.size)
        assertEquals(good, parsed[0].hash)
        assertEquals("2611250199", parsed[0].tokiId)
        assertTrue(parseOverrides("").isEmpty())
        assertTrue(parseOverrides("{}").isEmpty())
    }

    @Test
    fun derivedIdsDifferAcrossDevicesSample() {
        val a = TokiId.deriveFromDevice("aaaaaaaa11111111")
        val b = TokiId.deriveFromDevice("bbbbbbbb22222222")
        assertNotEquals(a, b)
    }
}
