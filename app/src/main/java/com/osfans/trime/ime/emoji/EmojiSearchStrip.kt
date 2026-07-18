/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.emoji

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.R
import com.osfans.trime.data.emoji.EmojiRepository
import com.osfans.trime.data.emoji.EmojiWithTags
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.ime.bar.ui.ToolButton
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.util.toast
import kotlinx.coroutines.launch
import splitties.dimensions.dp

/**
 * The emoji search strip, inserted between the input bar and the keyboard. While
 * active, everything the user "commits" with the normal keyboard (a picked candidate
 * word or a raw letter) is appended to the tag query instead of being sent to the app —
 * so pinyin → candidate → Chinese tag search comes for free from rime. Results render
 * as a horizontal row of emoji cells; tapping one sends it as rich content. The strip's
 * own ⌫ pops the last query character; ✕ leaves the mode.
 */
class EmojiSearchStrip(
    private val ctx: Context,
    private val service: TrimeInputMethodService,
) {
    var active: Boolean = false
        private set

    private val query = StringBuilder()
    private var allItems: List<EmojiWithTags> = emptyList()

    private val sender by lazy { EmojiContentSender(service) }

    private val queryLabel =
        TextView(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            textSize = 14f
            typeface = FontManager.getTypeface("candidate_font")
            setPadding(dp(12), 0, dp(8), 0)
            setSingleLine()
        }

    private val backspaceButton = ToolButton(ctx, R.drawable.ic_baseline_arrow_left_24)
    private val closeButton = ToolButton(ctx, R.drawable.ic_baseline_deselect_24)

    private val resultsAdapter =
        EmojiPanelAdapter(cellWidthPx = ctx.dp(84), onLongPress = { item ->
            if (sender.shareToCurrentApp(item.emoji)) {
                service.lifecycleScope.launch { EmojiRepository.markUsed(item.emoji.id) }
            } else {
                service.toast(R.string.emoji_send_failed)
            }
        }) { item ->
            when (sender.send(item.emoji)) {
                EmojiContentSender.Result.COMMITTED ->
                    service.lifecycleScope.launch { EmojiRepository.markUsed(item.emoji.id) }
                EmojiContentSender.Result.COPIED -> {
                    service.lifecycleScope.launch { EmojiRepository.markUsed(item.emoji.id) }
                    service.toast(R.string.emoji_copied_to_clipboard)
                }
                EmojiContentSender.Result.FAILED -> service.toast(R.string.emoji_send_failed)
            }
        }

    private val resultsView =
        RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx, RecyclerView.HORIZONTAL, false)
            adapter = resultsAdapter
        }

    val root: LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            isVisible = false
            addView(
                LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(queryLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                    addView(backspaceButton, LinearLayout.LayoutParams(dp(40), dp(28)))
                    addView(closeButton, LinearLayout.LayoutParams(dp(40), dp(28)))
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)),
            )
            addView(
                resultsView,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(112)),
            )
        }

    init {
        backspaceButton.setOnClickListener {
            if (query.isNotEmpty()) {
                query.deleteCharAt(query.length - 1)
                refresh()
            }
        }
        backspaceButton.setOnLongClickListener {
            query.setLength(0)
            refresh()
            true
        }
        closeButton.setOnClickListener { deactivate() }
        EmojiSearchState.strip = this
    }

    fun toggle() {
        if (active) deactivate() else activate()
    }

    fun activate() {
        if (active) return
        active = true
        query.setLength(0)
        root.isVisible = true
        service.lifecycleScope.launch {
            allItems = EmojiRepository.allEmojis()
            refresh()
        }
    }

    fun deactivate() {
        if (!active) return
        active = false
        query.setLength(0)
        root.isVisible = false
    }

    /** Consume committed text as query input while the mode is active. */
    fun intercept(text: String): Boolean {
        if (!active) return false
        query.append(text)
        refresh()
        return true
    }

    private fun refresh() {
        val q = query.toString().trim()
        queryLabel.text =
            if (q.isEmpty()) ctx.getString(R.string.emoji_search_strip_hint) else q
        queryLabel.setTextColor(ColorManager.getColor("candidate_text_color"))
        val results =
            if (q.isEmpty()) {
                allItems.sortedWith(
                    compareByDescending<EmojiWithTags> { it.emoji.useCount }
                        .thenByDescending { it.emoji.lastUsedAt },
                )
            } else {
                allItems.filter { item -> item.tags.any { it.name.contains(q, ignoreCase = true) } }
            }
        resultsAdapter.submitList(results)
    }
}
