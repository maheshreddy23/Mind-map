package com.maheshreddy.mym.data.db

import androidx.room.migration.Migration

/**
 * Every migration, in order. MYM starts at schema version 1 (the Fresher Notes test data was not carried over).
 * Never use fallbackToDestructiveMigration: from here on, updates must keep the user's library.
 */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
