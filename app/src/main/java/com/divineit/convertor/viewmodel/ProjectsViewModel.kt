package com.divineit.convertor.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.divineit.convertor.ConvertorApp
import com.divineit.convertor.data.ProjectEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as ConvertorApp).projectRepository

    val projects: StateFlow<List<ProjectEntity>> = repository.projects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteProject(id: Long) {
        viewModelScope.launch { repository.deleteProject(id) }
    }
}
