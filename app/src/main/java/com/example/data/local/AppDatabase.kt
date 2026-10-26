package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ProjectEntity::class], version = 2, exportSchema = false)
@TypeConverters(SubtitleConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE video_projects ADD COLUMN currentStage TEXT")
                database.execSQL("ALTER TABLE video_projects ADD COLUMN progressPercent INTEGER")
                database.execSQL("ALTER TABLE video_projects ADD COLUMN statusMessage TEXT")
                database.execSQL("ALTER TABLE video_projects ADD COLUMN errorMessage TEXT")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "subvid_database"
                ).addMigrations(MIGRATION_1_2).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
