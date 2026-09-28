package de.mm20.launcher2.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update


class NotificationRepository {
    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    private val _notifications: MutableStateFlow<List<Notification>> = MutableStateFlow(
        emptyList()
    )

    val notifications: Flow<List<Notification>> = _notifications

    internal fun setNotifications(notifications: List<Notification>) {
        _notifications.value = notifications
    }

    internal fun getNotifications(): List<Notification> = _notifications.value

    /**
     * Atomically updates the list of notifications
     */
    internal fun updateNotifications(transform: (List<Notification>) -> List<Notification>) {
        _notifications.update(transform)
    }

    internal fun onNotificationPosted(notification: Notification) {
        _notifications.update { notifications ->
            notifications.filter { !isEqual(it, notification) } + notification
        }
    }

    internal fun onNotificationRemoved(key: String) {
        _notifications.update { notifications -> notifications.filter { it.key != key } }
    }

    private fun isEqual(
        notification1: Notification,
        notification2: Notification
    ): Boolean {
        return notification1.key == notification2.key
    }

    fun cancelNotification(notification: Notification) {
        NotificationService.getInstance()?.cancelNotification(notification.key)
    }

}