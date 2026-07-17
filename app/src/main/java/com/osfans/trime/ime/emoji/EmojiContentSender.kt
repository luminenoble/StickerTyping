/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.emoji

import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.osfans.trime.BuildConfig
import com.osfans.trime.data.emoji.EmojiEntity
import com.osfans.trime.ime.core.TrimeInputMethodService
import splitties.systemservices.clipboardManager
import timber.log.Timber
import java.io.File

/**
 * Sends an emoji image into the current input field as rich content
 * (InputConnection#commitContent via trime's existing FileProvider). Apps that reject
 * rich content (WeChat/QQ) get the image copied to the clipboard instead, ready for a
 * long-press paste into the input box — the caller distinguishes the outcomes via
 * [Result].
 */
class EmojiContentSender(
    private val service: TrimeInputMethodService,
) {
    enum class Result { COMMITTED, COPIED, FAILED }

    fun send(emoji: EmojiEntity): Result {
        val file = File(emoji.filePath)
        if (!file.exists()) {
            Timber.w("Emoji file missing: %s", emoji.filePath)
            return Result.FAILED
        }
        val mime = MIME_BY_FORMAT[emoji.format] ?: return Result.FAILED
        val uri =
            FileProvider.getUriForFile(service, "${BuildConfig.APPLICATION_ID}.fileprovider", file)

        val ic = service.currentInputConnection
        val editorInfo = service.currentInputEditorInfo
        if (ic != null && editorInfo != null) {
            val content =
                InputContentInfoCompat(uri, ClipDescription(file.name, arrayOf(mime)), null)
            val committed =
                InputConnectionCompat.commitContent(
                    ic,
                    editorInfo,
                    content,
                    InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                    null,
                )
            Timber.i("commitContent %s (%s) to %s -> %s", file.name, mime, editorInfo.packageName, committed)
            if (committed) return Result.COMMITTED
        }

        // degrade: put the image on the clipboard so the user can paste it manually
        return runCatching {
            editorInfo?.packageName?.let {
                service.grantUriPermission(it, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            clipboardManager.setPrimaryClip(ClipData.newUri(service.contentResolver, file.name, uri))
            Timber.i("Copied %s to clipboard for manual paste", file.name)
            Result.COPIED
        }.getOrDefault(Result.FAILED)
    }

    companion object {
        private val MIME_BY_FORMAT =
            mapOf(
                "png" to "image/png",
                "jpg" to "image/jpeg",
                "jpeg" to "image/jpeg",
                "webp" to "image/webp",
                "gif" to "image/gif",
                "bmp" to "image/bmp",
                "heif" to "image/heif",
                "heic" to "image/heic",
                "mp4" to "video/mp4",
                "webm" to "video/webm",
            )
    }
}
