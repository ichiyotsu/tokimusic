package com.tokimusic.app.data.api

import com.tokimusic.app.ui.scrobbleReportSeconds
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 0.1.17 第 13 项：上报 JSON 结构（上游 scrobble.js 同款）+ 上报时机。 */
class ScrobbleTest {
    @Test fun logsJsonShape() {
        val logs = JSONArray(scrobbleLogsJson(518066366L, 36780169L, 291))
        assertEquals(1, logs.length())
        val entry = logs.getJSONObject(0)
        assertEquals("play", entry.getString("action"))
        val json = entry.getJSONObject("json")
        assertEquals(518066366L, json.getLong("id"))
        assertEquals(36780169L, json.getLong("sourceId"))
        assertEquals(291, json.getInt("time"))
        assertEquals("playend", json.getString("end"))
        assertEquals("song", json.getString("type"))
        assertEquals("list", json.getString("source"))
    }

    @Test fun reportsAtThreshold() {
        // 4 分钟歌播够 240 秒即报。
        assertEquals(240, scrobbleReportSeconds(240_000L, 240_000L, reported = false, ended = false))
        // 10 分钟歌播够 240 秒即报（阈值取 min(时长, 240s)）。
        assertEquals(240, scrobbleReportSeconds(240_000L, 600_000L, reported = false, ended = false))
        // 未到阈值不报。
        assertNull(scrobbleReportSeconds(239_000L, 600_000L, reported = false, ended = false))
        // 已报过不再报。
        assertNull(scrobbleReportSeconds(300_000L, 600_000L, reported = true, ended = false))
    }

    @Test fun reportsAtNaturalEndAfter30s() {
        assertEquals(45, scrobbleReportSeconds(45_000L, 600_000L, reported = false, ended = true))
        assertNull(scrobbleReportSeconds(29_000L, 600_000L, reported = false, ended = true))
    }
}
