package com.freshernotes.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TopicEntity::class, SessionEntity::class, TurnEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun topicDao(): TopicDao
    abstract fun sessionDao(): SessionDao
    abstract fun turnDao(): TurnDao
}
