package com.maheshreddy.mym.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maheshreddy.mym.core.llm.InterviewStyle
import com.maheshreddy.mym.models.DownloadState
import com.maheshreddy.mym.ui.components.Avatar
import com.maheshreddy.mym.ui.components.Caption
import com.maheshreddy.mym.ui.components.MindCard
import com.maheshreddy.mym.ui.components.Pill
import com.maheshreddy.mym.ui.components.PillStyle
import com.maheshreddy.mym.ui.components.ScreenTitle
import com.maheshreddy.mym.ui.components.SectionLabel
import com.maheshreddy.mym.ui.models.ModelsViewModel
import com.maheshreddy.mym.ui.theme.Mind
import com.maheshreddy.mym.ui.theme.MindDimens

class OnboardingActions(
    val onNext: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onDownload: () -> Unit = {},
    val onSkipDownload: () -> Unit = {},
    val onMobileData: (Boolean) -> Unit = {},
    val onName: (String) -> Unit = {},
    val onCallsYou: (String) -> Unit = {},
    val onStyle: (InterviewStyle) -> Unit = {},
    val onToggleArea: (String) -> Unit = {},
    val onAddArea: (String) -> Unit = {},
    val onSaveButler: () -> Unit = {},
    val onAllowMic: () -> Unit = {},
    val onStartSession: () -> Unit = {},
    val onFinishLater: () -> Unit = {},
)

@Composable
fun OnboardingScreen(onDone: () -> Unit, onStartSession: () -> Unit, onOpenModels: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.next() }
    OnboardingContent(
        state,
        OnboardingActions(
            onNext = vm::next, onBack = vm::back, onDownload = vm::downloadBundle,
            onSkipDownload = { vm.go(OnboardingStep.BUTLER); onOpenModels() },
            onMobileData = vm::setMobileData, onName = vm::setName, onCallsYou = vm::setCallsYou, onStyle = vm::setStyle,
            onToggleArea = vm::toggleArea, onAddArea = vm::addArea,
            onSaveButler = { vm.saveButler(); vm.next() },
            onAllowMic = {
                permissions.launch(
                    if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
                    else arrayOf(Manifest.permission.RECORD_AUDIO),
                )
            },
            onStartSession = { vm.finish(onStartSession) },
            onFinishLater = { vm.finish(onDone) },
        ),
    )
}

