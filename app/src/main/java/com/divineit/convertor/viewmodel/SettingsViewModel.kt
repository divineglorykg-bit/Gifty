package com.divineit.convertor.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.divineit.convertor.ConvertorApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = (application as ConvertorApp).settings

    val localOnly: StateFlow<Boolean> = settings.localOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val theme: StateFlow<String> = settings.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val textScale: StateFlow<Float> = settings.textScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    fun setLocalOnly(value: Boolean) {
        viewModelScope.launch { settings.setLocalOnly(value) }
    }

    fun setTheme(value: String) {
        viewModelScope.launch { settings.setTheme(value) }
    }

    fun setTextScale(value: Float) {
        viewModelScope.launch { settings.setTextScale(value) }
    }
}
