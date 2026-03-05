package com.cloudacr.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(
    entities = [Recording::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(AppDatabase.Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recordingDao(): RecordingDao

    class Converters {
        @TypeConverter
        fun fromCallType(value: Recording.CallType): String = value.name

        @TypeConverter
        fun toCallType(value: String): Recording.CallType =
            Recording.CallType.valueOf(value)
    }

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cloudacr_database"
                ).build().also { INSTANCE = it }
            }
    }
}
