package de.mm20.launcher2.badges.providers

import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.MutableBadge
import de.mm20.launcher2.notifications.Notification
import de.mm20.launcher2.notifications.NotificationRepository
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.Searchable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NotificationBadgeProvider : BadgeProvider, KoinComponent {
    private val notificationRepository: NotificationRepository by inject()

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    /**
     * The badges of the apps that have notifications, by package name. They're worked out once
     * whenever the notifications change, e.g. every time a download progresses, instead of once
     * for every icon that is shown.
     */
    private val badges: Flow<Map<String, Badge>> = notificationRepository.notifications
        .map { notifications ->
            notifications
                .filter { it.canShowBadge }
                .groupBy { it.packageName }
                .mapValues { (_, notifications) -> badgeOf(notifications) }
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(), 1)

    override fun getBadge(searchable: Searchable): Flow<Badge?> {
        if (searchable !is Application) return flowOf(null)

        val packageName = searchable.componentName.packageName
        // Only the icons whose badge changed are updated
        return badges.map { it[packageName] }.distinctUntilChanged()
    }

    private fun badgeOf(notifications: List<Notification>): Badge {
        return MutableBadge(
            number = notifications.sumOf {
                if (it.canShowBadge && !it.isGroupSummary) it.number
                else 0
            },
            progress = notifications.mapNotNull {
                val progress = it.progress ?: return@mapNotNull null
                val progressMax = it.progressMax ?: return@mapNotNull null
                return@mapNotNull progress.toFloat() / progressMax.toFloat()
            }
                .takeIf { it.isNotEmpty() }
                ?.let {
                    it.sumOf { it.toDouble() }.toFloat() / it.size
                }
        )
    }
}
