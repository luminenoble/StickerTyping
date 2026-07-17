/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.emoji

import kotlinx.serialization.Serializable

/**
 * JSON round-trip format for the emoji metadata layer. Only metadata is exported — the
 * image bytes stay in the collection folders, addressed by absolute path. Importing a
 * backup registers each collection (re-scanning its folder) and then re-applies primary
 * tag / tags / favorite / usage to every emoji whose file is still present.
 */
@Serializable
data class EmojiBackup(
    val version: Int = 1,
    val collections: List<CollectionBackup>,
    val kaomojis: List<KaomojiItemBackup> = emptyList(),
) {
    @Serializable
    data class KaomojiItemBackup(
        val text: String,
        val primaryTag: String,
        val tags: List<String>,
        val isFavorite: Boolean,
        val useCount: Int,
        val lastUsedAt: Long,
        val group: String? = null,
    )

    @Serializable
    data class CollectionBackup(
        val name: String,
        val folderPath: String,
        val tags: List<String>,
        val emojis: List<EmojiItemBackup>,
    )

    @Serializable
    data class EmojiItemBackup(
        val filePath: String,
        val fileName: String,
        val primaryTag: String,
        val tags: List<String>,
        val isFavorite: Boolean,
        val useCount: Int,
        val lastUsedAt: Long,
    )
}

/**
 * Standalone kaomoji pack format for sharing/importing kaomoji sets independently of a
 * full backup: groups of entries, each with a mandatory primary tag.
 */
@Serializable
data class KaomojiPack(
    val groups: List<Group>,
) {
    @Serializable
    data class Group(
        val name: String,
        val items: List<Item>,
    )

    @Serializable
    data class Item(
        val text: String,
        val primaryTag: String = "",
        val tags: List<String> = emptyList(),
    )
}
