package com.freshernotes.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TopicEntity::class, SessionEntity::class, TurnEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun topicDao(): TopicDao
    abstract fun sessionDao(): SessionDao
    abstract fun turnDao(): TurnDao
}

/** v2: per-session notes and the topic they were merged into. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sessions ADD COLUMN notesStatus TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE sessions ADD COLUMN notesMarkdown TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sessions ADD COLUMN topicId INTEGER")
    }
}
