/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.emoji

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.osfans.trime.R
import com.osfans.trime.data.emoji.EmojiRepository
import com.osfans.trime.data.emoji.EmojiWithTags
import com.osfans.trime.databinding.FragmentEmojiDetailBinding
import com.osfans.trime.util.toast
import kotlinx.coroutines.launch
import splitties.dimensions.dp
import java.io.File

/**
 * Detail page for one emoji: full preview, primary-tag edit, the complete tag list
 * (tap × to detach; the primary tag is locked), favorite toggle, and two delete
 * flavours — unregister only (file stays; a future sync re-registers it) or delete
 * from disk as well.
 */
class EmojiDetailFragment : Fragment() {
    private lateinit var binding: FragmentEmojiDetailBinding
    private var current: EmojiWithTags? = null

    private val emojiId: Long get() = requireArguments().getLong(ARG_ID)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentEmojiDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.changePrimaryButton.setOnClickListener {
            val item = current ?: return@setOnClickListener
            promptInput(getString(R.string.emoji_set_primary_tag), item.primaryTag.name) { name ->
                runAndReload { EmojiRepository.setPrimaryTag(item.emoji.id, name) }
            }
        }
        binding.addTagButton.setOnClickListener {
            promptInput(getString(R.string.emoji_add_tag)) { name ->
                runAndReload { EmojiRepository.addTagToEmoji(emojiId, name) }
            }
        }
        binding.favoriteCheck.setOnClickListener {
            val checked = binding.favoriteCheck.isChecked
            runAndReload { EmojiRepository.setFavorite(listOf(emojiId), checked) }
        }
        binding.deleteEntryButton.setOnClickListener {
            confirmDelete(R.string.emoji_delete_entry, R.string.emoji_delete_entry_warn, deleteFiles = false)
        }
        binding.deleteDiskButton.setOnClickListener {
            confirmDelete(R.string.emoji_delete_disk, R.string.emoji_delete_disk_warn, deleteFiles = true)
        }
        reload()
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? EmojiManagerActivity)?.supportActionBar?.title =
            current?.primaryTag?.name ?: getString(R.string.emoji_manager)
    }

    private fun reload() {
        viewLifecycleOwner.lifecycleScope.launch {
            val item = EmojiRepository.emojiWithTags(emojiId)
            if (item == null) {
                parentFragmentManager.popBackStack()
                return@launch
            }
            current = item
            render(item)
        }
    }

    private fun render(item: EmojiWithTags) {
        (requireActivity() as? EmojiManagerActivity)?.supportActionBar?.title = item.primaryTag.name
        binding.preview.load(File(item.emoji.filePath), EmojiImaging.loader(requireContext()))
        binding.primaryTagValue.text = item.primaryTag.name
        binding.favoriteCheck.isChecked = item.emoji.isFavorite
        viewLifecycleOwner.lifecycleScope.launch {
            val collection =
                EmojiRepository.collections().firstOrNull { it.id == item.emoji.collectionId }
            binding.infoText.text =
                getString(R.string.emoji_detail_info, collection?.name ?: "?", item.emoji.filePath, item.emoji.useCount)
        }

        binding.tagsContainer.removeAllViews()
        for (tag in item.tags) {
            val isPrimary = tag.id == item.emoji.primaryTagId
            val chip =
                TextView(requireContext()).apply {
                    text = if (isPrimary) getString(R.string.emoji_primary_chip, tag.name) else getString(R.string.emoji_tag_chip, tag.name)
                    textSize = 14f
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    alpha = if (isPrimary) 0.6f else 1f
                    if (!isPrimary) {
                        setOnClickListener {
                            AlertDialog
                                .Builder(requireContext())
                                .setTitle(R.string.emoji_remove_tag)
                                .setMessage(tag.name)
                                .setPositiveButton(R.string.ok) { _, _ ->
                                    runAndReload { EmojiRepository.removeTagFromEmoji(item.emoji.id, tag.id) }
                                }.setNegativeButton(R.string.cancel, null)
                                .show()
                        }
                    }
                }
            binding.tagsContainer.addView(chip)
        }
    }

    private fun confirmDelete(titleRes: Int, warnRes: Int, deleteFiles: Boolean) {
        AlertDialog
            .Builder(requireContext())
            .setTitle(titleRes)
            .setMessage(warnRes)
            .setPositiveButton(R.string.ok) { _, _ ->
                val appCtx = requireContext().applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        EmojiRepository.deleteEmojis(listOf(emojiId), deleteFiles)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        appCtx.toast(appCtx.getString(R.string.emoji_action_failed, e.message ?: "?"))
                    }
                    parentFragmentManager.popBackStack()
                }
            }.setNegativeButton(R.string.cancel, null)
            .show()
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

    private fun runAndReload(block: suspend () -> Unit) {
        val appCtx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                appCtx.toast(appCtx.getString(R.string.emoji_action_failed, e.message ?: "?"))
            }
            reload()
        }
    }

    companion object {
        private const val ARG_ID = "emojiId"

        fun newInstance(emojiId: Long): EmojiDetailFragment = EmojiDetailFragment().apply {
            arguments = Bundle().apply { putLong(ARG_ID, emojiId) }
        }
    }
}
