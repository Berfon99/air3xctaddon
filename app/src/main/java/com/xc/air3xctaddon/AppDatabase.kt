package com.xc.air3xctaddon

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xc.air3xctaddon.converters.Converters

@Database(entities = [EventConfig::class, EventEntity::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventConfigDao(): EventConfigDao
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create a new table with event as TEXT
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
                // Copy data from old table, keeping event values as-is
                database.execSQL("""
                    INSERT INTO event_configs_new (id, event, soundFile, volumeType, volumePercentage, playCount, position)
                    SELECT id, event, soundFile, volumeType, volumePercentage, playCount, position
                    FROM event_configs
                """.trimIndent())
                // Drop old table and rename new one
                database.execSQL("DROP TABLE event_configs")
                database.execSQL("ALTER TABLE event_configs_new RENAME TO event_configs")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create the new events table
                database.execSQL("""
                    CREATE TABLE events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        name TEXT NOT NULL
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}