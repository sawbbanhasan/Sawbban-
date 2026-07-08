package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentVideoDao {
    @Query("SELECT * FROM recent_videos ORDER BY timestamp DESC")
    fun getAllRecentVideos(): Flow<List<RecentVideo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentVideo(recentVideo: RecentVideo)

    @Query("DELETE FROM recent_videos WHERE uriString = :uriString")
    suspend fun deleteByUri(uriString: String)

    @Query("DELETE FROM recent_videos")
    suspend fun clearAllHistory()
}
