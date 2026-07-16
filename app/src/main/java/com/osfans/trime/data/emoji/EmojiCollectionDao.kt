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

/** Data access for [EmojiCollectionEntity]. */
@Dao
interface EmojiCollectionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(collection: EmojiCollectionEntity): Long

    @Query("SELECT * FROM emoji_collection ORDER BY name")
    suspend fun getAll(): List<EmojiCollectionEntity>

    @Transaction
    @Query("SELECT * FROM emoji_collection ORDER BY name")
    suspend fun getAllWithTags(): List<CollectionWithTags>

    @Query("SELECT * FROM emoji_collection WHERE id = :id")
    suspend fun getById(id: Long): EmojiCollectionEntity?

    @Query("SELECT * FROM emoji_collection WHERE folderPath = :folderPath")
    suspend fun getByFolderPath(folderPath: String): EmojiCollectionEntity?

    @Query("UPDATE emoji_collection SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE emoji_collection SET nextSeq = :nextSeq WHERE id = :id")
    suspend fun setNextSeq(id: Long, nextSeq: Long)

    @Query("DELETE FROM emoji_collection WHERE id = :id")
    suspend fun delete(id: Long)
}
