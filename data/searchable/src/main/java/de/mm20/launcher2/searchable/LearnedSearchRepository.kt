package de.mm20.launcher2.searchable

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import de.mm20.launcher2.preferences.search.RankingSettings
import de.mm20.launcher2.search.LearnedSearch
import de.mm20.launcher2.search.LearnedSearches
import de.mm20.launcher2.search.StringNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Learns which search results are opened after typing a query, so that they come first the next
 * time it is typed (see [LearnedSearches]).
 */
interface LearnedSearchRepository {
    /**
     * Everything that has been learned, most recently used first
     */
    val entries: Flow<List<LearnedSearch>>

    /**
     * The search that is currently shown: results that are opened while it is shown are learned
     * for its query.
     */
    fun setCurrentSearch(query: String, resultKeys: Set<String>)

    /**
     * An item has been opened: if it's a result of the current search, it's learned for its
     * query.
     */
    fun reportLaunch(key: String)

    /**
     * The results learned for [query], by key, with their position: 0 comes first.
     */
    suspend fun getRanks(query: String): Map<String, Int>

    fun remove(entry: LearnedSearch)

    fun clear()
}

internal class LearnedSearchRepositoryImpl(
    private val context: Context,
    private val stringNormalizer: StringNormalizer,
    private val settings: RankingSettings,
) : LearnedSearchRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val file by lazy { AtomicFile(File(context.filesDir, FileName)) }

    /**
     * Null until the file has been read
     */
    private val state = MutableStateFlow<List<LearnedSearch>?>(null)

    @Volatile
    private var currentSearch: CurrentSearch = CurrentSearch("", emptySet())

    private class CurrentSearch(val query: String, val resultKeys: Set<String>)

    override val entries: Flow<List<LearnedSearch>> = flow {
        load()
        emitAll(state.filterNotNull())
    }

    override fun setCurrentSearch(query: String, resultKeys: Set<String>) {
        currentSearch = CurrentSearch(clean(query), resultKeys)
    }

    override fun reportLaunch(key: String) {
        val search = currentSearch
        if (search.query.isEmpty() || key !in search.resultKeys) return
        val time = System.currentTimeMillis()
        scope.launch {
            if (!settings.learnFromLaunches.first()) return@launch
            update { LearnedSearches.record(it, search.query, key, time) }
        }
    }

    override suspend fun getRanks(query: String): Map<String, Int> {
        if (!settings.learnFromLaunches.first()) return emptyMap()
        return LearnedSearches.ranks(load(), clean(query))
    }

    override fun remove(entry: LearnedSearch) {
        scope.launch {
            update { entries ->
                entries.filterNot { it.query == entry.query && it.key == entry.key }
            }
        }
    }

    override fun clear() {
        scope.launch {
            update { emptyList() }
        }
    }

    private fun clean(query: String): String {
        return LearnedSearches.cleanQuery(stringNormalizer.normalize(query))
    }

    private suspend fun load(): List<LearnedSearch> {
        state.value?.let { return it }
        return mutex.withLock {
            state.value ?: withContext(Dispatchers.IO) { read() }.also { state.value = it }
        }
    }

    private suspend fun update(block: (List<LearnedSearch>) -> List<LearnedSearch>) {
        load()
        mutex.withLock {
            val updated = block(state.value ?: emptyList())
            state.value = updated
            withContext(Dispatchers.IO) { write(updated) }
        }
    }

    private fun read(): List<LearnedSearch> {
        if (!file.baseFile.exists()) return emptyList()
        return try {
            val json = JSONArray(String(file.readFully()))
            (0 until json.length()).mapNotNull { i ->
                val entry = json.optJSONObject(i) ?: return@mapNotNull null
                LearnedSearch(
                    query = entry.optString("query").ifEmpty { return@mapNotNull null },
                    key = entry.optString("key").ifEmpty { return@mapNotNull null },
                    count = entry.optInt("count", 1),
                    lastUsed = entry.optLong("lastUsed"),
                )
            }
        } catch (e: IOException) {
            Log.e("MM20", "Couldn't read learned searches", e)
            emptyList()
        } catch (e: JSONException) {
            Log.e("MM20", "Couldn't read learned searches", e)
            emptyList()
        }
    }

    private fun write(entries: List<LearnedSearch>) {
        val json = JSONArray()
        for (entry in entries) {
            json.put(
                JSONObject()
                    .put("query", entry.query)
                    .put("key", entry.key)
                    .put("count", entry.count)
                    .put("lastUsed", entry.lastUsed)
            )
        }
        val output = try {
            file.startWrite()
        } catch (e: IOException) {
            Log.e("MM20", "Couldn't write learned searches", e)
            return
        }
        try {
            output.write(json.toString().toByteArray())
            file.finishWrite(output)
        } catch (e: IOException) {
            Log.e("MM20", "Couldn't write learned searches", e)
            file.failWrite(output)
        }
    }

    companion object {
        private const val FileName = "learned_searches.json"
    }
}
