package com.freshernotes.app.di

import android.content.Context
import androidx.room.Room
import com.freshernotes.app.data.db.AppDatabase
import com.freshernotes.app.data.db.MIGRATION_1_2
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "fresher.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun topicDao(db: AppDatabase) = db.topicDao()
    @Provides fun sessionDao(db: AppDatabase) = db.sessionDao()
    @Provides fun turnDao(db: AppDatabase) = db.turnDao()
}
