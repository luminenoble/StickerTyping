/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.emoji

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Sidecar database for the emoji panel. Lives alongside (and independent of) the
 * clipboard/collection database in `data.db`; the filesystem remains the source of
 * truth for the actual image bytes.
 */
@Database(
    entities = [
        EmojiEntity::class,
        EmojiCollectionEntity::class,
        EmojiTagEntity::class,
        EmojiTagCrossRef::class,
        CollectionTagCrossRef::class,
    ],
    version = 1,
)
abstract class EmojiDatabase : RoomDatabase() {
    abstract fun emojiDao(): EmojiDao

    abstract fun collectionDao(): EmojiCollectionDao

    abstract fun tagDao(): EmojiTagDao
}
