package com.example.data

import kotlinx.coroutines.flow.Flow

class RecentVideoRepository(private val recentVideoDao: RecentVideoDao) {
    val allRecentVideos: Flow<List<RecentVideo>> = recentVideoDao.getAllRecentVideos()

    suspend fun insertRecentVideo(recentVideo: RecentVideo) {
        recentVideoDao.insertRecentVideo(recentVideo)
    }

    suspend fun deleteRecentVideo(uriString: String) {
        recentVideoDao.deleteByUri(uriString)
    }

    suspend fun clearHistory() {
        recentVideoDao.clearAllHistory()
    }
}
