/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.emoji

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber

/**
 * Single entry point for reading/writing emoji metadata (mirrors the
 * ClipboardHelper/CollectionHelper pattern). Invariants enforced here:
 *
 * - Every emoji always has exactly one primary tag; new imports get a numeric
 *   placeholder ("1", "2", ... per collection) minted from the collection's `nextSeq`.
 * - The primary tag is mirrored into the emoji↔tag junction, so every emoji has at
 *   least one tag and tag queries need a single join.
 * - Tags are the only retrieval dimension; nothing here ever searches file names.
 * - Sync is incremental: rows keep their tags/useCount/favorite as long as the file
 *   stays at the same path; rows whose files vanished are removed.
 */
object EmojiRepository : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {
    private lateinit var db: EmojiDatabase
    private lateinit var emojiDao: EmojiDao
    private lateinit var collectionDao: EmojiCollectionDao
    private lateinit var tagDao: EmojiTagDao

    private lateinit var kaomojiDao: KaomojiDao

    fun init(context: Context) {
        db =
            Room
                .databaseBuilder(context, EmojiDatabase::class.java, "emoji.db")
                .build()
        emojiDao = db.emojiDao()
        collectionDao = db.collectionDao()
        tagDao = db.tagDao()
        kaomojiDao = db.kaomojiDao()
    }

    /** Result of one collection sync, for surfacing in the UI. */
    data class SyncResult(val added: Int, val removed: Int, val total: Int)

    // region collections

    /**
     * Register [folderPath] as a collection named [name] (or return the existing one)
     * and sync its contents. The folder is used as-is; nothing is copied.
     */
    suspend fun addCollection(name: String, folderPath: String): SyncResult {
        collectionDao.insert(EmojiCollectionEntity(name = name, folderPath = folderPath))
        val collection = collectionDao.getByFolderPath(folderPath)
            ?: error("collection insert failed for $folderPath")
        return syncCollection(collection.id)
    }

    /**
     * Reconcile one collection with its folder on disk. New files are registered with a
     * numeric placeholder primary tag; rows whose files disappeared are removed (their
     * placeholder tags are garbage-collected unless still in use elsewhere).
     */
    suspend fun syncCollection(collectionId: Long): SyncResult {
        val collection = collectionDao.getById(collectionId)
            ?: return SyncResult(0, 0, 0)
        val onDisk = LocalFolderSource(collection.folderPath).scan()
        val known = emojiDao.getPathsByCollection(collectionId).toHashSet()

        val toAdd = onDisk.filter { it.filePath !in known }
        val onDiskPaths = onDisk.mapTo(HashSet()) { it.filePath }
        val toRemove = known.filter { it !in onDiskPaths }

        db.withTransaction {
            var seq = collection.nextSeq
            for (candidate in toAdd) {
                val tagId = getOrCreateTag(seq.toString()) ?: continue
                val emojiId =
                    emojiDao.insert(
                        EmojiEntity(
                            filePath = candidate.filePath,
                            format = candidate.format,
                            collectionId = collectionId,
                            primaryTagId = tagId,
                        ),
                    )
                if (emojiId > 0) {
                    tagDao.insertEmojiCrossRef(EmojiTagCrossRef(emojiId, tagId))
                    seq++
                }
            }
            if (seq != collection.nextSeq) {
                collectionDao.setNextSeq(collectionId, seq)
            }
            if (toRemove.isNotEmpty()) {
                emojiDao.deleteByPaths(toRemove)
            }
            tagDao.deleteOrphans()
        }

        val total = emojiDao.countByCollection(collectionId)
        Timber.i(
            "Emoji sync of '%s': +%d -%d = %d",
            collection.name,
            toAdd.size,
            toRemove.size,
            total,
        )
        return SyncResult(toAdd.size, toRemove.size, total)
    }

    /** Sync every registered collection. */
    suspend fun syncAll(): SyncResult = collectionDao
        .getAll()
        .map { syncCollection(it.id) }
        .fold(SyncResult(0, 0, 0)) { acc, r ->
            SyncResult(acc.added + r.added, acc.removed + r.removed, acc.total + r.total)
        }

