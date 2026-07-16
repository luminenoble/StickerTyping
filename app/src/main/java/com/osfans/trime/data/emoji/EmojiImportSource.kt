/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.emoji

/**
 * A source of emoji files for one collection. The import pipeline only talks to this
 * interface, so future sources (e.g. a network importer that downloads into the
 * collection folder first) plug in as new implementations without touching the data
 * layer or UI. This module is fully offline; [LocalFolderSource] is the only
 * implementation for now.
 */
interface EmojiImportSource {
    /** Enumerate every candidate emoji file this source currently provides. */
    suspend fun scan(): List<CandidateEmoji>

    /** A discovered emoji file: absolute path + lower-case extension. */
    data class CandidateEmoji(
        val filePath: String,
        val format: String,
    )
}
