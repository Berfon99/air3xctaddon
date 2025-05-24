package com.xc.air3xctaddon

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xc.air3xctaddon.converters.Converters

@Database(entities = [EventConfig::class, EventEntity::class, Task::class], version = 11, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventConfigDao(): EventConfigDao
    abstract fun eventDao(): EventDao
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration 1→2: Initial schema with soundFile
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE event_configs_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        event TEXT NOT NULL,
                        soundFile TEXT NOT NULL,
                        volumeType TEXT NOT NULL,
                        volumePercentage INTEGER NOT NULL,
                        playCount INTEGER NOT NULL,
                        position INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO event_configs_new (id, event, soundFile, volumeType, volumePercentage, playCount, position)
                    SELECT id, event, soundFile, volumeType, volumePercentage, playCount, position
                    FROM event_configs
                """.trimIndent())
                database.execSQL("DROP TABLE event_configs")
                database.execSQL("ALTER TABLE event_configs_new RENAME TO event_configs")
            }
        }

        // Migration 2→3: Add events table
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        name TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        // Migration 3→4: Add category to events
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE events ADD COLUMN category TEXT")
            }
        }

        // Migration 4→5: Add taskType and taskData
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE event_configs ADD COLUMN taskType TEXT")
                database.execSQL("ALTER TABLE event_configs ADD COLUMN taskData TEXT")
                database.execSQL("""
                    UPDATE event_configs
                    SET taskType = 'Sound', taskData = soundFile
                    WHERE soundFile IS NOT NULL
                """.trimIndent())
            }
        }

        // Migration 5→6: Add telegramChatId
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE event_configs ADD COLUMN telegramChatId TEXT")
            }
        }

        // Migration 6→7: Add telegramGroupName
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE event_configs ADD COLUMN telegramGroupName TEXT")
            }
        }

        // Migration 7→8: Add telegramUsername
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE event_configs ADD COLUMN telegramUsername TEXT")
            }
        }

        // Migration 8→9: Remove telegramUsername
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE event_configs_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        event TEXT NOT NULL,
                        taskType TEXT,
                        taskData TEXT,
                        volumeType TEXT NOT NULL,
                        volumePercentage INTEGER NOT NULL,
                        playCount INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        telegramChatId TEXT,
                        telegramGroupName TEXT
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO event_configs_new (
                        id, event, taskType, taskData, volumeType, volumePercentage, playCount, position, telegramChatId, telegramGroupName
                    )
                    SELECT id, event, taskType, taskData, volumeType, volumePercentage, playCount, position, telegramChatId, telegramGroupName
                    FROM event_configs
                """.trimIndent())
                database.execSQL("DROP TABLE event_configs")
                database.execSQL("ALTER TABLE event_configs_new RENAME TO event_configs")
            }
        }

        // Migration 9→10: Add launchInBackground
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    ALTER TABLE event_configs ADD COLUMN launchInBackground INTEGER NOT NULL DEFAULT 1
                """.trimIndent())
            }
        }

        // Migration 10→11: Add tasks table
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        taskType TEXT NOT NULL,
                        taskData TEXT NOT NULL,
                        taskName TEXT NOT NULL,
                        launchInBackground INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "xct_addon_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}