    /**
     * Reconcile the whole canonical resources dir: every dir under resources/emoji that
     * directly holds supported files becomes (or stays) a collection — at any depth, so
     * a whole pasted tree still partitions by its leaf folders. Bare wrapper dirs and
     * registrations whose dir vanished are dropped. Every txt line under
     * resources/kaomoji/<group>/ is registered under its group, then all collections
     * sync. Emoji files are handed to MediaStore so gallery-style clipboard URIs exist
     * at paste time.
     */
    suspend fun syncResources(context: Context): SyncResult {
        val emojiRoot = EmojiResources.emojiRoot
        val liveDirs =
            emojiRoot
                .walkTopDown()
                .filter { dir ->
                    dir.isDirectory &&
                        dir != emojiRoot &&
                        dir.listFiles()?.any {
                            it.isFile && it.extension.lowercase() in LocalFolderSource.SUPPORTED_FORMATS
                        } == true
                }.toList()
        liveDirs.forEach { dir ->
            collectionDao.insert(EmojiCollectionEntity(name = dir.name, folderPath = dir.absolutePath))
        }
        val livePaths = liveDirs.mapTo(HashSet()) { it.absolutePath }
        collectionDao
            .getAll()
            .filter { c ->
                // stale under resources/emoji (deleted or bare wrapper dir), or a
                // registration whose folder is gone entirely (e.g. a botched import)
                val inResources = c.folderPath.startsWith(emojiRoot.absolutePath + java.io.File.separator)
                (inResources && c.folderPath !in livePaths) ||
                    (!inResources && !java.io.File(c.folderPath).isDirectory)
            }.forEach { removeCollection(it.id) }
        for ((group, lines) in EmojiResources.kaomojiGroupsOnDisk()) {
            importKaomojiLines(lines, group, group)
        }
        val result = syncAll()
        val files = emojiDao.getAllWithTags().map { java.io.File(it.emoji.filePath) }
        EmojiResources.scanMedia(context, files)
        return result
    }

    /** Unregister a collection and all its emoji rows. Files on disk are untouched. */
    suspend fun removeCollection(collectionId: Long) {
        db.withTransaction {
            collectionDao.delete(collectionId)
            tagDao.deleteOrphans()
        }
    }

    suspend fun renameCollection(collectionId: Long, name: String) = collectionDao.rename(collectionId, name)

    suspend fun collections(): List<EmojiCollectionEntity> = collectionDao.getAll()

    suspend fun collectionsWithTags(): List<CollectionWithTags> = collectionDao.getAllWithTags()

    // endregion

    // region tags

    /** Attach a tag (created on demand) to an emoji. */
    suspend fun addTagToEmoji(emojiId: Long, tagName: String) {
        db.withTransaction {
            val tagId = getOrCreateTag(tagName) ?: return@withTransaction
            tagDao.insertEmojiCrossRef(EmojiTagCrossRef(emojiId, tagId))
        }
    }

    /**
     * Detach a normal tag from an emoji. Refuses to detach the primary tag — change it
     * with [setPrimaryTag] first.
     *
     * @return false if [tagId] is the emoji's primary tag
     */
    suspend fun removeTagFromEmoji(emojiId: Long, tagId: Long): Boolean {
        val emoji = emojiDao.getById(emojiId) ?: return false
        if (emoji.primaryTagId == tagId) return false
        db.withTransaction {
            tagDao.deleteEmojiCrossRef(emojiId, tagId)
            tagDao.deleteOrphans()
        }
        return true
    }

    /**
     * Set/replace an emoji's primary tag (created on demand). The previous primary tag
     * is detached from the emoji unless [keepOldAsNormal] — the default drop matches the
     * placeholder workflow, where renaming should not leave "42" behind as a stray tag.
     */
    suspend fun setPrimaryTag(emojiId: Long, tagName: String, keepOldAsNormal: Boolean = false) {
        db.withTransaction {
            val emoji = emojiDao.getById(emojiId) ?: return@withTransaction
            val tagId = getOrCreateTag(tagName) ?: return@withTransaction
            if (tagId == emoji.primaryTagId) return@withTransaction
            tagDao.insertEmojiCrossRef(EmojiTagCrossRef(emojiId, tagId))
            emojiDao.setPrimaryTag(emojiId, tagId)
            if (!keepOldAsNormal) {
                tagDao.deleteEmojiCrossRef(emojiId, emoji.primaryTagId)
            }
            tagDao.deleteOrphans()
        }
    }

