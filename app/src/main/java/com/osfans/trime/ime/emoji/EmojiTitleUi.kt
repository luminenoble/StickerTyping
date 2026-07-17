/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.emoji

import android.content.Context
import android.view.Gravity
import android.widget.TextView
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.ui.ToolButton
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.wrapContent

/**
 * Bar of the emoji panel: collection cycle label on the left, favorites filter /
 * use-count sort toggles and a back-to-keyboard button on the right.
 */
class EmojiTitleUi(override val ctx: Context, theme: Theme) : Ui {
    private val size = theme.generalStyle.run { candidateViewHeight + commentHeight }

    val collectionLabel =
        TextView(ctx).apply {
            gravity = Gravity.CENTER
            textSize = 15f
            typeface = FontManager.getTypeface("candidate_font")
            setTextColor(ColorManager.getColor("key_text_color"))
            setPadding(dp(12), 0, dp(12), 0)
        }

    val favButton = ToolButton(ctx, R.drawable.ic_baseline_star_24)

    val sortLabel =
        TextView(ctx).apply {
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = FontManager.getTypeface("candidate_font")
            setPadding(dp(8), 0, dp(8), 0)
        }

    val backButton = ToolButton(ctx, R.drawable.ic_baseline_keyboard_24)

    fun setFavActive(active: Boolean) {
        favButton.alpha = if (active) 1f else 0.4f
    }

    fun setSortActive(active: Boolean) {
        sortLabel.setTextColor(
            ColorManager.getColor(if (active) "hilited_candidate_text_color" else "candidate_text_color"),
        )
    }

    override val root =
        constraintLayout {
            add(
                collectionLabel,
                lParams(wrapContent, dp(size)) {
                    startOfParent()
                    centerVertically()
                },
            )
            add(
                backButton,
                lParams(dp(size), dp(size)) {
                    endOfParent()
                    centerVertically()
                },
            )
            add(
                favButton,
                lParams(dp(size), dp(size)) {
                    before(backButton)
                    centerVertically()
                },
            )
            add(
                sortLabel,
                lParams(wrapContent, dp(size)) {
                    before(favButton)
                    centerVertically()
                },
            )
        }
}
