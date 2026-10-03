package io.github.pxgray.clasp.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallet_items")
    fun observeAll(): Flow<List<WalletItemEntity>>

    @Query("SELECT * FROM wallet_items WHERE id = :id")
    fun observe(id: String): Flow<WalletItemEntity?>

    @Upsert
    suspend fun upsert(item: WalletItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<WalletItemEntity>)

    @Query("DELETE FROM wallet_items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE wallet_items SET lastUsedAt = :at WHERE id = :id")
    suspend fun markUsed(id: String, at: Long)

    @Query("UPDATE wallet_items SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)
}
