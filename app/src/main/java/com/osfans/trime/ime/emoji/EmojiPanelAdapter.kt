/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.emoji

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.osfans.trime.data.emoji.EmojiWithTags
import com.osfans.trime.databinding.ItemEmojiManageBinding
import com.osfans.trime.ui.emoji.EmojiImaging
import java.io.File

/**
 * Keyboard-panel grid: same cell as the manager (thumbnail + primary tag below).
 * [cellWidthPx] pins the cell width for horizontal layouts (e.g. the search strip),
 * where match_parent would fill the whole viewport.
 */
class EmojiPanelAdapter(
    private val cellWidthPx: Int? = null,
    private val onSend: (EmojiWithTags) -> Unit,
) : ListAdapter<EmojiWithTags, EmojiPanelAdapter.Holder>(DIFF) {
    class Holder(val binding: ItemEmojiManageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemEmojiManageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        cellWidthPx?.let { binding.root.layoutParams = RecyclerView.LayoutParams(it, RecyclerView.LayoutParams.WRAP_CONTENT) }
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            thumbnail.load(File(item.emoji.filePath), EmojiImaging.loader(root.context))
            primaryTagLabel.text = item.primaryTag.name
            favBadge.isVisible = item.emoji.isFavorite
            selectionOverlay.isVisible = false
            root.setOnClickListener { onSend(item) }
        }
    }

    companion object {
        private val DIFF =
            object : DiffUtil.ItemCallback<EmojiWithTags>() {
                override fun areItemsTheSame(oldItem: EmojiWithTags, newItem: EmojiWithTags) = oldItem.emoji.id == newItem.emoji.id

                override fun areContentsTheSame(oldItem: EmojiWithTags, newItem: EmojiWithTags) = oldItem == newItem
            }
    }
}
