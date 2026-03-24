package app.repository

import app.network.FamilyMessengerApiClient
import app.storage.LocalDatabase
import app.dto.StoredContact
import com.familymessenger.contract.ContactSummary
import kotlinx.datetime.Clock

class ContactsRepository(
    private val apiClient: FamilyMessengerApiClient,
    private val localDatabase: LocalDatabase,
) {
    suspend fun fetchContacts(): List<ContactSummary> = apiClient.contacts().contacts

    suspend fun cachedContacts(): List<ContactSummary> =
        localDatabase.snapshot().contacts.map { it.contact }

    suspend fun refreshContacts(): List<ContactSummary> {
        val contacts = fetchContacts()
        localDatabase.update { snapshot ->
            snapshot.copy(contacts = contacts.map { StoredContact(it, Clock.System.now()) })
        }
        return contacts
    }
}
