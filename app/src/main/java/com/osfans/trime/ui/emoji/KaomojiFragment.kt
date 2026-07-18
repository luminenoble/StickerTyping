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
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.osfans.trime.R
import com.osfans.trime.data.emoji.EmojiRepository
import com.osfans.trime.data.emoji.KaomojiGroupEntity
import com.osfans.trime.data.emoji.KaomojiWithTags
import com.osfans.trime.databinding.FragmentKaomojiBinding
import com.osfans.trime.util.toast
import kotlinx.coroutines.launch
import splitties.dimensions.dp

/**
 * Kaomoji manager: tag-searchable grid, add/edit/delete, mandatory primary tag on
 * every entry. Same tag-only retrieval rule as emojis.
 */
class KaomojiFragment : Fragment() {
    private lateinit var binding: FragmentKaomojiBinding

    private var allItems: List<KaomojiWithTags> = emptyList()
    private var groups: List<KaomojiGroupEntity> = emptyList()

    /** null = all; 0 = ungrouped; otherwise a group id. */
    private var selectedGroupId: Long? = null

    private val adapter = KaomojiManageAdapter { showItemDialog(it) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentKaomojiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.kaomojiGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.kaomojiGrid.adapter = adapter
        binding.favOnlyCheck.setOnCheckedChangeListener { _, _ -> applyFilters() }
        binding.sortByUseCheck.setOnCheckedChangeListener { _, _ -> applyFilters() }
        binding.searchInput.doAfterTextChanged { applyFilters() }
        binding.groupSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedGroupId =
                        when (position) {
                            0 -> null
                            1 -> 0L
                            else -> groups.getOrNull(position - 2)?.id
                        }
                    requireActivity().invalidateOptionsMenu()
                    applyFilters()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? EmojiManagerActivity)?.supportActionBar?.setTitle(R.string.kaomoji_manager)
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            allItems = EmojiRepository.allKaomoji()
            groups = EmojiRepository.kaomojiGroups()
            if (selectedGroupId != null && selectedGroupId != 0L && groups.none { it.id == selectedGroupId }) {
                selectedGroupId = null
            }
            rebuildGroupSpinner()
            applyFilters()
        }
    }

    private fun rebuildGroupSpinner() {
        val labels =
            listOf(getString(R.string.emoji_tag_all), getString(R.string.kaomoji_ungrouped)) + groups.map { it.name }
        binding.groupSpinner.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels)
        val position =
            when (selectedGroupId) {
                null -> 0
                0L -> 1
                else -> groups.indexOfFirst { it.id == selectedGroupId }.let { if (it >= 0) it + 2 else 0 }
            }
        binding.groupSpinner.setSelection(position)
    }

    /** Tag-only filtering — the kaomoji text itself is intentionally not searched. */
    private fun applyFilters() {
        if (!::binding.isInitialized) return
        val query = binding.searchInput.text.toString().trim()
        var seq = allItems.asSequence()
        when (selectedGroupId) {
            null -> {}
            0L -> seq = seq.filter { it.kaomoji.groupId == null }
            else -> seq = seq.filter { it.kaomoji.groupId == selectedGroupId }
        }
        if (binding.favOnlyCheck.isChecked) seq = seq.filter { it.kaomoji.isFavorite }
        if (query.isNotEmpty()) {
            seq = seq.filter { item -> item.tags.any { it.name.contains(query, ignoreCase = true) } }
        }
        val items =
            if (binding.sortByUseCheck.isChecked) {
                seq.sortedWith(
                    compareByDescending<KaomojiWithTags> { it.kaomoji.useCount }
                        .thenByDescending { it.kaomoji.lastUsedAt },
                ).toList()
            } else {
                seq.toList()
            }
        adapter.submitList(items)
    }

    private fun showItemDialog(item: KaomojiWithTags) {
        val favLabel = getString(if (item.kaomoji.isFavorite) R.string.emoji_unfavorite else R.string.emoji_favorite)
        val actions =
            arrayOf(
                getString(R.string.emoji_set_primary_tag),
                getString(R.string.emoji_add_tag),
                getString(R.string.emoji_remove_tag),
                favLabel,
                getString(R.string.kaomoji_set_group),
                getString(R.string.kaomoji_edit),
                getString(R.string.kaomoji_delete),
            )
        AlertDialog
            .Builder(requireContext())
            .setTitle(item.kaomoji.text)
            .setItems(actions) { _, which ->
                when (which) {
                    0 ->
                        promptInput(getString(R.string.emoji_set_primary_tag), item.primaryTag.name) { name ->
                            runAndRefresh { EmojiRepository.setKaomojiPrimaryTag(item.kaomoji.id, name) }
                        }
                    1 ->
                        promptInput(getString(R.string.emoji_add_tag)) { name ->
                            runAndRefresh { EmojiRepository.addTagToKaomoji(item.kaomoji.id, name) }
                        }
                    2 -> promptRemoveTag(item)
                    3 -> runAndRefresh { EmojiRepository.setKaomojiFavorite(listOf(item.kaomoji.id), !item.kaomoji.isFavorite) }
                    4 -> promptSetGroup(item)
                    5 ->
                        promptInput(getString(R.string.kaomoji_edit), item.kaomoji.text) { text ->
                            runAndRefresh { EmojiRepository.updateKaomojiText(item.kaomoji.id, text) }
                        }
                    6 ->
                        AlertDialog
                            .Builder(requireContext())
                            .setTitle(R.string.kaomoji_delete)
                            .setMessage(item.kaomoji.text)
                            .setPositiveButton(R.string.ok) { _, _ ->
                                runAndRefresh { EmojiRepository.deleteKaomoji(listOf(item.kaomoji.id)) }
                            }.setNegativeButton(R.string.cancel, null)
                            .show()
                }
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptRemoveTag(item: KaomojiWithTags) {
        val removable = item.tags.filter { it.id != item.kaomoji.primaryTagId }
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
                        if (checked[i]) EmojiRepository.removeTagFromKaomoji(item.kaomoji.id, tag.id)
                    }
                }
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptSetGroup(item: KaomojiWithTags) {
        val options =
            listOf(getString(R.string.kaomoji_ungrouped)) + groups.map { it.name } + getString(R.string.kaomoji_new_group)
        AlertDialog
            .Builder(requireContext())
            .setTitle(R.string.kaomoji_set_group)
            .setItems(options.toTypedArray()) { _, which ->
                when {
                    which == 0 -> runAndRefresh { EmojiRepository.setKaomojiGroup(listOf(item.kaomoji.id), null) }
                    which <= groups.size ->
                        runAndRefresh { EmojiRepository.setKaomojiGroup(listOf(item.kaomoji.id), groups[which - 1].name) }
                    else ->
                        promptInput(getString(R.string.kaomoji_new_group)) { name ->
                            runAndRefresh { EmojiRepository.setKaomojiGroup(listOf(item.kaomoji.id), name) }
                        }
                }
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun currentGroup(): KaomojiGroupEntity? = groups.firstOrNull { it.id == selectedGroupId }

    private fun showGroupDialog(group: KaomojiGroupEntity) {
        val actions = arrayOf(getString(R.string.emoji_rename), getString(R.string.kaomoji_delete_group))
        AlertDialog
            .Builder(requireContext())
            .setTitle(group.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 ->
                        promptInput(getString(R.string.emoji_rename), group.name) { name ->
                            runAndRefresh { EmojiRepository.renameKaomojiGroup(group.id, name) }
                        }
                    1 ->
                        AlertDialog
                            .Builder(requireContext())
                            .setTitle(R.string.kaomoji_delete_group)
                            .setMessage(R.string.kaomoji_delete_group_warn)
                            .setPositiveButton(R.string.ok) { _, _ ->
                                runAndRefresh { EmojiRepository.deleteKaomojiGroup(group.id) }
                            }.setNegativeButton(R.string.cancel, null)
                            .show()
                }
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptAdd() {
        val context = requireContext()
        val textInput = EditText(context).apply { hint = getString(R.string.kaomoji_content) }
        val tagInput = EditText(context).apply { hint = getString(R.string.emoji_set_primary_tag) }
        val layout =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(8), dp(16), 0)
                addView(textInput)
                addView(tagInput)
            }
        AlertDialog
            .Builder(context)
            .setTitle(R.string.kaomoji_add)
            .setView(layout)
            .setPositiveButton(R.string.ok) { _, _ ->
                val text = textInput.text.toString().trim()
                val tag = tagInput.text.toString().trim()
                if (text.isEmpty() || tag.isEmpty()) {
                    context.toast(R.string.emoji_primary_tag_required)
                    return@setPositiveButton
                }
                runAndRefresh { EmojiRepository.addKaomoji(text, tag, currentGroup()?.name) }
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    private val menuProvider =
        object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: android.view.MenuInflater) {
                menu.add(Menu.NONE, MENU_ADD, 0, R.string.kaomoji_add).apply {
                    setIcon(R.drawable.ic_baseline_add_24)
                    setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                menu.add(Menu.NONE, MENU_GROUP_OPS, 1, R.string.kaomoji_group_ops)
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(MENU_GROUP_OPS)?.isVisible = currentGroup() != null
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
                MENU_ADD -> {
                    promptAdd()
                    true
                }
                MENU_GROUP_OPS -> {
                    currentGroup()?.let { showGroupDialog(it) }
                    true
                }
                else -> false
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
        val appCtx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                appCtx.toast(appCtx.getString(R.string.emoji_action_failed, e.message ?: "?"))
            }
            refresh()
        }
    }

    companion object {
        private const val MENU_ADD = 201
        private const val MENU_GROUP_OPS = 202
    }
}
