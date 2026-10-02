package io.github.pxgray.clasp.testing

import io.github.pxgray.clasp.data.WalletRepository
import io.github.pxgray.clasp.domain.WalletItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

class FakeWalletRepository(initial: List<WalletItem> = emptyList()) : WalletRepository {
    val items = MutableStateFlow(initial)
    val markedUsed = mutableListOf<Pair<String, Instant>>()

    override fun observeItems(): Flow<List<WalletItem>> = items

    override fun observeItem(id: String): Flow<WalletItem?> = items.map { list -> list.find { it.id == id } }

    override suspend fun upsert(item: WalletItem) =
        items.update { list -> list.filterNot { it.id == item.id } + item }

    override suspend fun delete(id: String) = items.update { list -> list.filterNot { it.id == id } }

    override suspend fun markUsed(id: String, at: Instant) {
        markedUsed += id to at
        items.update { list -> list.map { if (it.id == id) it.copy(lastUsedAt = at) else it } }
    }
}
