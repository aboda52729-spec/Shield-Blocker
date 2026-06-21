package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BlockLog::class, ShieldSettings::class], version = 2, exportSchema = false)
abstract class ShieldDatabase : RoomDatabase() {
    abstract fun shieldDao(): ShieldDao

    companion object {
        @Volatile
        private var INSTANCE: ShieldDatabase? = null

        fun getDatabase(context: Context): ShieldDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShieldDatabase::class.java,
                    "shield_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
