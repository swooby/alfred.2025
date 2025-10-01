package com.swooby.alfred.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [EventEntity::class], version = 1, exportSchema = true)
@TypeConverters(RoomConverters::class)
abstract class AlfredDb : RoomDatabase() {
    abstract fun events(): EventDao

    companion object {
        fun open(context: Context): AlfredDb =
            Room.databaseBuilder(context, AlfredDb::class.java, "alfred.db")
                .addTypeConverter(RoomConverters())
                .fallbackToDestructiveMigration()
                .build()
    }
}