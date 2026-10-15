package com.simposio.taskeer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BoardEntity::class, ColumnEntity::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun boardDao(): BoardDao
    abstract fun columnDao(): ColumnDao

    companion object {
        const val DATABASE_NAME: String = "taskeer.db"

        private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE boards ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `columns` (" +
                        "`id` TEXT NOT NULL, " +
                        "`boardId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "`wipLimit` INTEGER, " +
                        "`isDoneColumn` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`pending` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }

        private val MIGRATIONS: List<Migration> = listOf(MIGRATION_1_2, MIGRATION_2_3)

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .addMigrations(*MIGRATIONS.toTypedArray())
                .build()
    }
}
