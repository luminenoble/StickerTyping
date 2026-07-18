/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.emoji

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [EmojiImportSource] backed by an on-device folder (one collection = one folder).
 * Reports the folder's direct children with a supported extension — never recurses,
 * so nested subfolders can be independent collections without double-registration.
 * File names are used only to locate the bytes — they contribute nothing to tags.
 */
class LocalFolderSource(
    private val folderPath: String,
) : EmojiImportSource {
    override suspend fun scan(): List<EmojiImportSource.CandidateEmoji> = withContext(Dispatchers.IO) {
        val root = File(folderPath)
        if (!root.isDirectory) return@withContext emptyList()
        root
            .listFiles { f -> f.isFile && f.extension.lowercase() in SUPPORTED_FORMATS }
            .orEmpty()
            .map { EmojiImportSource.CandidateEmoji(it.absolutePath, it.extension.lowercase()) }
    }

    companion object {
        val SUPPORTED_FORMATS =
            setOf(
                "png", "jpg", "jpeg", "webp", "gif", "bmp", "heif", "heic",
                "mp4", "webm",
            )
    }
}
