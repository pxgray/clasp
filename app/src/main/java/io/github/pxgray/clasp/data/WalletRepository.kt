package io.github.pxgray.clasp.data

import io.github.pxgray.clasp.domain.WalletItem
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface WalletRepository {
    fun observeItems(): Flow<List<WalletItem>>
    fun observeItem(id: String): Flow<WalletItem?>
    suspend fun upsert(item: WalletItem)

    /** Removes the item and all of its files. */
    suspend fun delete(id: String)
    suspend fun markUsed(id: String, at: Instant)
}
