package de.mm20.launcher2.currencies

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import de.mm20.launcher2.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

class CurrencyRepository(
    private val context: Context,
) {

    private fun getOwnCurrency(): String {
        return try {
            java.util.Currency.getInstance(Locale.getDefault()).currencyCode ?: "USD"
        } catch (e: IllegalArgumentException) {
            "USD"
        }
    }

    private val currencySymbolAliases = buildMap {
        val ownCurrency = getOwnCurrency()

        put("€", "EUR")

        val dollarSymbolCurrencies = listOf(
            // dollar
            "AUD",
            "BBD",
            "BMD",
            "BND",
            "BSD",
            "BZD",
            "CAD",
            "FJD",
            "GYD",
            "HKD",
            "JMD",
            "KID",
            "KYD",
            "LRD",
            "NAD",
            "NZD",
            "SBD",
            "SGD",
            "SRD",
            "TTD",
            "TVD",
            "TWD",
            "USD",
            "XCD",
            // peso
            "ARS",
            "CLP",
            "COP",
            "CUP",
            "DOP",
            "MXN",
            "UYU",
        )

        if (ownCurrency in dollarSymbolCurrencies) {
            put("$", ownCurrency)
        } else {
            put("$", "USD")
        }

        val poundSymbolCurrencies = listOf("EGP", "FKP", "GBP", "GIP", "SHP", "SDG", "SYP")
        if (ownCurrency in poundSymbolCurrencies) {
            put("£", ownCurrency)
        } else {
            put("£", "GBP")
        }

        put("¥", if (ownCurrency == "CNY") "CNY" else "JPY")
        put("₩", if (ownCurrency == "KPW") "KPW" else "KRW")

        if (ownCurrency == "NOK" || ownCurrency == "SEK" || ownCurrency == "DKK" || ownCurrency == "ISK") {
            put("kr", ownCurrency)
            put("kr.", ownCurrency)
            put("Kr", ownCurrency)
        } else {
            put("kr", "SEK")
            put("kr.", "DKK")
            put("Kr", "ISK")
        }

        put("zł", "PLN")
        put("Kč", "CZK")
        put("₴", "UAH")
        put("₽", "RUB")
        put("Ft", "HUF")

        put("₪", "ILS")
        put("₺", "TRY")

        put("R$", "BRL")

        put("₱", "PHP")
        put("฿", "THB")
        put("₹", "INR")

        return@buildMap
    }

    // Without a connection, the download would only fail and be tried again later and later
    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun enableCurrencyUpdateWorker() {
        val currencyWorker =
            PeriodicWorkRequest.Builder(ExchangeRateWorker::class.java, 60, TimeUnit.MINUTES)
                .setConstraints(networkConstraints)
                .build()
        // Update, so that the constraints also apply to the work scheduled by earlier versions
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "ExchangeRates",
            ExistingPeriodicWorkPolicy.UPDATE, currencyWorker
        )
    }

    fun disableCurrencyUpdateWorker() {
        WorkManager.getInstance(context).cancelUniqueWork("ExchangeRates")
        WorkManager.getInstance(context).cancelUniqueWork(UpdateNowWork)
    }

    /**
     * Downloads the exchange rates as soon as there is a connection.
     */
    fun updateExchangeRates() {
        enqueueUpdate(ExistingWorkPolicy.REPLACE)
    }

    private fun enqueueUpdate(policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequest.Builder(ExchangeRateWorker::class.java)
            .setConstraints(networkConstraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UpdateNowWork, policy, request)
    }

    @Volatile
    private var lastOutdatedCheck = 0L

    /**
     * Downloads the exchange rates if there are none or if they are outdated, e.g. because the
     * periodic update hasn't run for a while.
     */
    suspend fun updateIfOutdated() {
        val now = System.currentTimeMillis()
        if (now - lastOutdatedCheck < 10 * 60 * 1000L) return
        lastOutdatedCheck = now
        val latest = withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).currencyDao().getLatestUpdate()
        }
        // Rates are only published on working days: after a long weekend, they're 4 days old
        if (latest == null || now - latest > 5 * 24 * 60 * 60 * 1000L) {
            enqueueUpdate(ExistingWorkPolicy.KEEP)
        }
    }

    /**
     * The date of the latest exchange rates, null if there are none
     */
    fun latestUpdate(): Flow<Long?> {
        return AppDatabase.getInstance(context).currencyDao().latestUpdate()
            .distinctUntilChanged()
    }

    /**
     * Whether the exchange rates are being downloaded because they were requested
     */
    fun isUpdating(): Flow<Boolean> {
        return WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(UpdateNowWork)
            .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }
            .distinctUntilChanged()
    }

    /**
     * Resolves currency symbol aliases to their full currency symbol.
     * (e.g. € -> EUR)
     * @param symbol The currency symbol to resolve
     * @return The resolved currency symbol or the original symbol if no alias was found
     */
    fun resolveAlias(symbol: String): String {
        return currencySymbolAliases[symbol] ?: symbol.uppercase()
    }

    suspend fun convertCurrency(
        fromCurrency: String,
        value: Double,
        toCurrency: String? = null
    ): List<Pair<String, Double>> {

        return withContext(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(context)
                .currencyDao()

            val from = Currency(dao.getCurrency(fromCurrency) ?: return@withContext emptyList())

            return@withContext if (toCurrency == null) {
                dao.getAllCurrencies().mapNotNull {
                    val to = Currency(it)
                    if (from.lastUpdate != to.lastUpdate) {
                        Log.w("MM20", "Exchange rate update dates do not match: $fromCurrency, $it")
                        return@mapNotNull null
                    }
                    if (from.symbol == to.symbol) return@mapNotNull null
                    to.symbol to value * to.value / from.value
                }
            } else {
                val to = Currency(dao.getCurrency(toCurrency) ?: return@withContext emptyList())
                if (from.lastUpdate != to.lastUpdate) {
                    Log.w(
                        "MM20",
                        "Exchange rate update dates do not match: $fromCurrency, $toCurrency"
                    )
                    return@withContext emptyList()
                }
                listOf(toCurrency to value * to.value / from.value)
            }
        }
    }

    suspend fun getKnownUnits(): List<String> {
        return withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).currencyDao().getAllCurrencies().map { it.symbol }
        }
    }

    suspend fun isValidCurrency(symbol: String): Boolean {
        val isoSymbol = currencySymbolAliases[symbol] ?: symbol.uppercase()
        return withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).currencyDao().exists(isoSymbol)
        }
    }

    suspend fun getLastUpdate(symbol: String): Long {
        return withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).currencyDao().getLastUpdate(symbol)
        }
    }

    companion object {
        private const val UpdateNowWork = "ExchangeRatesNow"
    }
}
