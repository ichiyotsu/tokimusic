package com.tokimusic.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** 0.1.13 第 24 项：关注歌手 id 集合的增删（纯函数）。 */
class FollowedArtistsTest {

    @Test
    fun toggleAddsAndRemoves() {
        val empty = emptySet<Long>()
        assertEquals(setOf(42L), toggleFollowedIds(empty, 42L))
        assertEquals(emptySet<Long>(), toggleFollowedIds(setOf(42L), 42L))
        assertEquals(setOf(1L, 2L), toggleFollowedIds(setOf(1L), 2L))
        assertEquals(setOf(2L), toggleFollowedIds(setOf(1L, 2L), 1L))
    }
}
