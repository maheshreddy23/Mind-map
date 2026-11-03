package com.maheshreddy.mym.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maheshreddy.mym.core.llm.ButlerProfile
import com.maheshreddy.mym.core.llm.InterviewStyle
import com.maheshreddy.mym.core.models.CatalogModel
import com.maheshreddy.mym.core.models.DeviceFit
import com.maheshreddy.mym.core.models.ModelCategory
import com.maheshreddy.mym.data.ButlerRepository
import com.maheshreddy.mym.data.settings.SettingsRepository
import com.maheshreddy.mym.models.DeviceCheck
import com.maheshreddy.mym.models.DownloadState
import com.maheshreddy.mym.models.ModelDownloader
import com.maheshreddy.mym.models.ModelStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep { WELCOME, DEVICE, BUNDLE, DOWNLOAD, BUTLER, MIC, FIRST }

data class BundleItem(val model: CatalogModel, val installed: Boolean, val download: DownloadState?)

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    /** True when MYM already has a library or settings: the wizard runs once with the current choices pre-filled. */
    val existing: Boolean = false,
    val device: String = "",
    val tier: String = "",
    val ramGb: Int = 0,
    val bundle: List<BundleItem> = emptyList(),
    val canDownload: Boolean = true,
    val useMobileData: Boolean = false,
    val name: String = ButlerProfile.DEFAULT_NAME,
    val callsYou: String = "",
    val style: InterviewStyle = InterviewStyle.SHARP_ENGINEER,
    val areas: List<String> = emptyList(),
    val areaChoices: List<String> = emptyList(),
) {
    /** Bytes still to download for the recommended bundle. */
    val toDownload: Long get() = bundle.filterNot { it.installed }.sumOf { it.model.sizeBytes }
    val allInstalled: Boolean get() = bundle.isNotEmpty() && bundle.all { it.installed || it.download?.status == DownloadState.Status.DONE }
}

/**
 * First-run onboarding (v2 §13 phase 2): Welcome → device check → recommended bundle with its total size →
 * download → butler name and personality → microphone → first session. An existing install sees it once, with
 * everything already chosen pre-filled.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val device: DeviceCheck,
    private val store: ModelStore,
    private val downloader: ModelDownloader,
    private val butler: ButlerRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val local = MutableStateFlow(OnboardingUiState())

    init {
        viewModelScope.launch {
            val p = butler.profile.first()
            val s = settings.settings.first()
            val existing = store.models.value.isNotEmpty() || p.name != ButlerProfile.DEFAULT_NAME || p.callsYou.isNotBlank()
            local.update {
                it.copy(
                    existing = existing, name = p.displayName, callsYou = p.callsYou, style = p.style,
                    areas = s.starterSubjects,
                    areaChoices = (com.maheshreddy.mym.data.settings.AppSettings.DEFAULT_STARTERS + s.starterSubjects).distinct(),
                    useMobileData = !s.wifiOnly,
                )
            }
        }
    }

    val state: StateFlow<OnboardingUiState> = combine(local, store.models, downloader.states, settings.settings) { l, installed, downloads, prefs ->
        val profile = device.profile
        val ids = installed.map { it.id }.toSet()
        val kinds = installed.map { it.kind.category }.toSet()
        val bundle = DeviceFit.recommended(profile, prefs.multilingual).toSortedMap(compareBy { it.ordinal }).values.map { m ->
            // A model of the same kind that is already installed (e.g. an imported file) counts as installed.
            BundleItem(m, m.id in ids || (m.category != ModelCategory.VAD && m.category in kinds), downloads[m.id])
        }
        l.copy(device = device.description, tier = profile.tier.label, ramGb = profile.ramGb, bundle = bundle, canDownload = downloader.available)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OnboardingUiState())

    fun go(step: OnboardingStep) = local.update { it.copy(step = step) }
    fun next() = local.update { it.copy(step = OnboardingStep.entries.getOrElse(it.step.ordinal + 1) { OnboardingStep.FIRST }) }
    fun back() = local.update { it.copy(step = OnboardingStep.entries.getOrElse(it.step.ordinal - 1) { OnboardingStep.WELCOME }) }

    fun setMobileData(on: Boolean) = local.update { it.copy(useMobileData = on) }

    /** Starts every missing model of the bundle (Wi-Fi only unless the user allowed mobile data). */
    fun downloadBundle() {
        val s = state.value
        s.bundle.filterNot { it.installed || it.download?.status == DownloadState.Status.DONE }.forEach { item ->
            runCatching { downloader.enqueue(item.model.id, allowMobileData = s.useMobileData) }
        }
        go(OnboardingStep.DOWNLOAD)
    }

    fun setName(v: String) = local.update { it.copy(name = v.take(24)) }
    fun setCallsYou(v: String) = local.update { it.copy(callsYou = v.take(40)) }
    fun setStyle(v: InterviewStyle) = local.update { it.copy(style = v) }
    fun toggleArea(a: String) = local.update { it.copy(areas = if (a in it.areas) it.areas - a else it.areas + a) }
    fun addArea(a: String) {
        val t = a.trim().take(40)
        if (t.isEmpty()) return
        local.update { it.copy(areas = (it.areas + t).distinct(), areaChoices = (it.areaChoices + t).distinct()) }
    }

    /** Saves the butler and the areas (on leaving the butler step). */
    fun saveButler() = viewModelScope.launch {
        val s = local.value
        val p = butler.profile.first()
        butler.save(p.copy(name = s.name.trim().ifEmpty { ButlerProfile.DEFAULT_NAME }, callsYou = s.callsYou.trim(), style = s.style))
        settings.update { it.copy(starterSubjects = s.areas) }
    }

    /** Ends onboarding; the app never shows it again. */
    fun finish(then: () -> Unit) = viewModelScope.launch {
        settings.update { it.copy(onboarded = true) }
        then()
    }
}
