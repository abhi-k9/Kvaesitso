package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.WeightFactor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RankingSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
){
    val weightFactor
        get() = launcherDataStore.data.map { it.rankingWeightFactor }.distinctUntilChanged()

    fun setWeightFactor(weightFactor: WeightFactor) {
        launcherDataStore.update {
            it.copy(rankingWeightFactor = weightFactor)
        }
    }

    /**
     * Whether results that are opened after typing a query come first the next time it is typed
     */
    val learnFromLaunches
        get() = launcherDataStore.data.map { it.rankingLearnFromLaunches }.distinctUntilChanged()

    fun setLearnFromLaunches(learn: Boolean) {
        launcherDataStore.update {
            it.copy(rankingLearnFromLaunches = learn)
        }
    }
}