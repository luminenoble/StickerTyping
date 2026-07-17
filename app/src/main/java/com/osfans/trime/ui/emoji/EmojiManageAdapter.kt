/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.emoji

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.osfans.trime.data.emoji.EmojiWithTags
import com.osfans.trime.databinding.ItemEmojiManageBinding
import java.io.File

/** Grid adapter for the manager: thumbnail with the primary tag labelled below. */
class EmojiManageAdapter(
    private val isSelected: (Long) -> Boolean,
    private val onClick: (EmojiWithTags) -> Unit,
    private val onLongClick: (EmojiWithTags) -> Unit,
) : ListAdapter<EmojiWithTags, EmojiManageAdapter.Holder>(DIFF) {
    class Holder(val binding: ItemEmojiManageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(ItemEmojiManageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            thumbnail.load(File(item.emoji.filePath), EmojiImaging.loader(root.context))
            primaryTagLabel.text = item.primaryTag.name
            favBadge.isVisible = item.emoji.isFavorite
            selectionOverlay.isVisible = isSelected(item.emoji.id)
            root.setOnClickListener { onClick(item) }
            root.setOnLongClickListener {
                onLongClick(item)
                true
            }
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
