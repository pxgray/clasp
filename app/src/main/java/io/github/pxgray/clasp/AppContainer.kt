package io.github.pxgray.clasp

import android.content.Context
import io.github.pxgray.clasp.data.AndroidLogoImporter
import io.github.pxgray.clasp.data.ImportInbox
import io.github.pxgray.clasp.data.ItemFiles
import io.github.pxgray.clasp.data.LogoImporter
import io.github.pxgray.clasp.data.PassImporter
import io.github.pxgray.clasp.data.PkPassImporter
import io.github.pxgray.clasp.data.RoomWalletRepository
import io.github.pxgray.clasp.data.SettingsRepository
import io.github.pxgray.clasp.data.backup.AndroidDocumentStreams
import io.github.pxgray.clasp.data.backup.DocumentStreams
import io.github.pxgray.clasp.data.backup.WalletBackup
import io.github.pxgray.clasp.data.WalletRepository
import io.github.pxgray.clasp.data.db.ClaspDatabase
import kotlin.time.Clock

class AppContainer(context: Context) {
    private val database = ClaspDatabase.build(context)
    val clock: Clock = Clock.System
    val itemFiles = ItemFiles(context.filesDir)
    val settings = SettingsRepository.create(context)
    val repository: WalletRepository = RoomWalletRepository(database.walletDao(), itemFiles)
    val logoImporter: LogoImporter = AndroidLogoImporter(context.contentResolver, itemFiles, clock)
    val passImporter: PassImporter = PkPassImporter(context.contentResolver, itemFiles, clock)
    val importInbox = ImportInbox()
    val walletBackup = WalletBackup(repository, itemFiles)
    val documents: DocumentStreams = AndroidDocumentStreams(context.contentResolver)
}
