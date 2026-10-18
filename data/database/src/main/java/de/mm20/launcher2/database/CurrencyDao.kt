package de.mm20.launcher2.database

import androidx.room3.*
import de.mm20.launcher2.database.entities.CurrencyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyDao {

    @Query("SELECT value FROM Currency WHERE symbol = :symbol")
    fun getExchangeRate(symbol: String) : Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(currency: CurrencyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(currencies: List<CurrencyEntity>)

    @Query("SELECT * FROM Currency WHERE symbol = :symbol")
    fun getCurrency(symbol: String) : CurrencyEntity?

    @Query("SELECT * FROM Currency WHERE symbol IN (:symbols)")
    fun getCurrencies(symbols: List<String>) : List<CurrencyEntity>

    @Query("SELECT * FROM Currency")
    fun getAllCurrencies() : List<CurrencyEntity>

    @Transaction
    fun exists(symbol: String): Boolean {
        return getCurrency(symbol) != null
    }

    @Query("SELECT lastUpdate FROM Currency WHERE symbol = :symbol")
    fun getLastUpdate(symbol: String) : Long

    /**
     * The date of the latest exchange rates, null if there are none
     */
    @Query("SELECT MAX(lastUpdate) FROM Currency")
    fun getLatestUpdate(): Long?

    @Query("SELECT MAX(lastUpdate) FROM Currency")
    fun latestUpdate(): Flow<Long?>
}