/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.emoji

import android.content.Context
import android.media.MediaScannerConnection
import com.osfans.trime.data.base.DataManager
import timber.log.Timber
import java.io.File

/**
 * The canonical on-device home of all emoji/kaomoji sources:
 * `<rime user dir>/resources/emoji/<collection>/` for images and
 * `<rime user dir>/resources/kaomoji/<group>/` txt files for emoticon lists.
 * Imports copy into here (one subfolder per collection/group); the user can also drop
 * files in with a file manager and hit re-sync. Image files are registered with
 * MediaStore so the clipboard fallback can hand out gallery-style URIs directly.
 */
object EmojiResources {
    val root: File get() = File(DataManager.userDataDir, "resources")
    val emojiRoot: File get() = File(root, "emoji").also { it.mkdirs() }
    val kaomojiRoot: File get() = File(root, "kaomoji").also { it.mkdirs() }

    /**
     * Copy every supported file from [src] into `resources/emoji/<name>` (recursive,
     * flattening name clashes are the user's responsibility; same-size files skipped).
     *
     * @return the canonical collection dir and how many files were copied
     */
    fun copyEmojiFolder(src: File, name: String): Pair<File, Int> {
        val dest = File(emojiRoot, name).also { it.mkdirs() }
        var copied = 0
        src
            .walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in LocalFolderSource.SUPPORTED_FORMATS }
            .forEach { file ->
                val target = File(dest, file.name)
                if (!target.exists() || target.length() != file.length()) {
                    file.copyTo(target, overwrite = true)
                    copied++
                }
            }
        Timber.i("Copied %d emoji files into %s", copied, dest)
        return dest to copied
    }

    /**
     * Copy every .txt from [src] into `resources/kaomoji/<file-stem>/<file>` — each
     * text file becomes (or joins) the group named after it.
     *
     * @return number of files copied
     */
    fun copyKaomojiFolder(src: File): Int {
        var copied = 0
        src.listFiles { f -> f.isFile && f.extension.lowercase() == "txt" }?.forEach { file ->
            val stem = file.nameWithoutExtension.trim()
            if (stem.isEmpty()) return@forEach
            val target = File(File(kaomojiRoot, stem).also { it.mkdirs() }, file.name)
            if (!target.exists() || target.length() != file.length()) {
                file.copyTo(target, overwrite = true)
                copied++
            }
        }
        return copied
    }

    /** Write ad-hoc imported lines into the canonical layout so resources stay complete. */
    fun writeKaomojiLines(lines: List<String>, group: String) {
        val dir = File(kaomojiRoot, group).also { it.mkdirs() }
        val target = File(dir, "$group-imported.txt")
        val existing = if (target.exists()) target.readLines().toMutableList() else mutableListOf()
        val merged = (existing + lines).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        target.writeText(merged.joinToString("\n"))
    }

    /** Kaomoji groups on disk: subfolder name → all lines from its .txt files. */
    fun kaomojiGroupsOnDisk(): Map<String, List<String>> = kaomojiRoot
        .listFiles { f -> f.isDirectory }
        .orEmpty()
        .associate { dir ->
            dir.name to
                dir
                    .listFiles { f -> f.isFile && f.extension.lowercase() == "txt" }
                    .orEmpty()
                    .flatMap { it.readLines() }
        }.filterValues { it.isNotEmpty() }

    /** Ask MediaStore to index [files] so gallery-style URIs exist for pasting. */
    fun scanMedia(context: Context, files: List<File>) {
        if (files.isEmpty()) return
        MediaScannerConnection.scanFile(
            context.applicationContext,
            files.map { it.absolutePath }.toTypedArray(),
            null,
            null,
        )
    }
}
