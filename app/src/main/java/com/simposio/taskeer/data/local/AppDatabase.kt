package com.simposio.taskeer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BoardEntity::class,
        ColumnEntity::class,
        TaskEntity::class,
        SubtaskEntity::class,
        MyDayEntryEntity::class,
        ReminderEntity::class,
        AppNotificationEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun boardDao(): BoardDao
    abstract fun columnDao(): ColumnDao
    abstract fun taskDao(): TaskDao
    abstract fun subtaskDao(): SubtaskDao
    abstract fun myDayDao(): MyDayDao
    abstract fun reminderDao(): ReminderDao
    abstract fun appNotificationDao(): AppNotificationDao

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

        private val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tasks` (" +
                        "`id` TEXT NOT NULL, " +
                        "`columnId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`description` TEXT, " +
                        "`priority` TEXT NOT NULL, " +
                        "`dueAt` INTEGER, " +
                        "`assigneeId` TEXT, " +
                        "`position` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`pending` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tasks_columnId_position` " +
                        "ON `tasks` (`columnId`, `position`)",
                )
            }
        }

        private val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subtasks` (" +
                        "`id` TEXT NOT NULL, " +
                        "`taskId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`completed` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`pending` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_subtasks_taskId_position` " +
                        "ON `subtasks` (`taskId`, `position`)",
                )
            }
        }

        // El DEFAULT 0 lo exige SQLite para ADD COLUMN NOT NULL; updatedAt es la mejor
        // aproximación disponible porque la creación real de datos previos no se guardó.
        private val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subtasks ADD COLUMN assigneeId TEXT")
                db.execSQL("ALTER TABLE subtasks ADD COLUMN dueAt INTEGER")
                db.execSQL("ALTER TABLE columns ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE columns SET createdAt = updatedAt")
                db.execSQL("ALTER TABLE tasks ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE tasks SET createdAt = updatedAt")
                db.execSQL("ALTER TABLE subtasks ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE subtasks SET createdAt = updatedAt")
            }
        }

        private val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `my_day_tasks` (" +
                        "`id` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`pending` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }

        private val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Literal y no LocalUser.ID: una migración no debe cambiar si el código evoluciona.
                db.execSQL(
                    "UPDATE tasks SET assigneeId = 'local-user', pending = 1, " +
                        "updatedAt = ${System.currentTimeMillis()} " +
                        "WHERE assigneeId IS NULL AND deletedAt IS NULL",
                )
            }
        }

        private val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reminders` (" +
                        "`id` TEXT NOT NULL, " +
                        "`taskId` TEXT NOT NULL, " +
                        "`remindAt` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`sentAt` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`pending` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminders_status_remindAt` " +
                        "ON `reminders` (`status`, `remindAt`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminders_taskId` " +
                        "ON `reminders` (`taskId`)",
                )
            }
        }

        private val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_notifications` (" +
                        "`id` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`taskId` TEXT, " +
                        "`taskTitle` TEXT, " +
                        "`activeTaskCount` INTEGER, " +
                        "`delivered` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`readAt` INTEGER, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`pending` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_app_notifications_deletedAt_createdAt` " +
                        "ON `app_notifications` (`deletedAt`, `createdAt`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_app_notifications_deletedAt_readAt` " +
                        "ON `app_notifications` (`deletedAt`, `readAt`)",
                )
            }
        }

        private val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            // SQLite no puede quitar NOT NULL con ALTER TABLE: se reconstruye la tabla. Copia también
            // las filas borradas lógicamente y no toca updatedAt/pending (no es un cambio del usuario).
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tasks_new` (" +
                        "`id` TEXT NOT NULL, " +
                        "`columnId` TEXT, " +
                        "`title` TEXT NOT NULL, " +
                        "`description` TEXT, " +
                        "`priority` TEXT NOT NULL, " +
                        "`dueAt` INTEGER, " +
                        "`assigneeId` TEXT, " +
                        "`position` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`pending` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "INSERT INTO `tasks_new` (id, columnId, title, description, priority, dueAt, " +
                        "assigneeId, position, createdAt, updatedAt, pending, deletedAt) " +
                        "SELECT id, columnId, title, description, priority, dueAt, assigneeId, " +
                        "position, createdAt, updatedAt, pending, deletedAt FROM `tasks`",
                )
                db.execSQL("DROP TABLE `tasks`")
                db.execSQL("ALTER TABLE `tasks_new` RENAME TO `tasks`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tasks_columnId_position` " +
                        "ON `tasks` (`columnId`, `position`)",
                )
            }
        }

        private val MIGRATIONS: List<Migration> = listOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
        )

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .addMigrations(*MIGRATIONS.toTypedArray())
                .build()
    }
}
