package com.tokimusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 本轮（0124）第 15 项：音乐等级纯函数——用户定版数值（60×L² 门槛、每日上限 135、1 分/分钟、整首 +2）。 */
class MusicLevelTest {

    @Test
    fun thresholdsFollowSixtyTimesLevelSquared() {
        assertEquals(0, MusicLevel.levelFloor(1))
        assertEquals(240, MusicLevel.levelFloor(2))
        assertEquals(540, MusicLevel.levelFloor(3))
        assertEquals(37_500, MusicLevel.levelFloor(25))
        // 24 → 25 的增量 = 60 × (2×24 + 1) = 2,940。
        assertEquals(2_940, MusicLevel.levelFloor(25) - MusicLevel.levelFloor(24))
    }

    @Test
    fun levelForPointsBoundaries() {
        assertEquals(1, MusicLevel.levelForPoints(0))
        assertEquals(1, MusicLevel.levelForPoints(239))
        assertEquals(2, MusicLevel.levelForPoints(240))
        assertEquals(24, MusicLevel.levelForPoints(37_499))
        assertEquals(25, MusicLevel.levelForPoints(37_500))
        assertEquals(25, MusicLevel.levelForPoints(999_999))
        assertEquals(1, MusicLevel.levelForPoints(-5))
    }

    @Test
    fun nextFloorNullAtMaxLevel() {
        assertEquals(240, MusicLevel.nextLevelFloor(1))
        assertEquals(37_500, MusicLevel.nextLevelFloor(24))
        assertNull(MusicLevel.nextLevelFloor(25))
    }

    @Test
    fun progressWithinLevel() {
        assertEquals(0f, MusicLevel.progressInLevel(0), 0.001f)
        // Lv2 区间 [240, 540)：390 恰在中点。
        assertEquals(0.5f, MusicLevel.progressInLevel(390), 0.001f)
        assertEquals(1f, MusicLevel.progressInLevel(37_500), 0.001f)
    }

    @Test
    fun rawPointsMinutesAndCompletionBonus() {
        assertEquals(0, MusicLevel.rawPoints(59, completed = false))
        assertEquals(1, MusicLevel.rawPoints(60, completed = false))
        assertEquals(1, MusicLevel.rawPoints(61, completed = false))
        assertEquals(3, MusicLevel.rawPoints(180, completed = false))
        assertEquals(5, MusicLevel.rawPoints(180, completed = true))
        assertEquals(2, MusicLevel.rawPoints(30, completed = true))
    }

    @Test
    fun completionNeedsNinetyPercent() {
        assertTrue(MusicLevel.isCompleted(180, 200_000))
        assertFalse(MusicLevel.isCompleted(179, 200_000))
        assertFalse(MusicLevel.isCompleted(100, 0))
        assertFalse(MusicLevel.isCompleted(0, 200_000))
    }

    @Test
    fun dailyCapClampsGain() {
        assertEquals(135, MusicLevel.DAILY_CAP)
        assertEquals(10, MusicLevel.cappedGain(10, todayPoints = 0))
        assertEquals(5, MusicLevel.cappedGain(10, todayPoints = 130))
        assertEquals(0, MusicLevel.cappedGain(10, todayPoints = 135))
        assertEquals(0, MusicLevel.cappedGain(10, todayPoints = 200))
    }

    @Test
    fun maxLevelTakesAtLeastSixMonthsAtCap() {
        // 用户口径：满级 37,500 分、每日顶格 135 → ≥ 278 天 > 6 个月。
        val days = MusicLevel.levelFloor(25).toDouble() / MusicLevel.DAILY_CAP
        assertTrue(days >= 180.0)
        assertEquals(278, Math.ceil(days).toInt())
    }

    @Test
    fun pointCountFormatting() {
        assertEquals("0", formatPointCount(0))
        assertEquals("240", formatPointCount(240))
        assertEquals("37,500", formatPointCount(37_500))
        assertEquals("1,234,567", formatPointCount(1_234_567))
    }

    @Test
    fun earningRowsReflectTodayProgress() {
        // 第 15b 项：达成条件两行——分钟分与完成奖励分开记账展示。
        val snap = MusicLevelSnapshot(
            totalPoints = 300, level = 2, levelFloor = 240, nextLevelFloor = 540,
            progress = 0.2f, todayPoints = 50, todayMinutePoints = 44,
            todayCompletions = 3, dailyCap = 135,
        )
        val rows = levelEarningRows(snap)
        assertEquals(2, rows.size)
        assertEquals("听歌时长", rows[0].title)
        assertEquals("今日已得 44 分", rows[0].progressText)
        assertEquals("整首听完奖励", rows[1].title)
        assertEquals("今日已完成 3 首 · +6 分", rows[1].progressText)
    }
}
