package io.github.pxgray.clasp.data

import io.github.pxgray.clasp.data.db.WalletDao
import io.github.pxgray.clasp.data.db.toDomain
import io.github.pxgray.clasp.data.db.toEntity
import io.github.pxgray.clasp.domain.WalletItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Instant

class RoomWalletRepository(
    private val dao: WalletDao,
    private val files: ItemFiles,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WalletRepository {

    override fun observeItems(): Flow<List<WalletItem>> =
        dao.observeAll().map { items -> items.map { it.toDomain() } }

    override fun observeItem(id: String): Flow<WalletItem?> =
        dao.observe(id).map { it?.toDomain() }

    override suspend fun upsert(item: WalletItem) = dao.upsert(item.toEntity())

    override suspend fun upsertAll(items: List<WalletItem>) = dao.upsertAll(items.map { it.toEntity() })

    override suspend fun delete(id: String) {
        dao.delete(id)
        withContext(ioDispatcher) { files.deleteAll(id) }
    }

    override suspend fun markUsed(id: String, at: Instant) = dao.markUsed(id, at.toEpochMilliseconds())

    override suspend fun setFavorite(id: String, favorite: Boolean) = dao.setFavorite(id, favorite)
}
