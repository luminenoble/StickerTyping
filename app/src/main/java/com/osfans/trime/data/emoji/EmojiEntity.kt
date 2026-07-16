/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.emoji

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Metadata row for a single emoji/sticker image. The bytes stay on disk at [filePath]
 * (inside the folder of the owning [EmojiCollectionEntity]); this table only stores
 * metadata. The file path/name is storage-addressing only and is deliberately NOT part
 * of any search index — tags (incl. the primary tag) are the sole retrieval dimension.
 *
 * Every emoji always has exactly one primary tag ([primaryTagId], never null): on import
 * a numeric placeholder tag is assigned, which the user renames later. The primary tag
 * is additionally mirrored into [EmojiTagCrossRef] so "match by tag" queries need a
 * single join and every emoji is guaranteed at least one tag.
 */
@Entity(
    tableName = "emoji",
    foreignKeys = [
        ForeignKey(
            entity = EmojiCollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EmojiTagEntity::class,
            parentColumns = ["id"],
            childColumns = ["primaryTagId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["filePath"], unique = true),
        Index(value = ["collectionId"]),
        Index(value = ["primaryTagId"]),
    ],
)
data class EmojiEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val format: String,
    val collectionId: Long,
    val primaryTagId: Long,
    val useCount: Int = 0,
    val lastUsedAt: Long = 0,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
