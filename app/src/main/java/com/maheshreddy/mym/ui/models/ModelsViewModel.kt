package com.maheshreddy.mym.ui.models

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maheshreddy.mym.models.ModelKind
import com.maheshreddy.mym.models.ModelStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelsViewModel @Inject constructor(private val store: ModelStore) : ViewModel() {
    val installed = store.installed
    val progress = store.progress
    val integrity = store.integrity
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun import(kind: ModelKind, uri: Uri) {
        viewModelScope.launch {
            _error.value = null
            runCatching { store.import(kind, uri) }.onFailure { _error.value = "Import failed: ${it.message}" }
        }
    }

    fun remove(kind: ModelKind) = store.remove(kind)

    fun verify(kind: ModelKind) {
        viewModelScope.launch {
            _error.value = null
            runCatching { store.verify(kind) }.onFailure { _error.value = "Verify failed: ${it.message}" }
        }
    }
}
