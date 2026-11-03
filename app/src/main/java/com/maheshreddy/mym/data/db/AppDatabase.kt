package com.maheshreddy.mym.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * MYM's database. Version 1 is the first released schema (com.maheshreddy.mym); every later change needs a Room
 * migration in Migrations.kt and an instrumented migration test. Schemas are exported to app/schemas.
 */
@Database(
    entities = [SubjectEntity::class, TopicEntity::class, SessionEntity::class, TurnEntity::class, EntryEntity::class, EntryVectorEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun topicDao(): TopicDao
    abstract fun sessionDao(): SessionDao
    abstract fun turnDao(): TurnDao
    abstract fun entryDao(): EntryDao
    abstract fun vectorDao(): VectorDao
}
