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

/** Data access for [KaomojiEntity]. Retrieval is tag-only, same as for emojis. */
@Dao
interface KaomojiDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(kaomoji: KaomojiEntity): Long

    @Query("SELECT * FROM kaomoji WHERE id = :id")
    suspend fun getById(id: Long): KaomojiEntity?

    @Query("SELECT * FROM kaomoji WHERE text = :text")
    suspend fun getByText(text: String): KaomojiEntity?

    @Transaction
    @Query("SELECT * FROM kaomoji")
    suspend fun getAllWithTags(): List<KaomojiWithTags>

    @Query("DELETE FROM kaomoji WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE kaomoji SET text = :text WHERE id = :id")
    suspend fun setText(id: Long, text: String)

    @Query("UPDATE kaomoji SET useCount = useCount + 1, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun incrementUse(id: Long, timestamp: Long)

    @Query("UPDATE kaomoji SET useCount = :useCount, lastUsedAt = :lastUsedAt WHERE id = :id")
    suspend fun setUsage(id: Long, useCount: Int, lastUsedAt: Long)

    @Query("UPDATE kaomoji SET isFavorite = :favorite WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<Long>, favorite: Boolean)

    @Query("UPDATE kaomoji SET primaryTagId = :tagId WHERE id = :id")
    suspend fun setPrimaryTag(id: Long, tagId: Long)

    @Query("UPDATE kaomoji SET groupId = :groupId WHERE id IN (:ids)")
    suspend fun setGroup(ids: List<Long>, groupId: Long?)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroup(group: KaomojiGroupEntity): Long

    @Query("SELECT * FROM kaomoji_group ORDER BY name")
    suspend fun getAllGroups(): List<KaomojiGroupEntity>

    @Query("SELECT * FROM kaomoji_group WHERE name = :name")
    suspend fun getGroupByName(name: String): KaomojiGroupEntity?

    @Query("UPDATE kaomoji_group SET name = :name WHERE id = :id")
    suspend fun renameGroup(id: Long, name: String)

    @Query("DELETE FROM kaomoji_group WHERE id = :id")
    suspend fun deleteGroup(id: Long)
}