    /** Attach a tag (created on demand) to a collection. */
    suspend fun addTagToCollection(collectionId: Long, tagName: String) {
        db.withTransaction {
            val tagId = getOrCreateTag(tagName) ?: return@withTransaction
            tagDao.insertCollectionCrossRef(CollectionTagCrossRef(collectionId, tagId))
        }
    }

    suspend fun removeTagFromCollection(collectionId: Long, tagId: Long) {
        db.withTransaction {
            tagDao.deleteCollectionCrossRef(collectionId, tagId)
            tagDao.deleteOrphans()
        }
    }

    suspend fun allTags(): List<EmojiTagEntity> = tagDao.getAll()

    /** Get-or-create by trimmed name; null for blank input. Call inside a transaction. */
    private suspend fun getOrCreateTag(rawName: String): Long? {
        val name = rawName.trim()
        if (name.isEmpty()) return null
        val inserted = tagDao.insert(EmojiTagEntity(name = name))
        if (inserted > 0) return inserted
        return tagDao.getByName(name)?.id
    }

    // endregion

    // region emoji queries and updates

    suspend fun allEmojis(): List<EmojiWithTags> = emojiDao.getAllWithTags()

    suspend fun emojiWithTags(id: Long): EmojiWithTags? = emojiDao.getWithTagsById(id)

    /**
     * Delete emoji registrations. With [deleteFiles] the image files are removed from
     * disk too; without it only the DB rows go — note the next folder sync re-registers
     * files that still exist.
     */
    suspend fun deleteEmojis(ids: List<Long>, deleteFiles: Boolean) {
        val rows = emojiDao.getByIds(ids)
        db.withTransaction {
            emojiDao.deleteByIds(ids)
            tagDao.deleteOrphans()
        }
        if (deleteFiles) {
            for (row in rows) {
                runCatching { java.io.File(row.filePath).delete() }
            }
        }
    }

    suspend fun emojisByCollection(collectionId: Long): List<EmojiWithTags> = emojiDao.getByCollectionWithTags(collectionId)

    suspend fun favoriteEmojis(): List<EmojiWithTags> = emojiDao.getFavoritesWithTags()

    suspend fun emojisByUse(): List<EmojiWithTags> = emojiDao.getAllWithTagsByUse()

    /** Tag-only substring search (primary tag included). */
    suspend fun searchByTag(query: String): List<EmojiWithTags> = emojiDao.searchByTag(query)

    suspend fun setFavorite(ids: List<Long>, favorite: Boolean) = emojiDao.setFavorite(ids, favorite)

    /** Bump useCount/lastUsedAt after a successful send. */
    suspend fun markUsed(id: Long) = emojiDao.incrementUse(id, System.currentTimeMillis())

    // endregion

    // region kaomoji

