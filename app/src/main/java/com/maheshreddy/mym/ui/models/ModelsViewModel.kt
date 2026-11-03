package com.maheshreddy.mym.ui.models

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maheshreddy.mym.core.models.CatalogModel
import com.maheshreddy.mym.core.models.Catalog
import com.maheshreddy.mym.core.models.DeviceFit
import com.maheshreddy.mym.core.models.Fit
import com.maheshreddy.mym.core.models.ModelCategory
import com.maheshreddy.mym.data.settings.SettingsRepository
import com.maheshreddy.mym.models.DeviceCheck
import com.maheshreddy.mym.models.DownloadState
import com.maheshreddy.mym.models.ImportProgress
import com.maheshreddy.mym.models.InstalledModel
import com.maheshreddy.mym.models.ModelDownloader
import com.maheshreddy.mym.models.ModelKind
import com.maheshreddy.mym.models.ModelStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelRow(
    val model: CatalogModel,
    val fit: Fit,
    val recommended: Boolean,
    val installed: Boolean,
    val active: Boolean,
    val download: DownloadState?,
)

data class ModelsUiState(
    val device: String = "",
    val tier: String = "",
    val category: ModelCategory = ModelCategory.LLM,
    val sort: DeviceFit.Sort = DeviceFit.Sort.RECOMMENDED,
    val rows: List<ModelRow> = emptyList(),
    /** Imported (non-catalogue) models in this category. */
    val imported: List<InstalledModel> = emptyList(),
    val activeId: String? = null,
    val canDownload: Boolean = true,
    val wifiOnly: Boolean = true,
    val storagePath: String = "",
    val usedBytes: Long = 0,
    val freeBytes: Long = 0,
    val importing: ImportProgress? = null,
)

@HiltViewModel
class ModelsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val store: ModelStore,
    private val downloader: ModelDownloader,
    private val device: DeviceCheck,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val category = MutableStateFlow(
        savedState.get<String>("category")?.let { c -> ModelCategory.entries.firstOrNull { it.name == c } } ?: ModelCategory.LLM,
    )
    private val sort = MutableStateFlow(DeviceFit.Sort.RECOMMENDED)
    private val tick = MutableStateFlow(0)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    /** A download waiting for "use mobile data?" confirmation. */
    private val _confirmMobile = MutableStateFlow<CatalogModel?>(null)
    val confirmMobile: StateFlow<CatalogModel?> = _confirmMobile.asStateFlow()

    val state: StateFlow<ModelsUiState> = combine(
        combine(store.models, downloader.states, store.progress) { a, b, c -> Triple(a, b, c) },
        category, sort, settings.settings, tick,
    ) { (installed, downloads, importing), cat, s, prefs, _ ->
        val profile = device.profile
        val recommended = DeviceFit.recommended(profile, prefs.multilingual).values.map { it.id }.toSet()
        val kind = ModelKind.of(cat)
        val activeId = store.activeId(kind)
        val models = DeviceFit.sorted(Catalog.inCategory(cat), profile, s, recommended)
        ModelsUiState(
            device = device.description,
            tier = profile.tier.label,
            category = cat,
            sort = s,
            rows = models.map { m ->
                ModelRow(m, DeviceFit.fit(m, profile), m.id in recommended, installed.any { it.id == m.id }, activeId == m.id, downloads[m.id])
            },
            imported = installed.filter { it.kind == kind && it.catalog == null },
            activeId = activeId,
            canDownload = downloader.available,
            wifiOnly = prefs.wifiOnly,
            storagePath = store.root.absolutePath,
            usedBytes = store.usedBytes(),
            freeBytes = store.freeBytes(),
            importing = importing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelsUiState())

    fun setCategory(c: ModelCategory) { category.value = c }
    fun setSort(s: DeviceFit.Sort) { sort.value = s }

    fun download(id: String) {
        val m = Catalog.byId(id) ?: return
        viewModelScope.launch {
            val prefs = settings.settings.first()
            when {
                m.sizeBytes > store.freeBytes() - 200_000_000 -> _message.value = "Not enough free space for ${m.name} (${sizeLabel(m.sizeBytes)})."
                prefs.wifiOnly && !onUnmeteredNetwork() -> _confirmMobile.value = m
                else -> start(m, allowMobile = !prefs.wifiOnly)
            }
        }
    }

    /** "Not on Wi-Fi" dialog: download now over mobile data, or wait for Wi-Fi. */
    fun confirmMobile(useMobileData: Boolean) {
        val m = _confirmMobile.value ?: return
        _confirmMobile.value = null
        start(m, allowMobile = useMobileData)
        if (!useMobileData) _message.value = "${m.name} will download when you are on Wi-Fi."
    }

    private fun start(m: CatalogModel, allowMobile: Boolean) = runCatching { downloader.enqueue(m.id, allowMobile) }
        .onFailure { _message.value = it.message }

    fun pause(id: String) = downloader.pause(id)
    fun cancel(id: String) = downloader.cancel(id)

    fun activate(id: String) = runCatching { store.activate(id) }
        .onSuccess { _message.value = "${store.models.value.firstOrNull { it.id == id }?.name ?: "Model"} is now active." }
        .onFailure { _message.value = it.message }

    fun delete(id: String) = runCatching { store.delete(id) }
        .onSuccess { tick.value++; _message.value = "Deleted." }
        .onFailure { _message.value = it.message }

    fun import(kind: ModelKind, uri: Uri) {
        viewModelScope.launch {
            runCatching { store.import(kind, uri) }
                .onSuccess { tick.value++; _message.value = "Imported. ${store.active(kind)?.let { if (it.verified) "${it.name} matches its official checksum." else "No official checksum is known for this file." } ?: ""}" }
                .onFailure { _message.value = "Import failed: ${it.message}" }
        }
    }

    private fun onUnmeteredNetwork(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    companion object {
        fun sizeLabel(bytes: Long): String = when {
            bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
            bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1e6)
            else -> "%.0f KB".format(bytes / 1e3)
        }
    }
}
