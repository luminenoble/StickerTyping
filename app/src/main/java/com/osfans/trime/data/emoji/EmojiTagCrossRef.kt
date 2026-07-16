/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.emoji

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Emoji ↔ tag many-to-many join row. An emoji's primary tag also has a row here (kept in
 * sync by the repository), so tag-based queries only ever need this junction.
 */
@Entity(
    tableName = "emoji_tag_cross_ref",
    primaryKeys = ["emojiId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = EmojiEntity::class,
            parentColumns = ["id"],
            childColumns = ["emojiId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EmojiTagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tagId"])],
)
data class EmojiTagCrossRef(
    val emojiId: Long,
    val tagId: Long,
)
