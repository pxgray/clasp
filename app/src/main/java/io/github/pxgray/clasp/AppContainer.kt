package io.github.pxgray.clasp

import android.content.Context
import io.github.pxgray.clasp.data.AndroidLogoImporter
import io.github.pxgray.clasp.data.AndroidTakeoutImporter
import io.github.pxgray.clasp.data.ImportInbox
import io.github.pxgray.clasp.data.ItemFiles
import io.github.pxgray.clasp.data.LogoImporter
import io.github.pxgray.clasp.data.PassImporter
import io.github.pxgray.clasp.data.PkPassImporter
import io.github.pxgray.clasp.data.RoomWalletRepository
import io.github.pxgray.clasp.data.SettingsRepository
import io.github.pxgray.clasp.data.TakeoutImporter
import io.github.pxgray.clasp.data.backup.AndroidDocumentStreams
import io.github.pxgray.clasp.data.backup.DocumentStreams
import io.github.pxgray.clasp.data.backup.WalletBackup
import io.github.pxgray.clasp.data.WalletRepository
import io.github.pxgray.clasp.data.db.ClaspDatabase
import io.github.pxgray.clasp.logo.CompositeLogoProvider
import io.github.pxgray.clasp.logo.HttpFetcher
import io.github.pxgray.clasp.logo.LogoProvider
import io.github.pxgray.clasp.logo.SiteIconProvider
import io.github.pxgray.clasp.logo.UrlConnectionHttpFetcher
import io.github.pxgray.clasp.logo.WikidataLogoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

class AppContainer(context: Context) {
    private val database = ClaspDatabase.build(context)
    val clock: Clock = Clock.System
    val itemFiles = ItemFiles(context.filesDir)
    val settings = SettingsRepository.create(context)
    val repository: WalletRepository = RoomWalletRepository(database.walletDao(), itemFiles)
    val logoImporter: LogoImporter = AndroidLogoImporter(context.contentResolver, itemFiles, clock)
    val passImporter: PassImporter = PkPassImporter(context.contentResolver, itemFiles, clock)
    val takeoutImporter: TakeoutImporter = AndroidTakeoutImporter(context, itemFiles, clock)
    val importInbox = ImportInbox()

    /** For work that must finish after the screen that started it is gone, such as deleting preview files. */
    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val walletBackup = WalletBackup(repository, itemFiles)
    val documents: DocumentStreams = AndroidDocumentStreams(context.contentResolver)

    private val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /** Refuses every request unless the user has turned on logo search. */
    val httpFetcher: HttpFetcher = UrlConnectionHttpFetcher(
        isAllowed = { settings.settings.first().logoSearchEnabled },
        // Wikimedia asks API clients to identify themselves.
        userAgent = "Clasp/$versionName (https://github.com/pxgray/clasp)",
    )

    /** Website icons first: when the user gave a website, they are the likeliest exact match. */
    val logoProvider: LogoProvider = CompositeLogoProvider(
        listOf(SiteIconProvider(httpFetcher), WikidataLogoProvider(httpFetcher)),
    )
}
