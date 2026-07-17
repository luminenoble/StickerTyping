/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.emoji

import android.view.View
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.R
import com.osfans.trime.data.emoji.EmojiRepository
import com.osfans.trime.data.emoji.KaomojiWithTags
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import kotlinx.coroutines.launch
import org.kodein.di.instance
import splitties.dimensions.dp

/**
 * The kaomoji panel: same structure as [EmojiWindow] but text-only cells and plain
 * commitText output (no rich-content compatibility concerns). Tag chips are the
 * retrieval control; the kaomoji text itself is never searched.
 */
class KaomojiWindow : BoardWindow.BarBoardWindow() {
    override val showTitle = false

    private val service: TrimeInputMethodService by di.instance()
    private val theme: Theme by di.instance()
    private val windowManager: BoardWindowManager by di.instance()

    private var allItems: List<KaomojiWithTags> = emptyList()
    private var favOnly = false
    private var sortByUse = false
    private var selectedTag: String? = null

    private lateinit var titleUi: EmojiTitleUi

    private val panelAdapter =
        KaomojiPanelAdapter { item ->
            service.commitText(item.kaomoji.text)
            service.lifecycleScope.launch { EmojiRepository.markKaomojiUsed(item.kaomoji.id) }
        }

    private val chipAdapter by lazy {
        EmojiTagChipAdapter(context.getString(R.string.emoji_tag_all)) { tag ->
            selectedTag = tag
            applyFilters()
        }
    }

    override fun onCreateView(): View {
        titleUi = EmojiTitleUi(context, theme)
        titleUi.apply {
            collectionLabel.text = context.getString(R.string.kaomoji_manager)
            favButton.setOnClickListener {
                favOnly = !favOnly
                applyFilters()
            }
            sortLabel.text = context.getString(R.string.emoji_sort_by_use)
            sortLabel.setOnClickListener {
                sortByUse = !sortByUse
                applyFilters()
            }
            backButton.setOnClickListener { windowManager.attachWindow(KeyboardWindow) }
        }
        val chipsView =
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
                adapter = chipAdapter
            }
        val gridView =
            RecyclerView(context).apply {
                layoutManager = GridLayoutManager(context, GRID_SPAN)
                adapter = panelAdapter
            }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(chipsView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32)))
            addView(gridView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    override fun onCreateBarView(): View = titleUi.root

    override fun onAttached() {
        service.lifecycleScope.launch {
            allItems = EmojiRepository.allKaomoji()
            applyFilters()
        }
    }

    override fun onDetached() {}

    private fun applyFilters() {
        var scope = allItems.asSequence()
        if (favOnly) scope = scope.filter { it.kaomoji.isFavorite }
        val scoped = scope.toList()

        val tags = scoped.flatMap { item -> item.tags.map { it.name } }.distinct().sorted()
        if (selectedTag != null && selectedTag !in tags) selectedTag = null
        chipAdapter.submit(tags, selectedTag)

        var items = scoped
        selectedTag?.let { t -> items = items.filter { item -> item.tags.any { it.name == t } } }
        if (sortByUse) {
            items =
                items.sortedWith(
                    compareByDescending<KaomojiWithTags> { it.kaomoji.useCount }
                        .thenByDescending { it.kaomoji.lastUsedAt },
                )
        }
        panelAdapter.submitList(items)

        titleUi.setFavActive(favOnly)
        titleUi.setSortActive(sortByUse)
    }

    companion object {
        private const val GRID_SPAN = 3
    }
}
