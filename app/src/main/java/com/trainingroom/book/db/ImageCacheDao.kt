package com.trainingroom.book.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ImageCacheDao {
    @Query("SELECT * FROM image_cache WHERE imageUrl = :url")
    suspend fun getCache(url: String): ImageCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: ImageCacheEntity)

    @Query("DELETE FROM image_cache WHERE imageUrl = :url")
    suspend fun deleteCache(url: String)

    @Query("DELETE FROM image_cache")
    suspend fun clearAll()
}
