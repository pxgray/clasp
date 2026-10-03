package io.github.pxgray.clasp.screenshots

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.pxgray.clasp.data.AppSettings
import io.github.pxgray.clasp.domain.Barcode
import io.github.pxgray.clasp.domain.BarcodeFormat
import io.github.pxgray.clasp.domain.ItemDetails
import io.github.pxgray.clasp.domain.ItemKind
import io.github.pxgray.clasp.domain.PassField
import io.github.pxgray.clasp.domain.PkPassStyle
import io.github.pxgray.clasp.domain.WalletItem
import io.github.pxgray.clasp.testing.boardingItem
import io.github.pxgray.clasp.testing.loyaltyItem
import io.github.pxgray.clasp.ui.detail.DetailScreen
import io.github.pxgray.clasp.ui.edit.EditFormState
import io.github.pxgray.clasp.ui.edit.EditScreen
import io.github.pxgray.clasp.ui.home.HomeScreen
import io.github.pxgray.clasp.ui.home.HomeUiState
import io.github.pxgray.clasp.ui.scan.CameraPermission
import io.github.pxgray.clasp.ui.scan.ScanScreen
import io.github.pxgray.clasp.ui.settings.SettingsScreen
import io.github.pxgray.clasp.ui.takeout.TakeoutImportScreen
import io.github.pxgray.clasp.ui.takeout.TakeoutImportState
import io.github.pxgray.clasp.ui.takeout.TakeoutRow
import io.github.pxgray.clasp.ui.takeout.TakeoutRowStatus
import io.github.pxgray.clasp.ui.theme.ClaspTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/**
 * Golden images of every screen, in light and dark. Record with `./gradlew recordRoborazziDebug`,
 * check with `./gradlew verifyRoborazziDebug`. Plain `testDebugUnitTest` runs skip the comparison.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { ClaspTheme(darkTheme = dark, dynamicColor = false, content = content) }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private val wallet = HomeUiState(
        loading = false,
        passes = listOf(boardingItem(), concert),
        cards = listOf(
            loyaltyItem(id = "c1", title = "Costco", cardNumber = "111222333444", brandColor = 0xFFD32F2F.toInt()),
            loyaltyItem(id = "c2", title = "Blue Bottle Coffee", memberName = "Jane Doe", brandColor = 0xFF1565C0.toInt()),
            loyaltyItem(id = "c3", title = "Library card", cardNumber = "29384756"),
        ),
    )

    private val walletWithFavorites = wallet.copy(
        favorites = listOf(wallet.cards.first().copy(isFavorite = true), concert.copy(isFavorite = true)),
        passes = listOf(boardingItem()),
        cards = wallet.cards.drop(1),
    )

    @Test fun homeEmpty() = snap("home_empty") { HomeContent(HomeUiState(loading = false)) }
    @Test fun homeLight() = snap("home") { HomeContent(wallet) }
    @Test fun homeDark() = snap("home_dark", dark = true) { HomeContent(wallet) }
    @Test fun homeFavorites() = snap("home_favorites") { HomeContent(walletWithFavorites) }
    @Test fun homeFavoritesDark() = snap("home_favorites_dark", dark = true) { HomeContent(walletWithFavorites) }

    /** The menu is a popup window, so capture the whole screen rather than the compose root. */
    @Test fun homeSortMenu() {
        compose.setContent { ClaspTheme(darkTheme = false, dynamicColor = false) { HomeContent(wallet) } }
        compose.onNodeWithContentDescription("Sort").performClick()
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/home_sort_menu.png")
    }

    @Test fun detailLoyaltyLight() = snap("detail_loyalty") { DetailContent(wallet.cards.first()) }
    @Test fun detailLoyaltyDark() = snap("detail_loyalty_dark", dark = true) { DetailContent(wallet.cards.first()) }
    @Test fun detailBoardingPass() = snap("detail_boarding_pass") { DetailContent(boardingItem().copy(brandColor = 0xFFC62828.toInt())) }
    @Test fun detailPass() = snap("detail_pass") { DetailContent(concert) }

    @Test fun editLoyalty() = snap("edit_loyalty") { EditContent(EditFormState.from(wallet.cards.first())) }
    @Test fun editBoardingPass() = snap("edit_boarding_pass") { EditContent(EditFormState.from(boardingItem())) }

    @Test fun scanPermission() = snap("scan_permission") {
        ScanScreen(CameraPermission.NOT_GRANTED, onRequestPermission = {}, onOpenSettings = {}, onBarcode = {}, onClose = {})
    }

    @Test fun takeoutImport() = snap("takeout_import") {
        TakeoutImportScreen(
            TakeoutImportState.Ready(
                rows = listOf(
                    TakeoutRow(wallet.cards[0], TakeoutRowStatus.NEW),
                    TakeoutRow(wallet.cards[1], TakeoutRowStatus.UPDATE),
                    TakeoutRow(wallet.cards[2], TakeoutRowStatus.DUPLICATE),
                    TakeoutRow(wallet.passes[0], TakeoutRowStatus.PAST),
                ),
                selected = setOf(wallet.cards[0].id, wallet.cards[1].id),
            ),
            onToggle = {},
            onConfirm = {},
            onCancel = {},
        )
    }

    @Test fun settingsLight() = snap("settings") { SettingsContent() }
    @Test fun settingsDark() = snap("settings_dark", dark = true) { SettingsContent() }

    @Composable
    private fun HomeContent(state: HomeUiState) = HomeScreen(
        state, SnackbarHostState(), onOpenItem = {}, onScan = {}, onImport = {}, onImportGoogleWallet = {},
        onAddManually = {}, onOpenSettings = {}, onSortOrderChange = {},
        onFavoriteChange = { _, _ -> },
    )

    @Composable
    private fun DetailContent(item: WalletItem) = DetailScreen(item, onBack = {}, onEdit = {}, onDelete = {})

    @Composable
    private fun EditContent(state: EditFormState) =
        EditScreen(state, SnackbarHostState(), onUpdate = {}, onPickLogo = {}, onSave = {}, onCancel = {})

    @Composable
    private fun SettingsContent() = SettingsScreen(
        settings = AppSettings(),
        busy = false,
        versionName = "0.1.0",
        showDynamicColor = true,
        snackbarHostState = SnackbarHostState(),
        onBack = {},
        onTheme = {},
        onDynamicColor = {},
        onMaxBrightness = {},
        onExport = {},
        onImport = {},
        onLicenses = {},
    )

    private companion object {
        val concert = WalletItem(
            id = "concert",
            kind = ItemKind.PASS,
            title = "Riverside Hall",
            subtitle = "Concert ticket",
            barcode = Barcode("RH-2026-1122-B12", BarcodeFormat.QR, altText = "B12 · Row 4"),
            brandColor = 0xFF6A1B9A.toInt(),
            details = ItemDetails.Pass(
                style = PkPassStyle.EVENT_TICKET,
                headerFields = listOf(PassField("date", "DATE", "Nov 22, 2026, 7:30 PM")),
                primaryFields = listOf(PassField("event", "EVENT", "The Midnight Orchestra")),
                secondaryFields = listOf(PassField("section", "SECTION", "B"), PassField("row", "ROW", "4"), PassField("seat", "SEAT", "12")),
                auxiliaryFields = listOf(PassField("doors", "DOORS", "6:30 PM"), PassField("price", "PRICE", "$48.00")),
                backFields = listOf(PassField("terms", "TERMS", "No refunds or exchanges.")),
            ),
            createdAt = Instant.fromEpochMilliseconds(2_000),
        )
    }
}
