/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.emoji

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A kaomoji group — the folder-like categorization for kaomojis, mirroring emoji
 * collections. Kaomojis are DB rows (not files), so a group is a pure name; deleting a
 * group leaves its kaomojis ungrouped.
 */
@Entity(
    tableName = "kaomoji_group",
    indices = [Index(value = ["name"], unique = true)],
)
data class KaomojiGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
)
