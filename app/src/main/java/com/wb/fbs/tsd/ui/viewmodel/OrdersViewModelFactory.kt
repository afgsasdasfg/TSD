package com.wb.fbs.tsd.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.wb.fbs.tsd.data.repository.CrptRepository
import com.wb.fbs.tsd.data.repository.WbRepository

class OrdersViewModelFactory(
    private val repository: WbRepository,
    private val crptRepository: CrptRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OrdersViewModel::class.java)) {
            return OrdersViewModel(repository, crptRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
