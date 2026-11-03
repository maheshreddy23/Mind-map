package com.maheshreddy.mym

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.maheshreddy.mym.data.settings.SettingsRepository
import com.maheshreddy.mym.nudges.NudgeWorker
import com.maheshreddy.mym.ui.AppNavHost
import com.maheshreddy.mym.ui.components.Pill
import com.maheshreddy.mym.ui.settings.LOCK_AUTHENTICATORS
import com.maheshreddy.mym.ui.theme.Mind
import com.maheshreddy.mym.ui.theme.MindTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var settings: SettingsRepository

    /** App lock: true while the user must authenticate. */
    private var locked by mutableStateOf(false)
    private var backgroundedAt = 0L

    /** "Talk now" from a nudge notification: the topic to start a session about ("" = any). */
    val pendingTalk = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        lifecycleScope.launch { if (settings.settings.first().appLock) { locked = true; authenticate() } }
        setContent {
            MindTheme {
                if (locked) LockScreen() else AppNavHost(pendingTalk = pendingTalk)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(NudgeWorker.EXTRA_TALK, false) == true) {
            pendingTalk.value = intent.getStringExtra(NudgeWorker.EXTRA_TALK_ABOUT).orEmpty()
        }
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Re-lock after more than a minute away.
        if (backgroundedAt > 0 && SystemClock.elapsedRealtime() - backgroundedAt > 60_000) {
            lifecycleScope.launch { if (settings.settings.first().appLock) { locked = true; authenticate() } }
        }
    }

    private fun authenticate() {
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { locked = false }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock MYM")
                .setSubtitle("Your library is private")
                .setAllowedAuthenticators(LOCK_AUTHENTICATORS)
                .build(),
        )
    }

    @androidx.compose.runtime.Composable
    private fun LockScreen() {
        Box(Modifier.fillMaxSize().background(Mind.colors.ground), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("MYM is locked", style = Mind.type.screenTitle, color = Mind.colors.ink)
                Pill("Unlock", { authenticate() })
            }
        }
    }
}
