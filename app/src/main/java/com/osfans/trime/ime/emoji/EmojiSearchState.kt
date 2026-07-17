/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.emoji

/**
 * Global handle to the emoji search mode. The strip view (owned by InputView)
 * registers itself here; the IME service consults [intercept] inside its commitText
 * funnel, and the toolbar command toggles the mode. When the mode is active, committed
 * text (candidate picks or direct keys) becomes the tag query instead of going to the
 * target app.
 */
object EmojiSearchState {
    @Volatile
    var strip: EmojiSearchStrip? = null

    val active: Boolean get() = strip?.active == true

    /** @return true when [text] was consumed as query input */
    fun intercept(text: String): Boolean {
        val s = strip ?: return false
        return s.intercept(text)
    }

    fun toggle() {
        strip?.toggle()
    }

    fun deactivate() {
        strip?.deactivate()
    }
}
