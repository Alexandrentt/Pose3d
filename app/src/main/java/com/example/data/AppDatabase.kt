package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ProjectState::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
