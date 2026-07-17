/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.emoji

import android.content.ClipDescription
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.osfans.trime.BuildConfig
import com.osfans.trime.data.emoji.EmojiEntity
import com.osfans.trime.ime.core.TrimeInputMethodService
import timber.log.Timber
import java.io.File

/**
 * Sends an emoji image into the current input field as rich content
 * (InputConnection#commitContent via trime's existing FileProvider). By design there is
 * no clipboard/degradation fallback: if the target app rejects rich content the send
 * simply fails and the caller reports it.
 */
class EmojiContentSender(
    private val service: TrimeInputMethodService,
) {
    fun send(emoji: EmojiEntity): Boolean {
        val ic = service.currentInputConnection ?: return false
        val editorInfo = service.currentInputEditorInfo ?: return false
        val file = File(emoji.filePath)
        if (!file.exists()) {
            Timber.w("Emoji file missing: %s", emoji.filePath)
            return false
        }
        val mime = MIME_BY_FORMAT[emoji.format] ?: return false
        val uri =
            FileProvider.getUriForFile(service, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val content =
            InputContentInfoCompat(uri, ClipDescription(file.name, arrayOf(mime)), null)
        val result =
            InputConnectionCompat.commitContent(
                ic,
                editorInfo,
                content,
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                null,
            )
        Timber.i("commitContent %s (%s) to %s -> %s", file.name, mime, editorInfo.packageName, result)
        return result
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
