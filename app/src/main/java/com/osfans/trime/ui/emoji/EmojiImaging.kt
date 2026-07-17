/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.emoji

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder

/**
 * Shared Coil [ImageLoader] for emoji thumbnails (manager UI and keyboard panel).
 * Animated gif/webp render via ImageDecoder on API 28+, GifDecoder below; video
 * formats show their first frame.
 */
object EmojiImaging {
    @Volatile
    private var instance: ImageLoader? = null

    fun loader(context: Context): ImageLoader = instance ?: synchronized(this) {
        instance ?: ImageLoader
            .Builder(context.applicationContext)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(VideoFrameDecoder.Factory())
            }.build()
            .also { instance = it }
    }
}
