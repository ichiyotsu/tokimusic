package com.tokimusic.app.data.sync

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncBundleCodecTest {

    private fun section(stamp: Long, data: JSONObject) = SyncSection(stamp, data)

    @Test
    fun encodeDecodeRoundTrip() {
        val bundle = SyncBundle(
            sections = mapOf(
                SyncBundleCodec.SECTION_SETTINGS to section(100L, JSONObject().put("light_mode", true)),
                SyncBundleCodec.SECTION_LEVEL to section(200L, JSONObject().put("total", 1234)),
            ),
        )
        val decoded = SyncBundleCodec.decode(SyncBundleCodec.encode(bundle))!!
        assertEquals(1, decoded.version)
        assertEquals(100L, decoded.sections["settings"]!!.updatedAt)
        assertTrue(decoded.sections["settings"]!!.data.getBoolean("light_mode"))
        assertEquals(1234, decoded.sections["level"]!!.data.getInt("total"))
    }

    @Test
    fun decodeRejectsGarbageAndFutureVersions() {
        assertNull(SyncBundleCodec.decode(null))
        assertNull(SyncBundleCodec.decode(""))
        assertNull(SyncBundleCodec.decode("not json"))
        assertNull(SyncBundleCodec.decode("""{"version":99,"sections":{}}"""))
    }

    @Test
    fun mergeTakesNewerSectionPerStamp() {
        val local = SyncBundle(
            sections = mapOf(
                "settings" to section(500L, JSONObject().put("v", "local")),
                "level" to section(100L, JSONObject().put("total", 1)),
            ),
        )
        val remote = SyncBundle(
            sections = mapOf(
                "settings" to section(300L, JSONObject().put("v", "remote")),
                "level" to section(900L, JSONObject().put("total", 2)),
            ),
        )
        val merged = SyncBundleCodec.merge(local, remote)
        assertEquals("local", merged.sections["settings"]!!.data.getString("v"))
        assertEquals(2, merged.sections["level"]!!.data.getInt("total"))
    }

    @Test
    fun mergePlaylistsUnionsEntries() {
        fun item(source: String, id: String, name: String) = JSONObject()
            .put("source", source).put("playlistId", id).put("name", name)
        val local = SyncBundle(
            sections = mapOf(
                "playlists" to section(100L, JSONObject().put("items", org.json.JSONArray().put(item("NETEASE", "1", "甲")))),
            ),
        )
        val remote = SyncBundle(
            sections = mapOf(
                "playlists" to section(200L, JSONObject().put("items", org.json.JSONArray().put(item("QQ", "2", "乙")))),
            ),
        )
        val merged = SyncBundleCodec.merge(local, remote)
        val items = merged.sections["playlists"]!!.data.getJSONArray("items")
        assertEquals(2, items.length())
        assertEquals(200L, merged.sections["playlists"]!!.updatedAt)
    }

    @Test
    fun mergeLikedUnionsIdentities() {
        val local = SyncBundle(
            sections = mapOf(
                "liked" to section(1L, JSONObject().put("netease", org.json.JSONArray().put("11").put("22"))),
            ),
        )
        val remote = SyncBundle(
            sections = mapOf(
                "liked" to section(2L, JSONObject().put("netease", org.json.JSONArray().put("22").put("33"))),
            ),
        )
        val merged = SyncBundleCodec.merge(local, remote)
        val ids = merged.sections["liked"]!!.data.getJSONArray("netease")
        assertEquals(3, ids.length())
    }
}
