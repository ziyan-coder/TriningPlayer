package com.example.trainingplayer.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.example.trainingplayer.model.*

@Database(
    entities = [
        PlanTemplate::class,
        PlanInstance::class,
        TimeSlot::class,
        DayContent::class,
        DayName::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao
    abstract fun instanceDao(): InstanceDao
    abstract fun timeSlotDao(): TimeSlotDao
    abstract fun dayContentDao(): DayContentDao
    abstract fun dayNameDao(): DayNameDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "training_player_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
