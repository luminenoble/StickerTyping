/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.emoji

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import splitties.dimensions.dp

/**
 * Horizontal row of tag chips — the panel's primary retrieval control. Index 0 is the
 * fixed "all" chip; tapping a chip filters the grid to emojis carrying that tag.
 */
class EmojiTagChipAdapter(
    private val allLabel: String,
    private val onSelect: (String?) -> Unit,
) : RecyclerView.Adapter<EmojiTagChipAdapter.Holder>() {
    private var tags: List<String> = emptyList()
    private var selected: String? = null

    class Holder(val text: TextView) : RecyclerView.ViewHolder(text)

    @SuppressLint("NotifyDataSetChanged")
    fun submit(newTags: List<String>, newSelected: String?) {
        tags = newTags
        selected = newSelected
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = tags.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view =
            TextView(parent.context).apply {
                gravity = Gravity.CENTER
                textSize = 14f
                typeface = FontManager.getTypeface("candidate_font")
                setPadding(dp(12), 0, dp(12), 0)
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val tag = if (position == 0) null else tags[position - 1]
        val active = tag == selected
        holder.text.apply {
            text = tag ?: allLabel
            setTextColor(
                ColorManager.getColor(if (active) "hilited_candidate_text_color" else "candidate_text_color"),
            )
            setTypeface(typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
            setOnClickListener { onSelect(tag) }
        }
    }
}
