package com.simposio.taskeer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(entities = [BoardEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun boardDao(): BoardDao

    companion object {
        const val DATABASE_NAME: String = "taskeer.db"

        private val MIGRATIONS: List<Migration> = emptyList()

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .addMigrations(*MIGRATIONS.toTypedArray())
                .build()
    }
}
