package io.github.pxgray.clasp.data

import io.github.pxgray.clasp.domain.WalletItem
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface WalletRepository {
    fun observeItems(): Flow<List<WalletItem>>
    fun observeItem(id: String): Flow<WalletItem?>
    suspend fun upsert(item: WalletItem)

    /** Adds or replaces all [items] in a single transaction. */
    suspend fun upsertAll(items: List<WalletItem>)

    /** Removes the item and all of its files. */
    suspend fun delete(id: String)
    suspend fun markUsed(id: String, at: Instant)
    suspend fun setFavorite(id: String, favorite: Boolean)
}
