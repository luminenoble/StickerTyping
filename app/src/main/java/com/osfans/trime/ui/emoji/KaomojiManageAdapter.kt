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
import com.osfans.trime.data.emoji.KaomojiWithTags
import com.osfans.trime.databinding.ItemKaomojiBinding

/** Manager grid for kaomojis: the emoticon text with its primary tag below. */
class KaomojiManageAdapter(
    private val onClick: (KaomojiWithTags) -> Unit,
) : ListAdapter<KaomojiWithTags, KaomojiManageAdapter.Holder>(DIFF) {
    class Holder(val binding: ItemKaomojiBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(ItemKaomojiBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            kaomojiText.text = item.kaomoji.text
            primaryTagLabel.text = item.primaryTag.name
            favBadge.isVisible = item.kaomoji.isFavorite
            root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF =
            object : DiffUtil.ItemCallback<KaomojiWithTags>() {
                override fun areItemsTheSame(oldItem: KaomojiWithTags, newItem: KaomojiWithTags) = oldItem.kaomoji.id == newItem.kaomoji.id

                override fun areContentsTheSame(oldItem: KaomojiWithTags, newItem: KaomojiWithTags) = oldItem == newItem
            }
    }
}
