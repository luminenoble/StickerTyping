/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.emoji

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Sidecar database for the emoji/kaomoji panels. Lives alongside (and independent of)
 * the clipboard/collection database in `data.db`; the filesystem remains the source of
 * truth for the actual image bytes.
 */
@Database(
    entities = [
        EmojiEntity::class,
        EmojiCollectionEntity::class,
        EmojiTagEntity::class,
        EmojiTagCrossRef::class,
        CollectionTagCrossRef::class,
        KaomojiEntity::class,
        KaomojiTagCrossRef::class,
        KaomojiGroupEntity::class,
    ],
    version = 3,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
    ],
)
abstract class EmojiDatabase : RoomDatabase() {
    abstract fun emojiDao(): EmojiDao

    abstract fun collectionDao(): EmojiCollectionDao

    abstract fun tagDao(): EmojiTagDao

    abstract fun kaomojiDao(): KaomojiDao
}
