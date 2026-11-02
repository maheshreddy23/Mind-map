package com.freshernotes.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/** Schema history is exported to app/schemas; migrations live in Migrations.kt. */
@Database(
    entities = [SubjectEntity::class, TopicEntity::class, SessionEntity::class, TurnEntity::class, EntryEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun topicDao(): TopicDao
    abstract fun sessionDao(): SessionDao
    abstract fun turnDao(): TurnDao
    abstract fun entryDao(): EntryDao
}
