package io.github.pxgray.clasp.data.backup

import io.github.pxgray.clasp.data.ItemFiles
import io.github.pxgray.clasp.data.WalletRepository
import io.github.pxgray.clasp.data.db.WalletJson
import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.domain.WalletItem
import io.github.pxgray.clasp.pkpass.readCapped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.time.Instant

private const val MANIFEST = "wallet.json"
private const val VERSION = 1
private const val MAX_ENTRIES = 10_000
private const val MAX_ENTRY_BYTES = 10 * 1024 * 1024
private const val MAX_TOTAL_BYTES = 500L * 1024 * 1024

private val SAFE_ID = Regex("[A-Za-z0-9-]{1,64}")
private val ITEM_FILE = Regex("items/([A-Za-z0-9-]{1,64})/([A-Za-z0-9._@-]{1,128})")

@Serializable
private data class BackupFile(val version: Int, val items: List<BackupItem>)

@Serializable
private data class BackupItem(
    val id: String,
    val kind: ItemKind,
    val title: String,
    val subtitle: String? = null,
    val barcode: Barcode? = null,
    val brandColor: Int? = null,
    val logoPath: String? = null,
    val details: ItemDetails,
    val createdAt: Long,
    val lastUsedAt: Long? = null,
    val isFavorite: Boolean = false,
    val website: String? = null,
    val phone: String? = null,
)

/**
 * A backup is a zip with `wallet.json` (all items) and each item's files under `items/<id>/`.
 * Import merges: items replace those with the same id; nothing is changed if the backup is invalid.
 */
class WalletBackup(
    private val repository: WalletRepository,
    private val files: ItemFiles,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Writes every item to [out] and returns how many were written. Does not close [out]. */
    suspend fun export(out: OutputStream): Int {
        val items = repository.observeItems().first()
        withContext(ioDispatcher) {
            val zip = ZipOutputStream(out)
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(WalletJson.encodeToString(BackupFile.serializer(), BackupFile(VERSION, items.map { it.toBackup() })).toByteArray())
            zip.closeEntry()
            items.forEach { item ->
                files.listFiles(item.id).forEach { path ->
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(files.resolve(path).readBytes())
                    zip.closeEntry()
                }
            }
            zip.finish()
        }
        return items.size
    }

    /**
     * Restores the backup in [input] and returns how many items it held, or null if it isn't a valid
     * backup or couldn't be saved; then files it wrote are removed again.
     */
    suspend fun import(input: InputStream): Int? {
        val (items, payload) = withContext(ioDispatcher) { read(input) } ?: return null
        val ids = items.map { it.id }.toSet()
        val toWrite = payload.filterKeys { itemIdOf(it) in ids }
        val created = withContext(ioDispatcher) {
            toWrite.keys.filterNot { files.resolve(it).exists() }.also {
                toWrite.forEach { (path, bytes) -> files.write(path, bytes) }
            }
        }
        try {
            repository.upsertAll(items)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            withContext(NonCancellable + ioDispatcher) {
                created.forEach { files.resolve(it).delete() }
                ids.filter { files.listFiles(it).isEmpty() }.forEach(files::deleteAll)
            }
            return null
        }
        withContext(ioDispatcher) { items.forEach { files.pruneLogos(it.id, keep = it.logoPath) } }
        return items.size
    }

    private fun read(input: InputStream): Pair<List<WalletItem>, Map<String, ByteArray>>? {
        var manifest: ByteArray? = null
        val payload = mutableMapOf<String, ByteArray>()
        var count = 0
        var total = 0L
        try {
            ZipInputStream(input).let { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++count > MAX_ENTRIES) return null
                    if (entry.isDirectory) continue
                    val name = entry.name
                    val wanted = name == MANIFEST || isSafeItemFile(name)
                    if (!wanted) continue
                    val data = zip.readCapped(MAX_ENTRY_BYTES) ?: return null
                    total += data.size
                    if (total > MAX_TOTAL_BYTES) return null
                    if (name == MANIFEST) manifest = data else payload[name] = data
                }
            }
        } catch (_: IOException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        val backup = try {
            WalletJson.decodeFromString(BackupFile.serializer(), String(manifest ?: return null, Charsets.UTF_8))
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (backup.version != VERSION || backup.items.any { !SAFE_ID.matches(it.id) || !it.detailsMatchKind() }) return null
        val items = backup.items.map { stored ->
            // A logo must be one of this item's own files in the backup.
            stored.toDomain().let { item ->
                val logo = item.logoPath
                if (logo != null && (logo !in payload || itemIdOf(logo) != item.id)) item.copy(logoPath = null) else item
            }
        }
        return items to payload
    }

    /** The item id in an `items/<id>/<file>` path, or null for any other path. */
    private fun itemIdOf(path: String): String? = ITEM_FILE.matchEntire(path)?.groupValues?.get(1)

    private fun isSafeItemFile(name: String): Boolean {
        val match = ITEM_FILE.matchEntire(name) ?: return false
        val fileName = match.groupValues[2]
        return fileName != "." && fileName != ".."
    }
}

/** The rest of the app relies on each [ItemKind] having its own kind of [ItemDetails]. */
private fun BackupItem.detailsMatchKind(): Boolean = when (kind) {
    ItemKind.LOYALTY -> details is ItemDetails.Loyalty
    ItemKind.BOARDING_PASS -> details is ItemDetails.BoardingPass
    ItemKind.PASS -> details is ItemDetails.Pass
}

private fun WalletItem.toBackup() = BackupItem(
    id = id,
    kind = kind,
    title = title,
    subtitle = subtitle,
    barcode = barcode,
    brandColor = brandColor,
    logoPath = logoPath,
    details = details,
    createdAt = createdAt.toEpochMilliseconds(),
    lastUsedAt = lastUsedAt?.toEpochMilliseconds(),
    isFavorite = isFavorite,
    website = website,
    phone = phone,
)

private fun BackupItem.toDomain() = WalletItem(
    id = id,
    kind = kind,
    title = title,
    subtitle = subtitle,
    barcode = barcode,
    brandColor = brandColor,
    logoPath = logoPath,
    details = details,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    lastUsedAt = lastUsedAt?.let(Instant::fromEpochMilliseconds),
    isFavorite = isFavorite,
    website = website,
    phone = phone,
)
