/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.emoji

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Kaomoji ↔ tag join row; the primary tag is mirrored here like for emojis. */
@Entity(
    tableName = "kaomoji_tag_cross_ref",
    primaryKeys = ["kaomojiId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = KaomojiEntity::class,
            parentColumns = ["id"],
            childColumns = ["kaomojiId"],
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
data class KaomojiTagCrossRef(
    val kaomojiId: Long,
    val tagId: Long,
)
