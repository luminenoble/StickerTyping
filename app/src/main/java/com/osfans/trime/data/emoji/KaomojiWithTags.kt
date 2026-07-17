/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.emoji

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/** A kaomoji with its resolved primary tag and full tag list (primary included). */
data class KaomojiWithTags(
    @Embedded val kaomoji: KaomojiEntity,
    @Relation(parentColumn = "primaryTagId", entityColumn = "id")
    val primaryTag: EmojiTagEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy =
        Junction(
            value = KaomojiTagCrossRef::class,
            parentColumn = "kaomojiId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<EmojiTagEntity>,
)
