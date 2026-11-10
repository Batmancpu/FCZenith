package com.music.zenith

import com.music.zenith.data.model.HOME_SLOT_MOODS
import com.music.zenith.data.model.HOME_SLOT_TOP_PICKS
import com.music.zenith.data.model.HomeLayout
import com.music.zenith.data.model.HomeSlot
import com.music.zenith.data.model.homeShelfKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    private val items = listOf("a", "b", "c", "d")

    @Test
    fun emptyLayoutLeavesTheOrderAlone() {
        val layout = HomeLayout()
        assertFalse(layout.isCustom)
        assertEquals(items, layout.arrange(items) { it })
    }

    @Test
    fun savedOrderWinsAndUnseenItemsGoLast() {
        val layout = HomeLayout(listOf(HomeSlot("c"), HomeSlot("a")))
        assertEquals(listOf("c", "a", "b", "d"), layout.arrange(items) { it })
    }

    @Test
    fun hiddenItemsAreDropped() {
        val layout = HomeLayout(listOf(HomeSlot("b", hidden = true), HomeSlot("a")))
        assertEquals(listOf("a", "c", "d"), layout.arrange(items) { it })
        assertEquals(listOf("b", "a", "c", "d"), layout.arrange(items, dropHidden = false) { it })
    }

    @Test
    fun savedKeysWithoutAnItemAreSkipped() {
        val layout = HomeLayout(listOf(HomeSlot("gone"), HomeSlot("d")))
        assertEquals(listOf("d", "a", "b", "c"), layout.arrange(items) { it })
    }

    @Test
    fun encodeAndDecodeRoundTrip() {
        val layout = HomeLayout(
            listOf(
                HomeSlot(HOME_SLOT_TOP_PICKS),
                HomeSlot(HOME_SLOT_MOODS, hidden = true),
                HomeSlot(homeShelfKey("Albums for you")),
            ),
        )
        val decoded = HomeLayout.decode(layout.encode())
        assertEquals(layout, decoded)
        assertTrue(decoded.isHidden(HOME_SLOT_MOODS))
    }

    @Test
    fun decodeOfNothingIsNotCustom() {
        assertFalse(HomeLayout.decode(null).isCustom)
        assertFalse(HomeLayout.decode("").isCustom)
    }

    @Test
    fun knownShelvesShareAKeyAcrossLanguages() {
        assertEquals(homeShelfKey("Listen again"), homeShelfKey("  nghe lại "))
        assertEquals(homeShelfKey("Quick picks"), homeShelfKey("クイック ミックス"))
    }
}