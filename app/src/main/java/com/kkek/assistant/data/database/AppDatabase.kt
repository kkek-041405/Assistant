package com.kkek.assistant.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kkek.assistant.data.dao.ContactDao
import com.kkek.assistant.data.model.Contact

@Database(entities = [Contact::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
}
