package de.mm20.launcher2.unitconverter

import android.content.Context
import de.mm20.launcher2.currencies.CurrencyRepository
import de.mm20.launcher2.preferences.search.UnitConverterSettings
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.unitconverter.converters.AreaConverter
import de.mm20.launcher2.unitconverter.converters.Converter
import de.mm20.launcher2.unitconverter.converters.CurrencyConverter
import de.mm20.launcher2.unitconverter.converters.DataConverter
import de.mm20.launcher2.unitconverter.converters.LengthConverter
import de.mm20.launcher2.unitconverter.converters.MassConverter
import de.mm20.launcher2.unitconverter.converters.TemperatureConverter
import de.mm20.launcher2.unitconverter.converters.TimeConverter
import de.mm20.launcher2.unitconverter.converters.VelocityConverter
import de.mm20.launcher2.unitconverter.converters.VolumeConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

interface UnitConverterRepository {
    fun search(query: String): Flow<UnitConverter?>
    suspend fun getAvailableConverters(includeCurrencies: Boolean): List<Converter>

    /**
     * The date of the latest exchange rates, null if they haven't been downloaded yet
     */
    fun exchangeRatesDate(): Flow<Long?>

    /**
     * Whether the exchange rates are being downloaded because [updateExchangeRates] was called
     */
    fun isUpdatingExchangeRates(): Flow<Boolean>

    /**
     * Downloads the exchange rates as soon as there is a connection.
     */
    fun updateExchangeRates()
}

internal class UnitConverterRepositoryImpl(
    private val context: Context,
    private val currencyRepository: CurrencyRepository,
    private val settings: UnitConverterSettings,
) : UnitConverterRepository, KoinComponent {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    init {
        scope.launch {
            settings.map { it.enabled && it.currencies }
                .distinctUntilChanged().collectLatest {
                    if (it) currencyRepository.enableCurrencyUpdateWorker()
                    else currencyRepository.disableCurrencyUpdateWorker()
                }
        }
    }

    override fun search(query: String): Flow<UnitConverter?> {
        if (query.isBlank()) return flowOf(null)
        // Searched again when new exchange rates arrive, e.g. if there weren't any yet
        return combine(
            settings.distinctUntilChanged(),
            currencyRepository.latestUpdate(),
        ) { settings, _ -> settings }.map {
            if (!it.enabled) null
            else {
                if (it.currencies) currencyRepository.updateIfOutdated()
                queryUnitConverter(query, it.currencies)
            }
        }
    }

    override fun exchangeRatesDate(): Flow<Long?> = currencyRepository.latestUpdate()

    override fun isUpdatingExchangeRates(): Flow<Boolean> = currencyRepository.isUpdating()

    override fun updateExchangeRates() {
        currencyRepository.updateExchangeRates()
    }

    override suspend fun getAvailableConverters(includeCurrencies: Boolean): List<Converter> {
        val converters = mutableListOf(
            MassConverter(context),
            LengthConverter(context),
            DataConverter(context),
            TimeConverter(context),
            VelocityConverter(context),
            AreaConverter(context),
            TemperatureConverter(context),
            VolumeConverter(context),
        )
        if (includeCurrencies) converters.add(CurrencyConverter(currencyRepository, settings.preferredCurrencies.first()))

        return converters
    }

    private suspend fun queryUnitConverter(
        query: String,
        includeCurrencies: Boolean
    ): UnitConverter? {
        FeetAndInches.parse(query)?.let {
            val converter = LengthConverter(context)
            val unit = context.getString(
                if (it.inFeet) R.string.unit_foot_symbol else R.string.unit_inch_symbol
            )
            if (it.targetUnit != null && !converter.isValidUnit(it.targetUnit)) return null
            return converter.convert(context, unit, it.value, it.targetUnit)
        }

        val regex = Regex("""([+\-]?[\d+\-e,.]+|[^\d>\-]+)""")

        val matches = regex.findAll(query)

        var inputStr: String? = null
        var inputValue: Double? = null
        var inputUnit: String? = null
        var outputUnit: String? = null

        for ((i, match) in matches.withIndex()) {
            when (i) {
                0 -> {
                    inputStr = match.value.trim()
                    inputValue = inputStr.toDoubleOrNull()
                        ?: inputStr.replace(',', '.').toDoubleOrNull()
                                ?: return null
                }
                1 -> inputUnit = match.value.trim()
                2 -> {
                    if (!match.value.contains("-") && !match.value.contains(">")) {
                        outputUnit = match.value.trim()
                    }
                }
                3 -> {
                    if (outputUnit == null) {
                        outputUnit = match.value.trim()
                        break
                    } else {
                        return null
                    }
                }
                else -> return null
            }
        }

        if (inputValue == null || inputUnit == null) {
            return null
        }

        val converters = getAvailableConverters(includeCurrencies)

        for (converter in converters) {
            if (!converter.isValidUnit(inputUnit)) continue
            if (outputUnit != null && !converter.isValidUnit(outputUnit)) continue
            return converter.convert(context, inputUnit, inputValue, outputUnit)
        }
        return null
    }
}