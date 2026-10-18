package de.mm20.launcher2.ui.settings.unitconverter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import java.text.DateFormat
import java.util.Date
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.locale.CurrencySettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object UnitConverterSettingsRoute: NavKey

@Composable
fun UnitConverterSettingsScreen() {
    val viewModel: UnitConverterSettingsScreenVM = viewModel()
    val backStack = LocalBackStack.current

    PreferenceScreen(
        title = stringResource(R.string.preference_search_unitconverter),
        helpUrl = "https://kvaesitso.mm20.de/docs/user-guide/search/unit-converter"
    ) {
        item {
            PreferenceCategory {
                val unitConverter by viewModel.unitConverter.collectAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_search_unitconverter),
                    summary = stringResource(R.string.preference_search_unitconverter_summary),
                    value = unitConverter == true,
                    onValueChanged = {
                        viewModel.setUnitConverter(it)
                    }
                )
                val currencyConverter by viewModel.currencyConverter.collectAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_search_currencyconverter),
                    summary = stringResource(R.string.preference_search_currencyconverter_summary),
                    enabled = unitConverter != false,
                    value = currencyConverter == true,
                    onValueChanged = {
                        viewModel.setCurrencyConverter(it)
                    }
                )
                val exchangeRatesDate by viewModel.exchangeRatesDate.collectAsState()
                val isUpdating by viewModel.isUpdatingExchangeRates.collectAsState()
                Preference(
                    title = stringResource(R.string.preference_exchange_rates),
                    summary = when {
                        isUpdating -> stringResource(R.string.preference_exchange_rates_updating)
                        exchangeRatesDate == null -> stringResource(R.string.preference_exchange_rates_missing)
                        else -> stringResource(
                            R.string.preference_exchange_rates_summary,
                            DateFormat.getDateInstance(DateFormat.MEDIUM)
                                .format(Date(exchangeRatesDate!!)),
                        )
                    },
                    enabled = unitConverter != false && currencyConverter == true && !isUpdating,
                    onClick = {
                        viewModel.updateExchangeRates()
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.preference_search_supportedunits),
                    icon = R.drawable.help_24px,
                    onClick = {
                        backStack.add(UnitConverterHelpSettingsRoute)
                    }
                )
                Preference(
                    title = stringResource(R.string.preference_currency_settings),
                    icon = R.drawable.open_in_new_24px,
                    onClick = {
                        backStack.add(CurrencySettingsRoute)
                    }
                )
            }
        }

    }
}
