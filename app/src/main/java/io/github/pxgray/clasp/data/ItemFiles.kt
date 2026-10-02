package io.github.pxgray.clasp.data

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

    fun deleteAll(itemId: String) {
        itemDir(itemId).deleteRecursively()
    }

    private fun itemDir(itemId: String) = File(filesDir, "$ITEMS_DIR/$itemId")

    private companion object {
        const val ITEMS_DIR = "items"
        const val LOGO_PREFIX = "logo-"
    }
}
