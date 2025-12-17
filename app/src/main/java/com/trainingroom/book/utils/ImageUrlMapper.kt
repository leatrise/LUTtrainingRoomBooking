package com.trainingroom.book.utils

import android.content.Context
import com.trainingroom.book.db.AppDatabase
import com.trainingroom.book.db.ImageCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageUrlMapper {
    private const val BASE_URL = "https://weixinlib.lut.edu.cn"

    private suspend fun getCachedUrl(
        context: Context,
        imageUrl: String
    ): String? {
        val db = AppDatabase.getDatabase(context)
        val cache = db.imageCacheDao().getCache(imageUrl)
        return cache?.cachedPath
    }

    private suspend fun saveCacheUrl(
        context: Context,
        imageUrl: String,
        fullUrl: String
    ) {
        val db = AppDatabase.getDatabase(context)
        db.imageCacheDao().insertCache(
            ImageCacheEntity(
                imageUrl = imageUrl,
                cachedPath = fullUrl
            )
        )
    }

    suspend fun getFullImageUrl(
        context: Context,
        relativeUrl: String
    ): String = withContext(Dispatchers.IO) {
        // 检查缓存
        val cached = getCachedUrl(context, relativeUrl)
        if (cached != null) {
            return@withContext cached
        }

        // 构建完整 URL
        val fullUrl = if (relativeUrl.startsWith("http")) {
            relativeUrl
        } else {
            BASE_URL + relativeUrl
        }

        // 保存到缓存
        saveCacheUrl(context, relativeUrl, fullUrl)

        return@withContext fullUrl
    }
}
