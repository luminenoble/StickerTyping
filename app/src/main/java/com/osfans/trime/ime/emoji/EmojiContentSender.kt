/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.emoji

import android.content.ClipData
import android.content.ClipDescription
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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

        // degrade: put the image on the clipboard so the user can paste it manually.
        // Tencent apps only recognise gallery-style clips: they resolve the URI through
        // MediaStore (_data queries) and silently drop foreign provider URIs, so the
        // image is first copied into MediaStore and the media URI goes on the clipboard
        // — exactly the clip shape a gallery "copy" produces.
        return runCatching {
            val clipUri =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    mediaStoreUri(file, mime) ?: uri
                } else {
                    editorInfo?.packageName?.let {
                        service.grantUriPermission(it, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    uri
                }
            clipboardManager.setPrimaryClip(ClipData.newUri(service.contentResolver, file.name, clipUri))
            Timber.i("Copied %s to clipboard as %s for manual paste", file.name, clipUri)
            Result.COPIED
        }.getOrElse {
            Timber.w(it, "Clipboard fallback failed")
            Result.FAILED
        }
    }

    /** Share the emoji as an image directly to the current target app (QQ path). */
    fun shareToCurrentApp(emoji: EmojiEntity): Boolean {
        val file = File(emoji.filePath)
        if (!file.exists()) return false
        val mime = MIME_BY_FORMAT[emoji.format] ?: return false
        val uri =
            FileProvider.getUriForFile(service, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val target = service.currentInputEditorInfo?.packageName
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                target?.let { setPackage(it) }
            }
        return runCatching {
            service.startActivity(send)
            true
        }.recoverCatching {
            // target app has no direct SEND handler — fall back to a chooser
            service.startActivity(
                Intent.createChooser(send.apply { setPackage(null) }, null)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }

    /**
     * Return a gallery-style URI for [file]. Files under the resources dir are indexed
     * by MediaStore in place (no duplicate), so first look the original path up; only
     * fall back to copying into the [ALBUM] album when it is not indexed (yet).
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaStoreUri(file: File, mime: String): Uri? {
        val resolver = service.contentResolver
        val isVideo = mime.startsWith("video/")
        val queryCollection =
            if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
        resolver
            .query(
                queryCollection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DATA} = ?",
                arrayOf(file.absolutePath),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return ContentUris.withAppendedId(queryCollection, cursor.getLong(0))
                }
            }
        return albumCopyUri(file, mime)
    }

    /** Legacy path: copy into the [ALBUM] album and return the media URI. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun albumCopyUri(file: File, mime: String): Uri? {
        val resolver = service.contentResolver
        val isVideo = mime.startsWith("video/")
        val collection =
            if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
        val relPath = (if (isVideo) "Movies/" else "Pictures/") + ALBUM

        resolver
            .query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(file.name, "$relPath/"),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return ContentUris.withAppendedId(collection, cursor.getLong(0))
                }
            }

        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val inserted = resolver.insert(collection, values) ?: return null
        val copied =
            runCatching {
                resolver.openOutputStream(inserted)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } != null
            }.getOrDefault(false)
        if (!copied) {
            resolver.delete(inserted, null, null)
            return null
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(inserted, values, null, null)
        return inserted
    }

    companion object {
        private const val ALBUM = "StickerTyping"

        /** Delete all paste-cache copies in the [ALBUM] album. @return rows removed */
        fun clearPasteCache(context: android.content.Context): Int {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
            val resolver = context.contentResolver
            var removed = 0
            listOf(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "Pictures/$ALBUM/",
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "Movies/$ALBUM/",
            ).forEach { (collection, relPath) ->
                removed +=
                    runCatching {
                        resolver.delete(
                            collection,
                            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                            arrayOf(relPath),
                        )
                    }.getOrDefault(0)
            }
            return removed
        }

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