/** design-spec §4 Onboarding: one step per screen, progress dots, the butler introduced before the microphone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingContent(state: OnboardingUiState, actions: OnboardingActions) {
    val c = Mind.colors
    Column(
        Modifier.fillMaxSize().background(c.ground).statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(horizontal = MindDimens.screenPadding).testTag("onboarding_${state.step.name}"),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OnboardingStep.entries.forEach { s ->
                androidx.compose.foundation.layout.Box(
                    Modifier.weight(1f).height(4.dp).background(if (s.ordinal <= state.step.ordinal) c.accent else c.line, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                )
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (state.step) {
                OnboardingStep.WELCOME -> {
                    Avatar("M", onClick = null, size = 56.dp)
                    ScreenTitle("MYM – Map your Mind")
                    Text(
                        "Talk about what you know. Your butler listens, asks a sharp question now and then, files what you said into " +
                            "subjects and topics, and answers when you ask. Everything runs on this phone; nothing is uploaded.",
                        style = Mind.type.bodyLarge, color = c.ink,
                    )
                    if (state.existing) Caption("Your library and settings are kept. Your current choices are filled in; just check them.")
                }
                OnboardingStep.DEVICE -> {
                    ScreenTitle("Your phone")
                    MindCard {
                        Text(state.device, style = Mind.type.body, color = c.ink, modifier = Modifier.testTag("deviceDescription"))
                        Caption("${state.tier} · ${state.ramGb} GB RAM")
                    }
                    Text(
                        "Models run on the phone, so the right size depends on its memory and chip. The next step recommends a set that fits.",
                        style = Mind.type.body, color = c.ink,
                    )
                }
                OnboardingStep.BUNDLE -> {
                    ScreenTitle("Recommended for your phone")
                    state.bundle.forEach { b ->
                        MindCard(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Caption(b.model.category.label)
                                    Text(b.model.name, style = Mind.type.body, color = c.ink)
                                    Caption("${ModelsViewModel.sizeLabel(b.model.sizeBytes)} · ${b.model.licence.name}")
                                }
                                if (b.installed) Caption("Installed", color = c.accent)
                            }
                        }
                    }
                    Text(
                        if (state.toDownload == 0L) "Everything is installed." else "Total to download: ${ModelsViewModel.sizeLabel(state.toDownload)}",
                        style = Mind.type.cardTitle, color = c.ink, modifier = Modifier.testTag("bundleTotal"),
                    )
                    if (state.canDownload && state.toDownload > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Use mobile data", style = Mind.type.body, color = c.ink)
                                Caption("Off: downloads wait for Wi-Fi.")
                            }
                            Switch(state.useMobileData, actions.onMobileData, modifier = Modifier.testTag("mobileData"))
                        }
                        Caption("Downloaded from Hugging Face over HTTPS and checked against its SHA-256 before use. Network is used for nothing else.")
                    }
                    if (!state.canDownload) Caption("This build has no internet access. Import model files in Settings → Models.")
                }
                OnboardingStep.DOWNLOAD -> {
                    ScreenTitle("Downloading")
                    state.bundle.forEach { b ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(b.model.name, style = Mind.type.body, color = c.ink)
                            val d = b.download
                            when {
                                b.installed || d?.status == DownloadState.Status.DONE -> Caption("Ready", color = c.accent)
                                d == null -> Caption("Not started")
                                d.status == DownloadState.Status.FAILED -> Caption("Failed: ${d.error ?: "try again in Settings → Models"}", color = c.amberText)
                                else -> {
                                    LinearProgressIndicator({ d.fraction }, Modifier.fillMaxWidth(), color = c.accent, trackColor = c.line)
                                    Caption(
                                        when (d.status) {
                                            DownloadState.Status.WAITING -> "Waiting for Wi-Fi"
                                            DownloadState.Status.VERIFYING -> "Checking SHA-256…"
                                            DownloadState.Status.PAUSED -> "Paused"
                                            else -> "${ModelsViewModel.sizeLabel(d.bytes)} of ${ModelsViewModel.sizeLabel(d.total)}"
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Caption("You can carry on while this downloads; it continues in the background.")
                }
                OnboardingStep.BUTLER -> {
                    ScreenTitle("Your butler")
                    Caption("Name it anything. It is calm, dry-witted and efficient; you can tune its personality later.")
                    OutlinedTextField(state.name, actions.onName, label = { Text("Butler's name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("butlerNameField"))
                    OutlinedTextField(state.callsYou, actions.onCallsYou, label = { Text("What should it call you?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    SectionLabel("Interview style")
                    InterviewStyle.entries.forEach { s ->
                        MindCard(
                            onClick = { actions.onStyle(s) },
                            background = if (s == state.style) c.accentSoft else c.surface,
                            modifier = Modifier.testTag("style_${s.name}"),
                            padding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                        ) {
                            Text(s.label, style = Mind.type.body, color = c.ink)
                            Caption(s.hint)
                        }
                    }
                    SectionLabel("Areas to map")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.areaChoices.forEach { a ->
                            Pill(a, { actions.onToggleArea(a) }, style = if (a in state.areas) PillStyle.INK else PillStyle.OUTLINE, height = 36.dp)
                        }
                    }
                    var area by rememberSaveable { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(area, { area = it }, label = { Text("Add an area") }, singleLine = true, modifier = Modifier.weight(1f))
                        Pill("Add", { actions.onAddArea(area); area = "" }, style = PillStyle.OUTLINE, height = 40.dp)
                    }
                    Caption("These appear on your Map as places to start. Subjects are only created when you confirm where things go.")
                }
                OnboardingStep.MIC -> {
                    ScreenTitle("Your microphone")
                    Text(
                        "${state.name} listens only while a session is open, and audio never leaves the phone. Notifications are used for one daily nudge, outside quiet hours.",
                        style = Mind.type.bodyLarge, color = c.ink,
                    )
                }
                OnboardingStep.FIRST -> {
                    ScreenTitle("Ready")
                    Text(
                        "Start with something you know well. Talk for a few minutes; ${state.name} asks a question now and then. Tap the check when you're done and it files what you said.",
                        style = Mind.type.bodyLarge, color = c.ink,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.step != OnboardingStep.WELCOME) Pill("Back", actions.onBack, Modifier.testTag("onboardingBack"), style = PillStyle.TEXT, height = MindDimens.touch)
            Spacer(Modifier.weight(1f))
            when (state.step) {
                OnboardingStep.WELCOME -> Pill("Get started", actions.onNext, Modifier.testTag("onboardingNext"), height = MindDimens.touch)
                OnboardingStep.BUNDLE -> {
                    if (state.canDownload && state.toDownload > 0) {
                        Pill("Import instead", actions.onSkipDownload, style = PillStyle.OUTLINE, height = MindDimens.touch)
                        Pill("Download", actions.onDownload, Modifier.testTag("downloadBundle"), height = MindDimens.touch)
                    } else if (!state.canDownload && state.toDownload > 0) {
                        Pill("Import files", actions.onSkipDownload, Modifier.testTag("importInstead"), height = MindDimens.touch)
                    } else Pill("Next", { actions.onNext(); actions.onNext() }, Modifier.testTag("onboardingNext"), height = MindDimens.touch)
                }
                OnboardingStep.DOWNLOAD -> Pill(if (state.allInstalled) "Next" else "Continue", actions.onNext, Modifier.testTag("onboardingNext"), height = MindDimens.touch)
                OnboardingStep.BUTLER -> Pill("Next", actions.onSaveButler, Modifier.testTag("onboardingNext"), height = MindDimens.touch)
                OnboardingStep.MIC -> {
                    Pill("Not now", actions.onNext, style = PillStyle.OUTLINE, height = MindDimens.touch)
                    Pill("Allow microphone", actions.onAllowMic, Modifier.testTag("allowMic"), height = MindDimens.touch)
                }
                OnboardingStep.FIRST -> {
                    Pill("Later", actions.onFinishLater, Modifier.testTag("finishLater"), style = PillStyle.OUTLINE, height = MindDimens.touch)
                    Pill("Start first session", actions.onStartSession, Modifier.testTag("startFirstSession"), height = MindDimens.touch)
                }
                else -> Pill("Next", actions.onNext, Modifier.testTag("onboardingNext"), height = MindDimens.touch)
            }
        }
    }
}
