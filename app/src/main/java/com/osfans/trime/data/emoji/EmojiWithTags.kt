/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.emoji

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * An emoji together with its resolved primary tag and full tag list ([tags] includes the
 * primary tag, since the repository mirrors it into the junction table).
 */
data class EmojiWithTags(
    @Embedded val emoji: EmojiEntity,
    @Relation(parentColumn = "primaryTagId", entityColumn = "id")
    val primaryTag: EmojiTagEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy =
        Junction(
            value = EmojiTagCrossRef::class,
            parentColumn = "emojiId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<EmojiTagEntity>,
)
