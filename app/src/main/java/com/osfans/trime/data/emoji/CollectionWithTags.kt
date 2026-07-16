/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.emoji

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/** A collection together with its tags. */
data class CollectionWithTags(
    @Embedded val collection: EmojiCollectionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy =
        Junction(
            value = CollectionTagCrossRef::class,
            parentColumn = "collectionId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<EmojiTagEntity>,
)
