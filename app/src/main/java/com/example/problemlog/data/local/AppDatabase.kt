package com.example.problemlog.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ProblemEntity::class, AttemptEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun problemDao(): ProblemDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        // one database object for the whole app
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "problemlog.db"
                ).build().also { instance = it }
            }
    }
}
