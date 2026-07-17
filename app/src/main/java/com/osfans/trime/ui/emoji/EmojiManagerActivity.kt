/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.emoji

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.osfans.trime.R
import com.osfans.trime.databinding.ActivityEmojiManagerBinding
import com.osfans.trime.util.isStorageAvailable
import com.osfans.trime.util.requestExternalStoragePermission

/**
 * Standalone emoji manager: a toolbar over two sections — the collection browser
 * ([EmojiCollectionsFragment], default) and import/export settings
 * ([EmojiSettingsFragment], pushed on the back stack).
 */
class EmojiManagerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEmojiManagerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmojiManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.emojiToolbar.root.updatePadding(top = bars.top)
            binding.root.updatePadding(bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setSupportActionBar(binding.emojiToolbar.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.emojiToolbar.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        supportFragmentManager.addOnBackStackChangedListener {
            supportActionBar?.setTitle(
                if (supportFragmentManager.backStackEntryCount > 0) R.string.emoji_settings else R.string.emoji_manager,
            )
        }
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragmentContainer, EmojiCollectionsFragment())
                .commit()
        }
        supportActionBar?.setTitle(R.string.emoji_manager)
        if (!isStorageAvailable()) {
            requestExternalStoragePermission()
        }
    }

    fun showSettings() {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragmentContainer, EmojiSettingsFragment())
            .addToBackStack(null)
            .commit()
    }
}
