package com.kkek.assistant.data.repository

import com.kkek.assistant.data.dao.ContactDao
import com.kkek.assistant.data.model.Contact
import com.kkek.assistant.repository.FirebaseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ContactRepositoryImpl @Inject constructor(
    private val contactDao: ContactDao,
    private val firebaseRepository: FirebaseRepository
) : ContactRepository {

    override fun getContacts(): Flow<List<Contact>> = contactDao.getContacts()

    override suspend fun refreshContacts() {
        contactDao.clearAll()
        val remoteContacts = firebaseRepository.getContacts()
        for (contact in remoteContacts) {
            contactDao.insert(contact)
        }
    }
}
