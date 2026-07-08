package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RecentVideo::class], version = 2, exportSchema = false)
abstract class RecentVideoDatabase : RoomDatabase() {
    abstract fun recentVideoDao(): RecentVideoDao

    companion object {
        @Volatile
        private var INSTANCE: RecentVideoDatabase? = null

        fun getDatabase(context: Context): RecentVideoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RecentVideoDatabase::class.java,
                    "vlc_clone_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
