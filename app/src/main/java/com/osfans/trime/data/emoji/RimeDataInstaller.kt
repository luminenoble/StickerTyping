/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.emoji

import android.content.res.AssetManager
import com.osfans.trime.data.base.DataManager
import timber.log.Timber
import java.io.File

/**
 * Installs the bundled rime payload (assets/rime_data: dictionaries + octagram grammar
 * model) into the rime user data dir (default /sdcard/rime), where the user can see and
 * verify it and where librime resolves resources first. Unlike the shared-assets sync,
 * every file is size-verified against a build-time manifest, so a partial/failed copy
 * is retried on the next run instead of being stamped as done. The size check makes
 * repeat runs cheap (no asset reads).
 */
object RimeDataInstaller {
    private const val ASSET_ROOT = "rime_data"
    private const val MANIFEST = "rime_data.manifest"

    data class InstallReport(val copied: Int, val skipped: Int, val bytes: Long)

    /** Copy any missing/size-mismatched payload file into the user data dir. */
    fun install(assets: AssetManager): InstallReport {
        var copied = 0
        var skipped = 0
        var bytes = 0L
        val userDir = DataManager.userDataDir

        val manifest =
            runCatching {
                assets.open(MANIFEST).bufferedReader().readLines()
            }.getOrElse {
                Timber.w("No %s in assets, skipping rime data install", MANIFEST)
                return InstallReport(0, 0, 0)
            }

        for (line in manifest) {
            val (sizeStr, rel) = line.split('\t', limit = 2).takeIf { it.size == 2 } ?: continue
            val expected = sizeStr.toLongOrNull() ?: continue
            val dest = File(userDir, rel)
            if (dest.exists() && dest.length() == expected) {
                skipped++
                continue
            }
            dest.parentFile?.mkdirs()
            assets.open("$ASSET_ROOT/$rel").use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            if (dest.length() != expected) {
                dest.delete()
                error("size mismatch after copying $rel")
            }
            copied++
            bytes += expected
        }
        if (copied > 0) {
            Timber.i("Rime data install: copied=%d skipped=%d bytes=%d -> %s", copied, skipped, bytes, userDir)
        }
        return InstallReport(copied, skipped, bytes)
    }
}
