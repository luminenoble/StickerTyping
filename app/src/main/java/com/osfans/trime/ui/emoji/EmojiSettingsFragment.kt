/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.emoji

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceScreen
import com.osfans.trime.R
import com.osfans.trime.data.emoji.EmojiBackup
import com.osfans.trime.data.emoji.EmojiRepository
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import com.osfans.trime.util.getFileFromUri
import com.osfans.trime.util.requestExternalStoragePermission
import com.osfans.trime.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Emoji import/export settings. Importing registers an on-device folder as a collection
 * (absolute path, nothing copied); export/import round-trips all metadata as JSON.
 */
class EmojiSettingsFragment : PaddingPreferenceFragment() {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri ?: return@registerForActivityResult
            val docUri =
                DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                    ?: return@registerForActivityResult
            val folder = requireContext().getFileFromUri(docUri) ?: return@registerForActivityResult
            promptCollectionName(folder.absolutePath, folder.name)
        }

    private val exportJson =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@registerForActivityResult
            runWithToast {
                val backup = EmojiRepository.exportBackup()
                writeText(uri, json.encodeToString(EmojiBackup.serializer(), backup))
                getString(R.string.emoji_export_done)
            }
        }

    private val importJson =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            runWithToast {
                val backup = json.decodeFromString(EmojiBackup.serializer(), readText(uri))
                val report = EmojiRepository.importBackup(backup)
                getString(R.string.emoji_import_report, report.collections, report.restored, report.missing)
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen =
            preferenceManager.createPreferenceScreen(requireContext()).apply {
                addClickPreference(R.string.emoji_import_folder, R.string.emoji_import_folder_summary) {
                    pickFolder.launch(null)
                }
                addClickPreference(R.string.emoji_sync_all) {
                    runWithToast {
                        val r = EmojiRepository.syncAll()
                        getString(R.string.emoji_sync_result, r.added, r.removed, r.total)
                    }
                }
                addClickPreference(R.string.emoji_export_json) {
                    exportJson.launch("emoji-backup.json")
                }
                addClickPreference(R.string.emoji_import_json) {
                    importJson.launch(arrayOf("application/json"))
                }
                addClickPreference(R.string.emoji_storage_permission) {
                    requireContext().requestExternalStoragePermission()
                }
            }
    }

    private fun PreferenceScreen.addClickPreference(titleRes: Int, summaryRes: Int? = null, onClick: () -> Unit) {
        addPreference(
            androidx.preference.Preference(context).apply {
                setTitle(titleRes)
                summaryRes?.let { setSummary(it) }
                isIconSpaceReserved = false
                setOnPreferenceClickListener {
                    onClick()
                    true
                }
            },
        )
    }

    private fun promptCollectionName(folderPath: String, defaultName: String) {
        val edit = EditText(requireContext()).apply { setText(defaultName) }
        AlertDialog
            .Builder(requireContext())
            .setTitle(R.string.emoji_collection_name)
            .setMessage(folderPath)
            .setView(edit)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = edit.text.toString().trim().ifEmpty { defaultName }
                runWithToast {
                    val r = EmojiRepository.addCollection(name, folderPath)
                    getString(R.string.emoji_sync_result, r.added, r.removed, r.total)
                }
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    private suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        requireContext().contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(text.toByteArray())
        } ?: error("cannot open $uri")
    }

    private suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        requireContext().contentResolver.openInputStream(uri)?.use {
            it.readBytes().decodeToString()
        } ?: error("cannot open $uri")
    }

    private fun runWithToast(block: suspend () -> String) {
        lifecycleScope.launch {
            runCatching { block() }
                .onSuccess { requireContext().toast(it) }
                .onFailure { requireContext().toast(getString(R.string.emoji_action_failed, it.message ?: "?")) }
        }
    }
}
