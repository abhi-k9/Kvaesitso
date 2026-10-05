package de.mm20.launcher2.search

import de.mm20.launcher2.calculator.CalculatorRepository
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.search.data.Calculator
import de.mm20.launcher2.searchactions.SearchActionService
import de.mm20.launcher2.unitconverter.UnitConverterRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchServiceTest {

    private val app = mockk<Application>(relaxed = true) {
        every { key } returns "app://com.example:com.example.Main"
    }

    private val loggedErrors = mutableListOf<Exception>()
    private val errorLogged = CompletableDeferred<Unit>()

    @Before
    fun setUp() {
        mockkObject(CrashReporter)
        every { CrashReporter.logException(any()) } answers {
            loggedErrors += firstArg<Exception>()
            errorLogged.complete(Unit)
        }
    }

    @After
    fun tearDown() {
        unmockkObject(CrashReporter)
    }

    private class FakeRepository<T : Searchable>(
        private val results: Flow<List<T>>,
    ) : SearchableRepository<T> {
        override fun search(query: String, allowNetwork: Boolean) = results
    }

    private fun <T : Searchable> repository(results: Flow<List<T>>): SearchableRepository<T> =
        FakeRepository(results)

    private fun <T : Searchable> repository(vararg results: T): SearchableRepository<T> =
        FakeRepository(flowOf(results.toList()))

    private fun searchService(
        contacts: SearchableRepository<Contact> = repository(),
    ): SearchService {
        val customAttributes = mockk<CustomAttributesRepository> {
            every { search(any()) } returns flowOf(persistentListOf())
            every { getCustomLabels(any()) } returns flowOf(emptyList())
        }
        val searchActions = mockk<SearchActionService> {
            coEvery { search(any()) } returns flowOf(persistentListOf())
        }
        val calculator = mockk<CalculatorRepository> {
            every { search(any()) } returns flowOf(Calculator(term = "1+1", solution = 2.0))
        }
        val unitConverter = mockk<UnitConverterRepository> {
            every { search(any()) } returns flowOf(null)
        }
        return SearchServiceImpl(
            appRepository = repository(app),
            appShortcutRepository = repository(),
            calendarRepository = repository(),
            contactRepository = contacts,
            fileRepository = repository(),
            articleRepository = repository(),
            locationRepository = repository(),
            unitConverterRepository = unitConverter,
            calculatorRepository = calculator,
            websiteRepository = repository(),
            searchActionService = searchActions,
            customAttributesRepository = customAttributes,
            profileManager = mockk(relaxed = true),
        )
    }

    private fun SearchService.firstResults(
        filters: SearchFilters,
        predicate: (SearchResults) -> Boolean,
    ): SearchResults = runBlocking {
        withTimeout(5000) { search("1+1", filters).first(predicate) }
    }

    @Test
    fun combinesResultsOfAllSources() {
        val results = searchService().firstResults(SearchFilters()) {
            it.apps != null && it.calculators != null && it.contacts != null
        }
        assertEquals(listOf(app), results.apps)
        assertEquals(2.0, results.calculators!!.single().solution, 0.0)
        assertEquals(emptyList<Contact>(), results.contacts)
    }

    @Test
    fun skipsDisabledCategories() {
        val results = searchService().firstResults(SearchFilters(apps = false)) {
            it.calculators != null
        }
        assertNull(results.apps)
    }

    // A search source that throws (e.g. because of a bug in another app's content provider)
    // crashed the launcher. Its results should just be missing.
    @Test
    fun failingSourceDoesNotCrash() {
        val error = IllegalStateException("broken content provider")
        val service = searchService(contacts = repository(flow<List<Contact>> { throw error }))

        val uncaught = mutableListOf<Throwable>()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
        try {
            runBlocking {
                val latest = MutableStateFlow<SearchResults?>(null)
                val job = launch {
                    service.search("1+1", SearchFilters()).collect { latest.value = it }
                }
                withTimeout(5000) { errorLogged.await() }
                val results = withTimeout(5000) { latest.first { it?.apps != null } }!!
                job.cancel()

                assertEquals(listOf(app), results.apps)
                assertNull(results.contacts)
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
        assertTrue("uncaught: $uncaught", uncaught.isEmpty())
        // With coroutine debugging (e.g. assertions enabled), a copy with the original as cause
        // can be logged
        assertTrue(
            "logged: $loggedErrors",
            loggedErrors.any { it === error || it.cause === error },
        )
    }
}
