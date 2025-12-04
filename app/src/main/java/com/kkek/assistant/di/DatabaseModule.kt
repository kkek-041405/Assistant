package com.kkek.assistant.di

import android.content.Context
import androidx.room.Room
import com.kkek.assistant.data.database.AppDatabase
import com.kkek.assistant.data.dao.ContactDao
import com.kkek.assistant.repository.FirebaseRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "assistant-db"
        ).build()
    }

    @Provides
    fun provideContactDao(appDatabase: AppDatabase): ContactDao {
        return appDatabase.contactDao()
    }

    @Provides
    @Singleton
    fun provideFirebaseRepository(): FirebaseRepository {
        return FirebaseRepository
    }
}
