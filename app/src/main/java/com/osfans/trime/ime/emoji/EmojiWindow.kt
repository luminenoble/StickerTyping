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
import com.osfans.trime.data.emoji.EmojiCollectionEntity
import com.osfans.trime.data.emoji.EmojiRepository
import com.osfans.trime.data.emoji.EmojiWithTags
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.util.toast
import kotlinx.coroutines.launch
import org.kodein.di.instance
import splitties.dimensions.dp

/**
 * The emoji panel: a tag-filterable grid of the user's own emoji collections, opened
 * from the toolbar's emoji button (`command: emoji_window`). Bar = collection cycle +
 * favorites / use-count toggles + back to keyboard; content = tag chip row + grid.
 * Tapping a cell sends the image as rich content (no fallback) and bumps its use count.
 * Retrieval is tag-only: chips carry every tag in the current scope, file names are
 * never consulted.
 */
class EmojiWindow : BoardWindow.BarBoardWindow() {
    override val showTitle = false

    private val service: TrimeInputMethodService by di.instance()
    private val theme: Theme by di.instance()
    private val windowManager: BoardWindowManager by di.instance()

    private val sender by lazy { EmojiContentSender(service) }

    private var collections: List<EmojiCollectionEntity> = emptyList()
    private var allItems: List<EmojiWithTags> = emptyList()

    /** -1 = all collections, otherwise an index into [collections]. */
    private var collectionIndex = -1
    private var favOnly = false
    private var sortByUse = false
    private var selectedTag: String? = null

    private lateinit var titleUi: EmojiTitleUi
    private lateinit var chipsView: RecyclerView
    private lateinit var gridView: RecyclerView

    private val panelAdapter =
        EmojiPanelAdapter { item ->
            if (sender.send(item.emoji)) {
                item.emoji.let { sent ->
                    service.lifecycleScope.launch { EmojiRepository.markUsed(sent.id) }
                }
            } else {
                service.toast(R.string.emoji_send_failed)
            }
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
            collectionLabel.setOnClickListener { cycleCollection() }
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
        chipsView =
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
                adapter = chipAdapter
            }
        gridView =
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
            collections = EmojiRepository.collections()
            allItems = EmojiRepository.allEmojis()
            if (collectionIndex >= collections.size) collectionIndex = -1
            applyFilters()
        }
    }

    override fun onDetached() {}

    private fun currentCollection(): EmojiCollectionEntity? = collections.getOrNull(collectionIndex)

    private fun cycleCollection() {
        if (collections.isEmpty()) return
        collectionIndex = if (collectionIndex + 1 >= collections.size) -1 else collectionIndex + 1
        applyFilters()
    }

    private fun applyFilters() {
        var scope = allItems.asSequence()
        currentCollection()?.let { c -> scope = scope.filter { it.emoji.collectionId == c.id } }
        if (favOnly) scope = scope.filter { it.emoji.isFavorite }
        val scoped = scope.toList()

        val tags = scoped.flatMap { item -> item.tags.map { it.name } }.distinct().sorted()
        if (selectedTag != null && selectedTag !in tags) selectedTag = null
        chipAdapter.submit(tags, selectedTag)

        var items = scoped
        selectedTag?.let { t -> items = items.filter { item -> item.tags.any { it.name == t } } }
        if (sortByUse) {
            items =
                items.sortedWith(
                    compareByDescending<EmojiWithTags> { it.emoji.useCount }
                        .thenByDescending { it.emoji.lastUsedAt },
                )
        }
        panelAdapter.submitList(items)

        titleUi.collectionLabel.text =
            currentCollection()?.name ?: context.getString(R.string.emoji_all_collections)
        titleUi.setFavActive(favOnly)
        titleUi.setSortActive(sortByUse)
    }

    companion object {
        private const val GRID_SPAN = 5
    }
}
