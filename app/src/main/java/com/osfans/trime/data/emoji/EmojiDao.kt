/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.emoji

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Data access for [EmojiEntity]. Note that no query in here matches on
 * filePath/format except by exact key for storage bookkeeping — tag-based
 * retrieval lives in the JOIN queries and never touches file names.
 */
@Dao
interface EmojiDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(emoji: EmojiEntity): Long

    @Query("SELECT filePath FROM emoji WHERE collectionId = :collectionId")
    suspend fun getPathsByCollection(collectionId: Long): List<String>

    @Query("SELECT * FROM emoji WHERE id = :id")
    suspend fun getById(id: Long): EmojiEntity?

    @Query("DELETE FROM emoji WHERE filePath IN (:filePaths)")
    suspend fun deleteByPaths(filePaths: List<String>)

    @Transaction
    @Query("SELECT * FROM emoji")
    suspend fun getAllWithTags(): List<EmojiWithTags>

    @Transaction
    @Query("SELECT * FROM emoji WHERE collectionId = :collectionId")
    suspend fun getByCollectionWithTags(collectionId: Long): List<EmojiWithTags>

    @Transaction
    @Query("SELECT * FROM emoji WHERE isFavorite = 1")
    suspend fun getFavoritesWithTags(): List<EmojiWithTags>

    @Transaction
    @Query("SELECT * FROM emoji ORDER BY useCount DESC, lastUsedAt DESC")
    suspend fun getAllWithTagsByUse(): List<EmojiWithTags>

    /**
     * Tag-only search: an emoji matches when any of its tags (the mirrored primary tag
     * included) contains [query].
     */
    @Transaction
    @Query(
        """
        SELECT DISTINCT emoji.* FROM emoji
        JOIN emoji_tag_cross_ref ON emoji.id = emoji_tag_cross_ref.emojiId
        JOIN emoji_tag ON emoji_tag.id = emoji_tag_cross_ref.tagId
        WHERE emoji_tag.name LIKE '%' || :query || '%'
        """,
    )
    suspend fun searchByTag(query: String): List<EmojiWithTags>

    @Query("UPDATE emoji SET useCount = useCount + 1, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun incrementUse(id: Long, timestamp: Long)

    @Query("UPDATE emoji SET isFavorite = :favorite WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<Long>, favorite: Boolean)

    @Query("UPDATE emoji SET primaryTagId = :tagId WHERE id = :id")
    suspend fun setPrimaryTag(id: Long, tagId: Long)

    @Query("SELECT COUNT(*) FROM emoji WHERE collectionId = :collectionId")
    suspend fun countByCollection(collectionId: Long): Int

    @Query("SELECT COUNT(*) FROM emoji WHERE primaryTagId = :tagId")
    suspend fun countByPrimaryTag(tagId: Long): Int
}
