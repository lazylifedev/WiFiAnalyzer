package com.lazyapps.wifianalyzer.billing

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BillingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: EntitlementRepository = DefaultEntitlementRepository(
        PlayBillingRepository(application),
    )
    val uiState = repository.snapshot.map(BillingSnapshot::toUiState).stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        BillingUiState(),
    )

    init { refresh() }
    fun refresh(force: Boolean = false) = viewModelScope.launch { repository.refresh(force) }
    fun restore() = viewModelScope.launch { repository.restore() }
    fun purchase(activity: Activity): Boolean = repository.purchase(activity)

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
