package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_videos")
data class RecentVideo(
    @PrimaryKey val uriString: String,
    val displayName: String,
    val durationMs: Long,
    val lastPositionMs: Long,
    val thumbnailPath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
