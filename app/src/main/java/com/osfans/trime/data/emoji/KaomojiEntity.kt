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
 * A kaomoji (text emoticon). Same rules as emojis: exactly one mandatory primary tag
 * (mirrored into [KaomojiTagCrossRef]), any number of extra tags, tag-only retrieval.
 * Tags live in the shared [EmojiTagEntity] table so one tag can find both emojis and
 * kaomojis. Output channel is plain commitText.
 */
@Entity(
    tableName = "kaomoji",
    foreignKeys = [
        ForeignKey(
            entity = EmojiTagEntity::class,
            parentColumns = ["id"],
            childColumns = ["primaryTagId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["text"], unique = true),
        Index(value = ["primaryTagId"]),
    ],
)
data class KaomojiEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val primaryTagId: Long,
    val useCount: Int = 0,
    val lastUsedAt: Long = 0,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
