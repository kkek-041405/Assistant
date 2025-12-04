package com.kkek.assistant.di

import com.kkek.assistant.data.repository.ContactRepository
import com.kkek.assistant.data.repository.ContactRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindContactRepository(impl: ContactRepositoryImpl): ContactRepository
}
