package com.simposio.taskeer.ui.tasks

import androidx.compose.runtime.saveable.Saver

class SwipeHintState(private var played: Boolean = false) {

    fun shouldPlay(): Boolean = !played

    fun markPlayed() {
        played = true
    }

    companion object {
        val Saver: Saver<SwipeHintState, Boolean> = Saver(
            save = { it.played },
            restore = { SwipeHintState(it) },
        )
    }
}
