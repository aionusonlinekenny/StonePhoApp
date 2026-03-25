package com.example.stonephopro.viewmodel

import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stonephopro.model.InventoryItem
import com.example.stonephopro.utils.InventoryStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InventoryViewModel(private val context: Context) : ViewModel() {
    var selectedDate by mutableStateOf(InventoryStorage.getTodayDate())
    var items = mutableStateListOf<InventoryItem>()
        private set

    init {
        loadItemsForDate(selectedDate)
    }

    fun loadItemsForDate(date: String) {
        selectedDate = date
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = InventoryStorage.loadItems(context, date)
            items.clear()
            items.addAll(loaded)
        }
    }

    fun addItem(item: InventoryItem) {
        val existingIndex = items.indexOfFirst {
            it.id == item.id && it.type == item.type && it.date == item.date
        }

        if (existingIndex != -1) {
            val existing = items[existingIndex]
            val updated = existing.copy(
                quantity = existing.quantity + item.quantity,
                subtotal = existing.subtotal + item.subtotal,
                slc = (existing.slc ?: 0) + item.quantity
            )
            items[existingIndex] = updated
            InventoryStorage.updateItem(context, updated)
        } else {
            items.add(item)
            InventoryStorage.saveItem(context, item)
        }
    }


    fun deleteItem(item: InventoryItem) {
        InventoryStorage.deleteItem(context, item.date, item.id)
        if (item.date == selectedDate) {
            items.removeAll { it.id == item.id }
        }
    }

    fun updateItem(updatedItem: InventoryItem) {
        InventoryStorage.updateItem(context, updatedItem)
        if (updatedItem.date == selectedDate) {
            val index = items.indexOfFirst { it.id == updatedItem.id }
            if (index != -1) {
                items[index] = updatedItem
            }
        }
    }
}
