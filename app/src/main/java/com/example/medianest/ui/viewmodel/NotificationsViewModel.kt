package com.example.medianest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medianest.data.local.entity.AppNotificationEntity
import com.example.medianest.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow("ALL")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    val notifications: StateFlow<List<AppNotificationEntity>> =
        notificationRepository.getAllNotificationsFlow().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredNotifications: StateFlow<List<AppNotificationEntity>> =
        combine(notificationRepository.getAllNotificationsFlow(), _selectedFilter) { list, filter ->
            if (filter == "ALL") {
                list
            } else {
                list.filter { it.type.equals(filter, ignoreCase = true) }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val unreadCount: StateFlow<Int> =
        notificationRepository.getUnreadCountFlow().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun markAsRead(id: Long) {
        viewModelScope.launch {
            notificationRepository.markAsRead(id)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            notificationRepository.markAllAsRead()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            notificationRepository.clearAll()
        }
    }

    fun deleteNotification(id: Long) {
        viewModelScope.launch {
            notificationRepository.deleteById(id)
        }
    }

    fun addNotification(notification: AppNotificationEntity) {
        viewModelScope.launch {
            notificationRepository.insert(notification)
        }
    }

    fun pruneOlderThan(days: Int = 365) {
        viewModelScope.launch {
            val cutoff = System.currentTimeMillis() - (days.toLong() * 24L * 60L * 60L * 1000L)
            notificationRepository.deleteOlderThan(cutoff)
        }
    }
}
