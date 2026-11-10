package com.music.zenith.data.model

/** Key of the Top Picks for You slot on Home. */
const val HOME_SLOT_TOP_PICKS = "slot:top-picks"

/** Key of the Find Your Mood slot on Home. */
const val HOME_SLOT_MOODS = "slot:moods"

private val LISTEN_AGAIN_ALIASES = listOf("Listen again", "Nghe lại", "もう一度聴く", "Dengarkan lagi", "Putar ulang", "다시 듣기")
private val QUICK_PICKS_ALIASES = listOf("Quick picks", "Lựa chọn nhanh", "Chọn nhanh đài phát", "クイック ミックス", "Pilihan cepat", "Pilihan singkat", "빠른 선곡", "빠른 선택")

/**
 * A stable key for a feed shelf. The same shelf keeps its key across app
 * languages where its title is known in several, so a saved order survives.
 */
fun homeShelfKey(title: String): String {
    val clean = title.trim().replace('\n', ' ')
    return when {
        LISTEN_AGAIN_ALIASES.any { it.equals(clean, ignoreCase = true) } -> "shelf:listen-again"
        QUICK_PICKS_ALIASES.any { it.equals(clean, ignoreCase = true) } -> "shelf:quick-picks"
        else -> "shelf:" + clean.lowercase()
    }
}

/** One place on Home: where it sits in [HomeLayout.slots] is its position, [hidden] takes it off the page. */
data class HomeSlot(val key: String, val hidden: Boolean = false)

/**
 * The listener's own arrangement of Home. Empty means "not customised": Home
 * keeps its built-in order. Anything the layout has not seen yet goes after
 * everything it has.
 */
data class HomeLayout(val slots: List<HomeSlot> = emptyList()) {
    val isCustom: Boolean get() = slots.isNotEmpty()

    fun isHidden(key: String): Boolean = slots.any { it.key == key && it.hidden }

    /**
     * [items] in the saved order. Items the layout has not seen go last, in the
     * order they came in; saved keys without an item are skipped. Hidden items
     * are dropped unless [dropHidden] is false.
     */
    fun <T> arrange(items: List<T>, dropHidden: Boolean = true, keyOf: (T) -> String): List<T> {
        if (!isCustom) return items
        val position = HashMap<String, Int>()
        slots.forEachIndexed { index, slot -> position.putIfAbsent(slot.key, index) }
        val known = items.filter { keyOf(it) in position }.sortedBy { position.getValue(keyOf(it)) }
        val fresh = items.filter { keyOf(it) !in position }
        return (known + fresh).filterNot { dropHidden && isHidden(keyOf(it)) }
    }

    fun encode(): String = slots.joinToString("\n") { (if (it.hidden) "1" else "0") + it.key }

    companion object {
        fun decode(text: String?): HomeLayout = HomeLayout(
            text.orEmpty().lineSequence()
                .filter { it.length > 1 }
                .map { HomeSlot(key = it.substring(1), hidden = it[0] == '1') }
                .distinctBy { it.key }
                .toList(),
        )
    }
}