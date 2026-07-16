/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.emoji

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Collection ↔ tag many-to-many join row (collections can be tagged too). */
@Entity(
    tableName = "emoji_collection_tag_cross_ref",
    primaryKeys = ["collectionId", "tagId"],
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
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tagId"])],
)
data class CollectionTagCrossRef(
    val collectionId: Long,
    val tagId: Long,
)
