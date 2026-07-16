/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.emoji

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Data access for tags and both junction tables. */
@Dao
interface EmojiTagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: EmojiTagEntity): Long

    @Query("SELECT * FROM emoji_tag WHERE name = :name")
    suspend fun getByName(name: String): EmojiTagEntity?

    @Query("SELECT * FROM emoji_tag ORDER BY name")
    suspend fun getAll(): List<EmojiTagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEmojiCrossRef(crossRef: EmojiTagCrossRef)

    @Query("DELETE FROM emoji_tag_cross_ref WHERE emojiId = :emojiId AND tagId = :tagId")
    suspend fun deleteEmojiCrossRef(emojiId: Long, tagId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCollectionCrossRef(crossRef: CollectionTagCrossRef)

    @Query("DELETE FROM emoji_collection_tag_cross_ref WHERE collectionId = :collectionId AND tagId = :tagId")
    suspend fun deleteCollectionCrossRef(collectionId: Long, tagId: Long)

    /**
     * Remove tags that are no longer referenced anywhere: not attached to any emoji or
     * collection, and not anyone's primary tag (primary tags always have a junction row
     * too, but the extra guard keeps the invariant safe against partial writes).
     */
    @Query(
        """
        DELETE FROM emoji_tag WHERE
            id NOT IN (SELECT tagId FROM emoji_tag_cross_ref)
            AND id NOT IN (SELECT tagId FROM emoji_collection_tag_cross_ref)
            AND id NOT IN (SELECT primaryTagId FROM emoji)
        """,
    )
    suspend fun deleteOrphans()
}
