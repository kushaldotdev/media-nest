package com.example.medianest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medianest.data.local.entity.SubscriptionEntity
import com.example.medianest.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {

    val subscriptions: StateFlow<List<SubscriptionEntity>> =
        subscriptionRepository.getAllSubscriptions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun unsubscribe(id: Long) {
        viewModelScope.launch {
            subscriptionRepository.unsubscribe(id)
        }
    }

    fun updateAutoDownload(id: Long, autoDownload: Boolean, audioOnly: Boolean) {
        viewModelScope.launch {
            subscriptionRepository.updateAutoDownload(id, autoDownload, audioOnly)
        }
    }
}
