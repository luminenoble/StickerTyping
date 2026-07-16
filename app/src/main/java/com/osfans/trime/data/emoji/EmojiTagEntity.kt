/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.emoji

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A tag. Tags are global (shared by emojis and collections) and unique by name.
 * They are the only retrieval dimension in the emoji layer.
 */
@Entity(
    tableName = "emoji_tag",
    indices = [Index(value = ["name"], unique = true)],
)
data class EmojiTagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
)
