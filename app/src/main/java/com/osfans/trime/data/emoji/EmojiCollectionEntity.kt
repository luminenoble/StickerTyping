/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.emoji

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An emoji collection. One collection maps to exactly one on-device folder
 * ([folderPath], absolute); syncing a collection walks that folder and registers the
 * emoji files found inside. Collections can carry their own tags via
 * [CollectionTagCrossRef]. Like emojis, collections are searched by tag only — never by
 * name or path.
 *
 * @property nextSeq monotonic per-collection counter used to mint numeric placeholder
 *   primary tags ("1", "2", ...) for newly imported emojis; never reused after deletion.
 */
@Entity(
    tableName = "emoji_collection",
    indices = [Index(value = ["folderPath"], unique = true)],
)
data class EmojiCollectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val folderPath: String,
    val nextSeq: Long = 1,
)
