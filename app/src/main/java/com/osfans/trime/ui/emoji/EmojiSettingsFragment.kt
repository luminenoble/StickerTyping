/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.emoji

import android.content.Context
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
import com.osfans.trime.data.emoji.EmojiResources
import com.osfans.trime.data.emoji.KaomojiPack
import com.osfans.trime.ime.emoji.EmojiContentSender
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import com.osfans.trime.util.getFileFromUri
import com.osfans.trime.util.requestExternalStoragePermission
import com.osfans.trime.util.toast
import kotlinx.coroutines.CancellationException
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
            runWithToast { ctx ->
                val backup = EmojiRepository.exportBackup()
                writeText(ctx, uri, json.encodeToString(EmojiBackup.serializer(), backup))
                ctx.getString(R.string.emoji_export_done)
            }
        }

    private val importJson =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            runWithToast { ctx ->
                val backup = json.decodeFromString(EmojiBackup.serializer(), readText(ctx, uri))
                val report = EmojiRepository.importBackup(backup)
                ctx.getString(R.string.emoji_import_report, report.collections, report.restored, report.missing)
            }
        }

    private val importKaomojiTxt =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            val appCtx = requireContext().applicationContext
            lifecycleScope.launch {
                try {
                    val lines = readText(appCtx, uri).lines()
                    promptKaomojiTag(lines)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    appCtx.toast(appCtx.getString(R.string.emoji_action_failed, e.message ?: "?"))
                }
            }
        }

    private val pickKaomojiFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri ?: return@registerForActivityResult
            val docUri =
                DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                    ?: return@registerForActivityResult
            val folder = requireContext().getFileFromUri(docUri) ?: return@registerForActivityResult
            runWithToast { ctx ->
                // copy txts into resources/kaomoji/<stem>/ then register from disk
                withContext(Dispatchers.IO) { EmojiResources.copyKaomojiFolder(folder) }
                var groups = 0
                var added = 0
                for ((group, lines) in EmojiResources.kaomojiGroupsOnDisk()) {
                    groups++
                    added += EmojiRepository.importKaomojiLines(lines, group, group)
                }
                ctx.getString(R.string.kaomoji_import_folder_result, groups, added)
            }
        }

    private val importKaomojiJson =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            runWithToast { ctx ->
                val pack = json.decodeFromString(KaomojiPack.serializer(), readText(ctx, uri))
                val (groups, added) = EmojiRepository.importKaomojiPack(pack)
                ctx.getString(R.string.kaomoji_import_folder_result, groups, added)
            }
        }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? EmojiManagerActivity)?.supportActionBar?.setTitle(R.string.emoji_settings)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen =
            preferenceManager.createPreferenceScreen(requireContext()).apply {
                addClickPreference(R.string.emoji_import_folder, R.string.emoji_import_folder_summary) {
                    pickFolder.launch(null)
                }
                addClickPreference(R.string.emoji_sync_all, R.string.emoji_sync_all_summary) {
                    runWithToast { ctx ->
                        val r = EmojiRepository.syncResources(ctx)
                        ctx.getString(R.string.emoji_sync_result, r.added, r.removed, r.total)
                    }
                }
                addClickPreference(R.string.kaomoji_import_txt, R.string.kaomoji_import_txt_summary) {
                    importKaomojiTxt.launch(arrayOf("text/*"))
                }
                addClickPreference(R.string.kaomoji_import_folder, R.string.kaomoji_import_folder_summary) {
                    pickKaomojiFolder.launch(null)
                }
                addClickPreference(R.string.kaomoji_import_json, R.string.kaomoji_import_json_summary) {
                    importKaomojiJson.launch(arrayOf("application/json"))
                }
                addClickPreference(R.string.emoji_export_json) {
                    exportJson.launch("emoji-backup.json")
                }
                addClickPreference(R.string.emoji_import_json) {
                    importJson.launch(arrayOf("application/json"))
                }
                addClickPreference(R.string.emoji_clear_paste_cache, R.string.emoji_clear_paste_cache_summary) {
                    runWithToast { ctx ->
                        val removed =
                            withContext(Dispatchers.IO) {
                                EmojiContentSender.clearPasteCache(ctx)
                            }
                        ctx.getString(R.string.emoji_clear_paste_cache_result, removed)
                    }
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

    private fun promptKaomojiTag(lines: List<String>) {
        val context = requireContext()
        val tagInput = EditText(context).apply { hint = getString(R.string.emoji_set_primary_tag) }
        val groupInput = EditText(context).apply { hint = getString(R.string.kaomoji_group_optional) }
        val layout =
            android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(48, 16, 48, 0)
                addView(tagInput)
                addView(groupInput)
            }
        AlertDialog
            .Builder(context)
            .setTitle(R.string.kaomoji_import_txt)
            .setView(layout)
            .setPositiveButton(R.string.ok) { _, _ ->
                val tag = tagInput.text.toString().trim()
                if (tag.isEmpty()) {
                    context.toast(R.string.emoji_primary_tag_required)
                    return@setPositiveButton
                }
                val group = groupInput.text.toString().trim().ifEmpty { null }
                runWithToast { ctx ->
                    val added = EmojiRepository.importKaomojiLines(lines, tag, group)
                    withContext(Dispatchers.IO) {
                        EmojiResources.writeKaomojiLines(lines, group ?: tag)
                    }
                    ctx.getString(R.string.kaomoji_import_result, added)
                }
            }.setNegativeButton(R.string.cancel, null)
            .show()
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
                runWithToast { ctx ->
                    // copy into the canonical resources dir; the collection points there
                    val (dest, _) =
                        withContext(Dispatchers.IO) {
                            EmojiResources.copyEmojiFolder(java.io.File(folderPath), name)
                        }
                    val r = EmojiRepository.addCollection(name, dest.absolutePath)
                    EmojiResources.scanMedia(ctx, dest.listFiles().orEmpty().toList())
                    ctx.getString(R.string.emoji_sync_result, r.added, r.removed, r.total)
                }
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    private suspend fun writeText(ctx: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(text.toByteArray())
        } ?: error("cannot open $uri")
    }

    private suspend fun readText(ctx: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        ctx.contentResolver.openInputStream(uri)?.use {
            it.readBytes().decodeToString()
        } ?: error("cannot open $uri")
    }

    /**
     * Run [block] on the repository scope so it survives leaving this screen, and toast
     * its result via the application context. Never touches the fragment after launch:
     * cancellation is rethrown (a swallowed cancel used to resurface as a
     * requireContext() crash on a detached fragment).
     */
    private fun runWithToast(block: suspend (Context) -> String) {
        val appCtx = requireContext().applicationContext
        EmojiRepository.launch {
            val message =
                try {
                    block(appCtx)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    appCtx.getString(R.string.emoji_action_failed, e.message ?: "?")
                }
            withContext(Dispatchers.Main) { appCtx.toast(message) }
        }
    }
}
