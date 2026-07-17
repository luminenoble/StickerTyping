/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.emoji

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.osfans.trime.R
import com.osfans.trime.data.emoji.EmojiCollectionEntity
import com.osfans.trime.data.emoji.EmojiRepository
import com.osfans.trime.data.emoji.EmojiWithTags
import com.osfans.trime.databinding.FragmentEmojiCollectionsBinding
import com.osfans.trime.util.toast
import kotlinx.coroutines.launch

/**
 * Collection browser: a tag-searchable grid of emojis (primary tag labelled under each
 * thumbnail), filterable by collection/favorites and sortable by use count. Tap an emoji
 * to edit its tags; long-press for batch selection. Retrieval is tag-only by design —
 * the search box never matches file names.
 */
class EmojiCollectionsFragment : Fragment() {
    private lateinit var binding: FragmentEmojiCollectionsBinding

    private var collections: List<EmojiCollectionEntity> = emptyList()
    private var collectionTags: Map<Long, List<String>> = emptyMap()
    private var allItems: List<EmojiWithTags> = emptyList()
    private var visibleItems: List<EmojiWithTags> = emptyList()

    private var selectedCollectionId: Long? = null
    private var selectionMode = false
    private val selectedIds = mutableSetOf<Long>()

    private val adapter =
        EmojiManageAdapter(
            isSelected = { it in selectedIds },
            onClick = { item ->
                if (selectionMode) toggleSelection(item) else showItemDialog(item)
            },
            onLongClick = { item ->
                if (!selectionMode) {
                    selectionMode = true
                    requireActivity().invalidateOptionsMenu()
                }
                toggleSelection(item)
            },
        )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentEmojiCollectionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.emojiGrid.layoutManager = GridLayoutManager(requireContext(), GRID_SPAN)
        binding.emojiGrid.adapter = adapter
        binding.favOnlyCheck.setOnCheckedChangeListener { _, _ -> applyFilters() }
        binding.sortByUseCheck.setOnCheckedChangeListener { _, _ -> applyFilters() }
        binding.searchInput.doAfterTextChanged { applyFilters() }
        binding.collectionSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedCollectionId = if (position == 0) null else collections.getOrNull(position - 1)?.id
                    applyFilters()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        updateTitle()
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            collections = EmojiRepository.collections()
            collectionTags =
                EmojiRepository.collectionsWithTags().associate { c ->
                    c.collection.id to c.tags.map { it.name }
                }
            allItems = EmojiRepository.allEmojis()
            if (selectedCollectionId != null && collections.none { it.id == selectedCollectionId }) {
                selectedCollectionId = null
            }
            rebuildSpinner()
            applyFilters()
        }
    }

    private fun rebuildSpinner() {
        val labels = listOf(getString(R.string.emoji_all_collections)) + collections.map { it.name }
        binding.collectionSpinner.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels)
        val index = collections.indexOfFirst { it.id == selectedCollectionId }
        binding.collectionSpinner.setSelection(if (index >= 0) index + 1 else 0)
    }

    /** Tag-only filtering over the in-memory snapshot; file names never participate. */
    private fun applyFilters() {
        if (!::binding.isInitialized) return
        val query = binding.searchInput.text.toString().trim()
        var seq = allItems.asSequence()
        selectedCollectionId?.let { c -> seq = seq.filter { it.emoji.collectionId == c } }
        if (binding.favOnlyCheck.isChecked) seq = seq.filter { it.emoji.isFavorite }
        if (query.isNotEmpty()) {
            seq = seq.filter { item -> item.tags.any { it.name.contains(query, ignoreCase = true) } }
        }
        visibleItems =
            if (binding.sortByUseCheck.isChecked) {
                seq.sortedWith(compareByDescending<EmojiWithTags> { it.emoji.useCount }.thenByDescending { it.emoji.lastUsedAt }).toList()
            } else {
                seq.toList()
            }
        selectedIds.retainAll(visibleItems.mapTo(HashSet()) { it.emoji.id })
        adapter.submitList(visibleItems)
        updateCollectionInfo()
        updateTitle()
    }

    private fun updateCollectionInfo() {
        val collection = collections.firstOrNull { it.id == selectedCollectionId }
        binding.collectionInfo.isVisible = collection != null
        if (collection != null) {
            val tags = collectionTags[collection.id].orEmpty()
            binding.collectionInfo.text =
                getString(R.string.emoji_collection_path, collection.folderPath, tags.joinToString(", "))
        }
    }

    private fun updateTitle() {
        (requireActivity() as? EmojiManagerActivity)?.supportActionBar?.title =
            if (selectionMode) {
                getString(R.string.emoji_selection_count, selectedIds.size)
            } else {
                getString(R.string.emoji_manager)
            }
    }

    private fun toggleSelection(item: EmojiWithTags) {
        val id = item.emoji.id
        if (!selectedIds.remove(id)) selectedIds.add(id)
        adapter.notifyItemChanged(visibleItems.indexOfFirst { it.emoji.id == id })
        updateTitle()
    }

    private fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
        adapter.notifyDataSetChanged()
        requireActivity().invalidateOptionsMenu()
        updateTitle()
    }

    // region single-item actions

    private fun showItemDialog(item: EmojiWithTags) {
        val favLabel = getString(if (item.emoji.isFavorite) R.string.emoji_unfavorite else R.string.emoji_favorite)
        val actions =
            arrayOf(
                getString(R.string.emoji_set_primary_tag),
                getString(R.string.emoji_add_tag),
                getString(R.string.emoji_remove_tag),
                favLabel,
            )
        AlertDialog
            .Builder(requireContext())
            .setTitle(item.primaryTag.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 ->
                        promptInput(getString(R.string.emoji_set_primary_tag), item.primaryTag.name) { name ->
                            runAndRefresh { EmojiRepository.setPrimaryTag(item.emoji.id, name) }
                        }
                    1 ->
                        promptInput(getString(R.string.emoji_add_tag)) { name ->
                            runAndRefresh { EmojiRepository.addTagToEmoji(item.emoji.id, name) }
                        }
                    2 -> promptRemoveTag(item)
                    3 -> runAndRefresh { EmojiRepository.setFavorite(listOf(item.emoji.id), !item.emoji.isFavorite) }
                }
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptRemoveTag(item: EmojiWithTags) {
        val removable = item.tags.filter { it.id != item.emoji.primaryTagId }
        if (removable.isEmpty()) {
            requireContext().toast(R.string.emoji_no_removable_tags)
            return
        }
        val checked = BooleanArray(removable.size)
        AlertDialog
            .Builder(requireContext())
            .setTitle(R.string.emoji_remove_tag)
            .setMultiChoiceItems(removable.map { it.name }.toTypedArray(), checked) { _, i, isChecked ->
                checked[i] = isChecked
            }.setPositiveButton(R.string.ok) { _, _ ->
                runAndRefresh {
                    removable.forEachIndexed { i, tag ->
                        if (checked[i]) EmojiRepository.removeTagFromEmoji(item.emoji.id, tag.id)
                    }
                }
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    // endregion

    // region collection actions

    private fun showCollectionDialog(collection: EmojiCollectionEntity) {
        val actions =
            arrayOf(
                getString(R.string.emoji_resync),
                getString(R.string.emoji_rename),
                getString(R.string.emoji_add_collection_tag),
                getString(R.string.emoji_remove_collection_tag),
                getString(R.string.emoji_delete_collection),
            )
        AlertDialog
            .Builder(requireContext())
            .setTitle(collection.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 ->
                        runAndRefresh {
                            val r = EmojiRepository.syncCollection(collection.id)
                            requireContext().toast(getString(R.string.emoji_sync_result, r.added, r.removed, r.total))
                        }
                    1 ->
                        promptInput(getString(R.string.emoji_rename), collection.name) { name ->
                            runAndRefresh { EmojiRepository.renameCollection(collection.id, name) }
                        }
                    2 ->
                        promptInput(getString(R.string.emoji_add_collection_tag)) { name ->
                            runAndRefresh { EmojiRepository.addTagToCollection(collection.id, name) }
                        }
                    3 -> promptRemoveCollectionTag(collection)
                    4 ->
                        AlertDialog
                            .Builder(requireContext())
                            .setTitle(R.string.emoji_delete_collection)
                            .setMessage(collection.folderPath)
                            .setPositiveButton(R.string.ok) { _, _ ->
                                runAndRefresh { EmojiRepository.removeCollection(collection.id) }
                            }.setNegativeButton(R.string.cancel, null)
                            .show()
                }
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptRemoveCollectionTag(collection: EmojiCollectionEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            val tags =
                EmojiRepository.collectionsWithTags().firstOrNull { it.collection.id == collection.id }?.tags.orEmpty()
            if (tags.isEmpty()) {
                requireContext().toast(R.string.emoji_no_removable_tags)
                return@launch
            }
            val checked = BooleanArray(tags.size)
            AlertDialog
                .Builder(requireContext())
                .setTitle(R.string.emoji_remove_collection_tag)
                .setMultiChoiceItems(tags.map { it.name }.toTypedArray(), checked) { _, i, isChecked ->
                    checked[i] = isChecked
                }.setPositiveButton(R.string.ok) { _, _ ->
                    runAndRefresh {
                        tags.forEachIndexed { i, tag ->
                            if (checked[i]) EmojiRepository.removeTagFromCollection(collection.id, tag.id)
                        }
                    }
                }.setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    // endregion

    private val menuProvider =
        object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: android.view.MenuInflater) {
                menu.add(Menu.NONE, MENU_SETTINGS, 0, R.string.emoji_settings).apply {
                    setIcon(R.drawable.ic_baseline_settings_24)
                    setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                menu.add(Menu.NONE, MENU_KAOMOJI, 1, R.string.kaomoji_manager)
                menu.add(Menu.NONE, MENU_COLLECTION_OPS, 2, R.string.emoji_collection_ops)
                menu.add(Menu.NONE, MENU_BATCH_ADD_TAG, 2, R.string.emoji_batch_add_tag)
                menu.add(Menu.NONE, MENU_BATCH_FAV, 3, R.string.emoji_favorite)
                menu.add(Menu.NONE, MENU_BATCH_UNFAV, 4, R.string.emoji_unfavorite)
                menu.add(Menu.NONE, MENU_SELECT_ALL, 5, R.string.emoji_select_all)
                menu.add(Menu.NONE, MENU_EXIT_SELECTION, 6, R.string.emoji_exit_selection)
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(MENU_SETTINGS)?.isVisible = !selectionMode
                menu.findItem(MENU_KAOMOJI)?.isVisible = !selectionMode
                menu.findItem(MENU_COLLECTION_OPS)?.isVisible = !selectionMode && selectedCollectionId != null
                menu.findItem(MENU_BATCH_ADD_TAG)?.isVisible = selectionMode
                menu.findItem(MENU_BATCH_FAV)?.isVisible = selectionMode
                menu.findItem(MENU_BATCH_UNFAV)?.isVisible = selectionMode
                menu.findItem(MENU_SELECT_ALL)?.isVisible = selectionMode
                menu.findItem(MENU_EXIT_SELECTION)?.isVisible = selectionMode
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when (menuItem.itemId) {
                    MENU_SETTINGS -> (requireActivity() as EmojiManagerActivity).showSettings()
                    MENU_KAOMOJI -> (requireActivity() as EmojiManagerActivity).showKaomoji()
                    MENU_COLLECTION_OPS ->
                        collections.firstOrNull { it.id == selectedCollectionId }?.let { showCollectionDialog(it) }
                    MENU_BATCH_ADD_TAG -> {
                        val ids = selectedIds.toList()
                        if (ids.isNotEmpty()) {
                            promptInput(getString(R.string.emoji_batch_add_tag)) { name ->
                                runAndRefresh { ids.forEach { EmojiRepository.addTagToEmoji(it, name) } }
                            }
                        }
                    }
                    MENU_BATCH_FAV -> runAndRefresh { EmojiRepository.setFavorite(selectedIds.toList(), true) }
                    MENU_BATCH_UNFAV -> runAndRefresh { EmojiRepository.setFavorite(selectedIds.toList(), false) }
                    MENU_SELECT_ALL -> {
                        selectedIds.addAll(visibleItems.map { it.emoji.id })
                        adapter.notifyDataSetChanged()
                        updateTitle()
                    }
                    MENU_EXIT_SELECTION -> exitSelection()
                    else -> return false
                }
                return true
            }
        }

    private fun promptInput(title: String, initial: String = "", onOk: (String) -> Unit) {
        val edit = EditText(requireContext()).apply { setText(initial) }
        AlertDialog
            .Builder(requireContext())
            .setTitle(title)
            .setView(edit)
            .setPositiveButton(R.string.ok) { _, _ ->
                val text = edit.text.toString().trim()
                if (text.isNotEmpty()) onOk(text)
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun runAndRefresh(block: suspend () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { block() }
                .onFailure { requireContext().toast(getString(R.string.emoji_action_failed, it.message ?: "?")) }
            refresh()
        }
    }

    companion object {
        private const val GRID_SPAN = 4
        private const val MENU_SETTINGS = 101
        private const val MENU_KAOMOJI = 108
        private const val MENU_COLLECTION_OPS = 102
        private const val MENU_BATCH_ADD_TAG = 103
        private const val MENU_BATCH_FAV = 104
        private const val MENU_BATCH_UNFAV = 105
        private const val MENU_SELECT_ALL = 106
        private const val MENU_EXIT_SELECTION = 107
    }
}
