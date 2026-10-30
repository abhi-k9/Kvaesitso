package de.mm20.launcher2.contacts.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidContactProviderTest {
    // Run the provider's selections against SQLite rather than assuming that its query matched.
    // Android's optional name filter returns no extra matches in this fixture.
    class ContactsProvider : ContentProvider() {
        lateinit var database: SQLiteDatabase

        override fun onCreate(): Boolean {
            database = SQLiteDatabase.create(null)
            database.execSQL(
                """CREATE TABLE raw_contacts (
                    _id INTEGER PRIMARY KEY, contact_id INTEGER,
                    display_name TEXT, display_name_alt TEXT, phonetic_name TEXT, sort_key TEXT,
                    account_type_and_data_set TEXT
                )"""
            )
            database.execSQL(
                """CREATE TABLE data (
                    _id INTEGER PRIMARY KEY, contact_id INTEGER, raw_contact_id INTEGER,
                    mimetype TEXT, data1 TEXT, data2 TEXT, data3 TEXT
                )"""
            )
            database.execSQL("CREATE TABLE contacts (_id INTEGER PRIMARY KEY, lookup TEXT)")
            return true
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            if (uri.pathSegments.take(2) == listOf("contacts", "filter")) {
                return MatrixCursor(arrayOf(ContactsContract.Contacts._ID))
            }
            return database.query(
                uri.pathSegments.first(), projection, selection, selectionArgs, null, null, sortOrder
            )
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ) = 0
    }

    private lateinit var contacts: ContactsProvider
    private lateinit var provider: AndroidContactProvider

    @Before
    fun setUp() {
        contacts = Robolectric.buildContentProvider(ContactsProvider::class.java)
            .create(ContactsContract.AUTHORITY).get()
        provider = AndroidContactProvider(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        contacts.database.close()
    }

    private fun contact(
        id: Long,
        company: String,
        displayName: String? = null,
        firstName: String? = null,
        lastName: String? = null,
        rawDisplayName: String? = displayName,
    ) {
        contacts.database.insertOrThrow("contacts", null, ContentValues().apply {
            put("_id", id)
            put("lookup", "lookup-$id")
        })
        contacts.database.insertOrThrow("raw_contacts", null, ContentValues().apply {
            put("_id", id)
            put("contact_id", id)
            put("display_name", rawDisplayName)
        })
        contacts.database.insertOrThrow("data", null, ContentValues().apply {
            put("_id", id * 100)
            put("contact_id", id)
            put("raw_contact_id", id)
            put("mimetype", ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
            put("data1", company)
        })
        if (displayName != null || firstName != null || lastName != null) {
            contacts.database.insertOrThrow("data", null, ContentValues().apply {
                put("_id", id * 100 + 1)
                put("contact_id", id)
                put("raw_contact_id", id)
                put("mimetype", ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                put("data1", displayName)
                put("data2", firstName)
                put("data3", lastName)
            })
        }
    }

    private fun search(query: String) = runBlocking { provider.search(query, allowNetwork = false) }

    @Test
    fun companyOnlyContactHasAUsableNameAndIsNotDuplicated() {
        contact(1, company = "Acme Services", rawDisplayName = "Acme Services")

        val results = search("acme")

        assertEquals(listOf("Acme Services"), results.map { it.name })
        assertEquals("contact://1", results.single().key)
    }

    @Test
    fun companyIsSearchedEvenWhenItIsNotInTheRawDisplayName() {
        contact(1, company = "Acme Services", displayName = "Alice Example")
        contact(2, company = "Other Company", displayName = "Bob Example")

        assertEquals(listOf("Alice Example"), search("acme").map { it.name })
    }

    @Test
    fun companySearchIncludesLinkedPersonalNameAndActionsOnce() {
        // The organization matches both the name query and the organization query, while the
        // linked raw contact holds all the personal details and does not match either query.
        contact(1, company = "Acme Services", rawDisplayName = "Acme Services")
        contacts.database.insertOrThrow("raw_contacts", null, ContentValues().apply {
            put("_id", 2L)
            put("contact_id", 1L)
            put("display_name", "Alice Example")
            put("account_type_and_data_set", "example.messenger")
        })
        fun detail(id: Long, mimeType: String, value: String, label: String? = null) {
            contacts.database.insertOrThrow("data", null, ContentValues().apply {
                put("_id", id)
                put("contact_id", 1L)
                put("raw_contact_id", 2L)
                put("mimetype", mimeType)
                put("data1", value)
                put("data3", label)
            })
        }
        detail(201, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, "Alice Example")
        detail(202, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, "+12025550123")
        detail(203, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, "alice@example.com")
        detail(204, "vnd.example.messenger.call", "Alice", label = "Call with Example")

        val result = search("acme").single()

        assertEquals("Alice Example", result.name)
        assertEquals("contact://1", result.key)
        assertEquals("12025550123", result.phoneNumbers.single().number.filter { it.isDigit() })
        assertEquals("alice@example.com", result.emailAddresses.single().address)
        assertEquals("Call with Example", result.customActions.single().label)
        assertEquals("example.messenger", result.customActions.single().packageName)
    }

    @Test
    fun blankStructuredNameFallsBackToCompany() {
        contact(1, company = "Acme Services", displayName = " ", firstName = "", lastName = "")

        assertEquals("Acme Services", search("acme").single().name)
    }

    @Test
    fun personalNameStillTakesPrecedenceOverCompany() {
        contact(1, company = "Acme Services", firstName = "Alice", lastName = "Example")

        assertEquals("Alice Example", search("acme").single().name)
    }

    @Test
    fun savedCompanyOnlyContactCanBeLoadedById() = runBlocking {
        contact(1, company = "Acme Services")

        assertEquals("Acme Services", provider.get(1)?.name)
    }

    @Test
    fun unrelatedContactsAreNotReturned() {
        contact(1, company = "Acme Services")

        assertTrue(search("unrelated").isEmpty())
    }

    @Test
    fun companyNamesWithApostrophesUseBoundQueryArguments() {
        contact(1, company = "O'Reilly")
        contact(2, company = "Other Company")

        assertEquals(listOf("O'Reilly"), search("O'Rei").map { it.name })
        assertTrue(search("missing' OR 1=1 --").isEmpty())
    }
}
