package com.music.zenith.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Temporary A/B switch for the full-screen Spotify Canvas to lyrics/queue transition.
 *
 * false = mode 1 (flight): the sleeve flies to the header as the canvas frame
 *         and crossfades into the static cover.
 * true  = mode 2 (simple): no flight, the static cover fades in at the header.
 *
 * Toggled by long-pressing Home in the nav bar. Delete the losing mode and this file.
 */
object CanvasTransitionExperiment {
    var simple by mutableStateOf(false)
}