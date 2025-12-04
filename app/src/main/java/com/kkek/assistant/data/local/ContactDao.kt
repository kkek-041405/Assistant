package com.kkek.assistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<Contact>)

    @Query("SELECT * FROM contacts")
    fun getAllContacts(): Flow<List<Contact>>
    
    @Query("DELETE FROM contacts")
    suspend fun clearAll()
}