    /**
     * Register a kaomoji with its mandatory primary tag (created on demand), optionally
     * into a group (created on demand).
     *
     * @return true if a new row was inserted; false for blank/duplicate input
     */
    suspend fun addKaomoji(text: String, primaryTagName: String, groupName: String? = null): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        var inserted = false
        db.withTransaction {
            val tagId = getOrCreateTag(primaryTagName) ?: return@withTransaction
            val groupId = groupName?.let { getOrCreateKaomojiGroup(it) }
            val id = kaomojiDao.insert(KaomojiEntity(text = trimmed, primaryTagId = tagId, groupId = groupId))
            if (id > 0) {
                tagDao.insertKaomojiCrossRef(KaomojiTagCrossRef(id, tagId))
                inserted = true
            } else {
                tagDao.deleteOrphans()
            }
        }
        return inserted
    }

    /** Batch-register kaomojis (one per line), all under [primaryTagName]/[groupName]. */
    suspend fun importKaomojiLines(lines: List<String>, primaryTagName: String, groupName: String? = null): Int = lines
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .count { addKaomoji(it, primaryTagName, groupName) }

    /**
     * Import a folder of .txt files: each file becomes a group named after the file
     * (without extension), each line a kaomoji whose primary tag defaults to the group
     * name (retag later in the manager).
     *
     * @return (groups, added)
     */
    suspend fun importKaomojiFolder(folderPath: String): Pair<Int, Int> {
        val root = java.io.File(folderPath)
        if (!root.isDirectory) return 0 to 0
        var groups = 0
        var added = 0
        root.listFiles { f -> f.isFile && f.extension.lowercase() == "txt" }?.forEach { file ->
            val group = file.nameWithoutExtension.trim()
            if (group.isEmpty()) return@forEach
            groups++
            added += importKaomojiLines(file.readLines(), group, group)
        }
        return groups to added
    }

    suspend fun kaomojiGroups(): List<KaomojiGroupEntity> = kaomojiDao.getAllGroups()

    suspend fun setKaomojiGroup(ids: List<Long>, groupName: String?) {
        db.withTransaction {
            val groupId = groupName?.let { getOrCreateKaomojiGroup(it) }
            kaomojiDao.setGroup(ids, groupId)
        }
    }

    suspend fun renameKaomojiGroup(groupId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) kaomojiDao.renameGroup(groupId, trimmed)
    }

    /** Delete a group; its kaomojis stay, ungrouped (FK SET_NULL). */
    suspend fun deleteKaomojiGroup(groupId: Long) = kaomojiDao.deleteGroup(groupId)

    /** Get-or-create by trimmed name; null for blank. */
    private suspend fun getOrCreateKaomojiGroup(rawName: String): Long? {
        val name = rawName.trim()
        if (name.isEmpty()) return null
        val inserted = kaomojiDao.insertGroup(KaomojiGroupEntity(name = name))
        if (inserted > 0) return inserted
        return kaomojiDao.getGroupByName(name)?.id
    }

    suspend fun allKaomoji(): List<KaomojiWithTags> = kaomojiDao.getAllWithTags()

    suspend fun updateKaomojiText(id: Long, text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) kaomojiDao.setText(id, trimmed)
    }

    suspend fun deleteKaomoji(ids: List<Long>) {
        db.withTransaction {
            kaomojiDao.deleteByIds(ids)
            tagDao.deleteOrphans()
        }
    }

    suspend fun addTagToKaomoji(kaomojiId: Long, tagName: String) {
        db.withTransaction {
            val tagId = getOrCreateTag(tagName) ?: return@withTransaction
            tagDao.insertKaomojiCrossRef(KaomojiTagCrossRef(kaomojiId, tagId))
        }
    }

    /** Same primary-tag protection as [removeTagFromEmoji]. */
    suspend fun removeTagFromKaomoji(kaomojiId: Long, tagId: Long): Boolean {
        val kaomoji = kaomojiDao.getById(kaomojiId) ?: return false
        if (kaomoji.primaryTagId == tagId) return false
        db.withTransaction {
            tagDao.deleteKaomojiCrossRef(kaomojiId, tagId)
            tagDao.deleteOrphans()
        }
        return true
    }

    /** Same replace semantics as [setPrimaryTag]. */
    suspend fun setKaomojiPrimaryTag(kaomojiId: Long, tagName: String, keepOldAsNormal: Boolean = false) {
        db.withTransaction {
            val kaomoji = kaomojiDao.getById(kaomojiId) ?: return@withTransaction
            val tagId = getOrCreateTag(tagName) ?: return@withTransaction
            if (tagId == kaomoji.primaryTagId) return@withTransaction
            tagDao.insertKaomojiCrossRef(KaomojiTagCrossRef(kaomojiId, tagId))
            kaomojiDao.setPrimaryTag(kaomojiId, tagId)
            if (!keepOldAsNormal) {
                tagDao.deleteKaomojiCrossRef(kaomojiId, kaomoji.primaryTagId)
            }
            tagDao.deleteOrphans()
        }
    }

    suspend fun setKaomojiFavorite(ids: List<Long>, favorite: Boolean) = kaomojiDao.setFavorite(ids, favorite)

    suspend fun markKaomojiUsed(id: Long) = kaomojiDao.incrementUse(id, System.currentTimeMillis())

    // endregion

    // region backup

    /** Snapshot the whole metadata layer into the JSON round-trip format. */
    suspend fun exportBackup(): EmojiBackup {
        val collections = collectionDao.getAllWithTags()
        val groupNames = kaomojiDao.getAllGroups().associate { it.id to it.name }
        return EmojiBackup(
            kaomojis =
            kaomojiDao.getAllWithTags().map { k ->
                EmojiBackup.KaomojiItemBackup(
                    text = k.kaomoji.text,
                    primaryTag = k.primaryTag.name,
                    tags = k.tags.map { it.name },
                    isFavorite = k.kaomoji.isFavorite,
                    useCount = k.kaomoji.useCount,
                    lastUsedAt = k.kaomoji.lastUsedAt,
                    group = k.kaomoji.groupId?.let { groupNames[it] },
                )
            },
            collections =
            collections.map { c ->
                EmojiBackup.CollectionBackup(
                    name = c.collection.name,
                    folderPath = c.collection.folderPath,
                    tags = c.tags.map { it.name },
                    emojis =
                    emojiDao.getByCollectionWithTags(c.collection.id).map { e ->
                        EmojiBackup.EmojiItemBackup(
                            filePath = e.emoji.filePath,
                            fileName = e.emoji.filePath.substringAfterLast('/'),
                            primaryTag = e.primaryTag.name,
                            tags = e.tags.map { it.name },
                            isFavorite = e.emoji.isFavorite,
                            useCount = e.emoji.useCount,
                            lastUsedAt = e.emoji.lastUsedAt,
                        )
                    },
                )
            },
        )
    }

    /** Per-collection outcome of [importBackup]. */
    data class ImportReport(val collections: Int, val restored: Int, val missing: Int)

    /**
     * Merge a backup into the database: register every collection whose folder still
     * exists (scanning it), then restore primary tag / tags / favorite / usage onto
     * each emoji, located by exact path or — for backups made on another device/layout —
     * by bare file name. Emojis found neither way are counted in [ImportReport.missing]
     * and skipped.
     */
    suspend fun importBackup(backup: EmojiBackup): ImportReport = db.withTransaction {
        var restored = 0
        var missing = 0
        for (c in backup.collections) {
            if (java.io.File(c.folderPath).isDirectory) {
                addCollection(c.name, c.folderPath)
                collectionDao.getByFolderPath(c.folderPath)?.let { collection ->
                    for (tag in c.tags) {
                        addTagToCollection(collection.id, tag)
                    }
                }
            }
            for (item in c.emojis) {
                val emoji = emojiDao.getByPath(item.filePath) ?: emojiDao.getByFileName(item.fileName)
                if (emoji == null) {
                    missing++
                    continue
                }
                setPrimaryTag(emoji.id, item.primaryTag)
                for (tag in item.tags) {
                    addTagToEmoji(emoji.id, tag)
                }
                emojiDao.setFavorite(listOf(emoji.id), item.isFavorite)
                emojiDao.setUsage(emoji.id, item.useCount, item.lastUsedAt)
                restored++
            }
        }
        for (item in backup.kaomojis) {
            addKaomoji(item.text, item.primaryTag, item.group)
            val kaomoji = kaomojiDao.getByText(item.text.trim()) ?: continue
            setKaomojiPrimaryTag(kaomoji.id, item.primaryTag)
            for (tag in item.tags) {
                addTagToKaomoji(kaomoji.id, tag)
            }
            item.group?.let { setKaomojiGroup(listOf(kaomoji.id), it) }
            kaomojiDao.setFavorite(listOf(kaomoji.id), item.isFavorite)
            kaomojiDao.setUsage(kaomoji.id, item.useCount, item.lastUsedAt)
            restored++
        }
        ImportReport(backup.collections.size, restored, missing)
    }

    /**
     * Import a standalone [KaomojiPack]: entries land in their group; a blank
     * primaryTag falls back to the group name (so the mandatory-primary-tag rule
     * always holds).
     *
     * @return (groups, added)
     */
    suspend fun importKaomojiPack(pack: KaomojiPack): Pair<Int, Int> {
        var added = 0
        for (group in pack.groups) {
            for (item in group.items) {
                val primary = item.primaryTag.trim().ifEmpty { group.name }
                if (addKaomoji(item.text, primary, group.name)) {
                    added++
                }
                val row = kaomojiDao.getByText(item.text.trim()) ?: continue
                for (tag in item.tags) {
                    addTagToKaomoji(row.id, tag)
                }
            }
        }
        return pack.groups.size to added
    }

    // endregion
}
