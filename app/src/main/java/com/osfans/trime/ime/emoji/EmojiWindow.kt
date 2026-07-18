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

    /** null = all collections, otherwise a collection name selected via the chips. */
    private var selectedCollectionName: String? = null
    private var favOnly = false
    private var sortByUse = false

    private lateinit var titleUi: EmojiTitleUi
    private lateinit var chipsView: RecyclerView
    private lateinit var gridView: RecyclerView

    private val panelAdapter =
        EmojiPanelAdapter(onLongPress = { item ->
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

    /** Chips carry collection names — the panel's grouping dimension. */
    private val chipAdapter by lazy {
        EmojiTagChipAdapter(context.getString(R.string.emoji_tag_all)) { name ->
            selectedCollectionName = name
            applyFilters()
        }
    }

    override fun onCreateView(): View {
        titleUi = EmojiTitleUi(context, theme)
        titleUi.apply {
            collectionLabel.text = context.getString(R.string.emoji_panel_title)
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
            applyFilters()
        }
    }

    override fun onDetached() {}

    private fun applyFilters() {
        val names = collections.map { it.name }
        if (selectedCollectionName != null && selectedCollectionName !in names) {
            selectedCollectionName = null
        }
        chipAdapter.submit(names, selectedCollectionName)

        var seq = allItems.asSequence()
        selectedCollectionName?.let { name ->
            val ids = collections.filter { it.name == name }.mapTo(HashSet()) { it.id }
            seq = seq.filter { it.emoji.collectionId in ids }
        }
        if (favOnly) seq = seq.filter { it.emoji.isFavorite }
        var items = seq.toList()
        if (sortByUse) {
            items =
                items.sortedWith(
                    compareByDescending<EmojiWithTags> { it.emoji.useCount }
                        .thenByDescending { it.emoji.lastUsedAt },
                )
        }
        panelAdapter.submitList(items)

        titleUi.setFavActive(favOnly)
        titleUi.setSortActive(sortByUse)
    }

    companion object {
        private const val GRID_SPAN = 5
    }
}
