package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProxyDao {
    @Query("SELECT * FROM proxies ORDER BY isDefault DESC, id DESC")
    fun getAllProxies(): Flow<List<ProxyEntity>>

    @Query("SELECT * FROM proxies WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProxy(): ProxyEntity?

    @Query("SELECT * FROM proxies WHERE id = :id LIMIT 1")
    suspend fun getProxyById(id: Long): ProxyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProxy(proxy: ProxyEntity): Long

    @Update
    suspend fun updateProxy(proxy: ProxyEntity)

    @Delete
    suspend fun deleteProxy(proxy: ProxyEntity)

    @Query("DELETE FROM proxies WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE proxies SET isDefault = 0")
    suspend fun clearDefault()

    @Query("UPDATE proxies SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: Long)

    @Query("UPDATE proxies SET pingMs = :pingMs WHERE id = :id")
    suspend fun updatePing(id: Long, pingMs: Long)

    @Query("SELECT COUNT(*) FROM proxies")
    suspend fun count(): Int
}
