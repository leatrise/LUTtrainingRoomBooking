package com.trainingroom.book.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "image_cache")
data class ImageCacheEntity(
    @PrimaryKey
    val imageUrl: String,
    val cachedPath: String,
    val timestamp: Long = System.currentTimeMillis()
)
