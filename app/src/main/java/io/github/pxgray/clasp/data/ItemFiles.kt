package io.github.pxgray.clasp.data

import io.github.pxgray.clasp.domain.WalletItem
import java.io.File

/**
 * Per-item files (logos now; pkpass images later) under `filesDir/items/<id>/`.
 * Paths handed to the rest of the app are relative to `filesDir`. Calls do blocking IO.
 */
class ItemFiles(private val filesDir: File) {

    fun resolve(relativePath: String): File = File(filesDir, relativePath)

    /** Logos get a unique name per import so an un-saved edit never overwrites the saved logo. */
    fun newLogoPath(itemId: String, stamp: Long): String = "$ITEMS_DIR/$itemId/$LOGO_PREFIX$stamp.png"

    fun write(relativePath: String, bytes: ByteArray) {
        val file = resolve(relativePath)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    /** Deletes every logo file of [itemId] except [keep]. Non-logo files are untouched. */
    fun pruneLogos(itemId: String, keep: String?) {
        itemDir(itemId).listFiles { file -> file.name.startsWith(LOGO_PREFIX) }
            ?.filter { "$ITEMS_DIR/$itemId/${it.name}" != keep }
            ?.forEach { it.delete() }
    }

    /**
     * Removes files written to preview an import of [itemId] that won't be saved. [saved] is the item
     * already in the wallet, if any: its logo stays; with none, the item's whole directory goes.
     */
    fun discardPreview(itemId: String, saved: WalletItem?) {
        if (saved == null) deleteAll(itemId) else pruneLogos(itemId, keep = saved.logoPath)
    }

    /** Deletes one logo file, and its item's directory if that leaves it empty. */
    fun deleteLogo(relativePath: String) {
        val file = resolve(relativePath)
        file.delete()
        file.parentFile?.takeIf { it.list()?.isEmpty() == true }?.delete()
    }

    fun deleteAll(itemId: String) {
        itemDir(itemId).deleteRecursively()
    }

    /** Relative paths of every file stored for [itemId]. */
    fun listFiles(itemId: String): List<String> =
        itemDir(itemId).listFiles { file -> file.isFile }.orEmpty().map { "$ITEMS_DIR/$itemId/${it.name}" }

    private fun itemDir(itemId: String) = File(filesDir, "$ITEMS_DIR/$itemId")

    private companion object {
        const val ITEMS_DIR = "items"
        const val LOGO_PREFIX = "logo-"
    }
}
