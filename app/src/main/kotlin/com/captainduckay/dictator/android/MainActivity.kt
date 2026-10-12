package com.captainduckay.dictator.android

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.work.WorkInfo
import com.captainduckay.dictator.core.AppVersion
import com.captainduckay.dictator.core.Changelog
import com.captainduckay.dictator.core.DictationLanguage
import com.captainduckay.dictator.core.WhatsNew
import java.util.Locale
import android.provider.Settings as AndroidSettings

/**
 * Setup only: microphone, the floating button, language and the model, plus a field to try it.
 * Dictation itself runs in [FloatingButtonService] (ADR 0007).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settings = Settings(this)
        val whatsNew = whatsNewDecision(settings)
        setContent { DictatorTheme { RootScreen(settings, whatsNew) } }
    }

    private fun whatsNewDecision(settings: Settings): WhatsNew.Decision {
        val current = AppVersion.parse(BuildConfig.VERSION_NAME) ?: return WhatsNew.Decision.None
        val changelog = Changelog.parse(readDoc("CHANGELOG.md"))
        val decision = WhatsNew.decide(current, settings.lastSeenVersion, changelog)
        settings.lastSeenVersion = current.toString()
        return decision
    }

    fun readDoc(name: String): String = assets.open("docs/$name").bufferedReader().use { it.readText() }

    fun hasMicrophone() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun hasNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun canOverlay() = AndroidSettings.canDrawOverlays(this)

    fun isTypingEnabled(): Boolean {
        val enabled =
            AndroidSettings.Secure.getString(contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
        val component = ComponentName(this, DictatorAccessibilityService::class.java)
        return enabled.split(':').any {
            it.equals(component.flattenToString(), ignoreCase = true) ||
                it.equals(component.flattenToShortString(), ignoreCase = true)
        }
    }

    fun openAppSettings() =
        startActivity(
            Intent(
                AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ),
        )

    fun openOverlaySettings() =
        startActivity(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))

    fun openTypingSettings() {
        val component = ComponentName(this, DictatorAccessibilityService::class.java).flattenToString()
        val bundle = Bundle().apply { putString(SETTINGS_FRAGMENT_ARG, component) }
        val intent =
            Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                putExtra(SETTINGS_FRAGMENT_ARG, component)
                putExtra(SETTINGS_FRAGMENT_BUNDLE, bundle)
            }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    fun showButton() = FloatingButtonService.show(this)

    fun hideButton() = FloatingButtonService.hide(this)

    private companion object {
        const val SETTINGS_FRAGMENT_ARG = ":settings:fragment_args_key"
        const val SETTINGS_FRAGMENT_BUNDLE = ":settings:show_fragment_args"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootScreen(settings: Settings, initialWhatsNew: WhatsNew.Decision) {
    val activity = LocalActivity.current as MainActivity
    var microphone by remember { mutableStateOf(activity.hasMicrophone()) }
    var overlay by remember { mutableStateOf(activity.canOverlay()) }
    var typing by remember { mutableStateOf(activity.isTypingEnabled()) }
    val buttonOn by FloatingButtonService.running.collectAsState()
    var language by remember { mutableStateOf(settings.language) }
    var whatsNew by remember { mutableStateOf(initialWhatsNew) }
    var showNotices by remember { mutableStateOf(false) }
    var askedOnce by remember { mutableStateOf(false) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            microphone = it
            askedOnce = true
        }
    val notifications =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            activity.showButton()
        }
    val showButton = {
        if (Build.VERSION.SDK_INT >= 33 && !activity.hasNotifications()) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            activity.showButton()
        }
    }
    // Settings can change while we're away (permission, overlay, accessibility): re-check on return.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        microphone = activity.hasMicrophone()
        overlay = activity.canOverlay()
        typing = activity.isTypingEnabled()
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section(stringResource(R.string.section_microphone)) {
                if (microphone) {
                    Text(stringResource(R.string.microphone_allowed))
                } else if (askedOnce &&
                    !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                ) {
                    Button(onClick = activity::openAppSettings) { Text(stringResource(R.string.action_open_settings)) }
                } else {
                    Button(onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text(stringResource(R.string.action_allow_microphone))
                    }
                }
                Footnote(stringResource(R.string.microphone_footnote))
            }
            Section(stringResource(R.string.section_button)) {
                when {
                    !overlay -> {
                        Text(stringResource(R.string.overlay_needed))
                        Button(onClick = activity::openOverlaySettings) {
                            Text(stringResource(R.string.action_allow_overlay))
                        }
                    }
                    !typing -> {
                        Text(stringResource(R.string.typing_needed))
                        Button(onClick = activity::openTypingSettings) {
                            Text(stringResource(R.string.action_allow_typing))
                        }
                        if (buttonOn) {
                            OutlinedButton(onClick = activity::hideButton) {
                                Text(stringResource(R.string.action_hide_button))
                            }
                        } else {
                            OutlinedButton(onClick = showButton) { Text(stringResource(R.string.action_show_button)) }
                        }
                    }
                    else -> {
                        Text(stringResource(R.string.button_ready))
                        if (buttonOn) {
                            OutlinedButton(onClick = activity::hideButton) {
                                Text(stringResource(R.string.action_hide_button))
                            }
                        } else {
                            Button(onClick = showButton) { Text(stringResource(R.string.action_show_button)) }
                        }
                    }
                }
                Footnote(stringResource(R.string.button_footnote))
            }
            Section(stringResource(R.string.section_language)) {
                Column(Modifier.selectableGroup()) {
                    DictationLanguage.entries.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = language == option, role = Role.RadioButton, onClick = {
                                    language = option
                                    settings.language = option
                                })
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = language == option, onClick = null)
                            Text(
                                if (option ==
                                    DictationLanguage.AUTOMATIC
                                ) {
                                    stringResource(R.string.language_automatic)
                                } else {
                                    option.displayName
                                },
                                Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
            Section(stringResource(R.string.section_model)) { ModelSection() }
            Section(stringResource(R.string.section_try)) {
                var text by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = { Text(stringResource(R.string.try_placeholder)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            Section(stringResource(R.string.section_about)) {
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
                Footnote(stringResource(R.string.about_privacy))
                TextButton(onClick = { showNotices = true }) { Text(stringResource(R.string.action_notices)) }
            }
        }
    }

    when (val decision = whatsNew) {
        is WhatsNew.Decision.Welcome, is WhatsNew.Decision.Updated -> {
            val entries =
                (decision as? WhatsNew.Decision.Welcome)?.entries ?: (decision as WhatsNew.Decision.Updated).entries
            if (entries.isNotEmpty()) {
                TextDialog(
                    title = stringResource(whatsNewTitle(decision)),
                    body = entries.joinToString("\n\n") { "${it.version}\n${it.body}" },
                    onDismiss = { whatsNew = WhatsNew.Decision.None },
                )
            }
        }
        WhatsNew.Decision.None -> Unit
    }
    if (showNotices) {
        TextDialog(
            title = stringResource(R.string.action_notices),
            body =
                listOf("LICENSE", "THIRD_PARTY_NOTICES.md", "licenses/Apache-2.0.txt").joinToString("\n\n") {
                    activity.readDoc(it)
                },
            onDismiss = { showNotices = false },
        )
    }
}

/**
 * Explicit opt-in download (ADR 0006): shows the size, recommends Wi-Fi, and only uses mobile data
 * when the user ticks the box. Nothing downloads until they tap the button.
 */
@Composable
private fun ModelSection() {
    val context = LocalContext.current
    val sizeMb = remember { String.format(Locale.ROOT, "%.0f", SpeechModel.downloadBytes(context) / 1_000_000.0) }
    val work by remember { ModelDownloadWorker.observe(context) }.collectAsState(initial = emptyList())
    val info = work.firstOrNull()
    var allowMobileData by remember { mutableStateOf(false) }
    val installed = SpeechModel.isInstalled(context) || info?.state == WorkInfo.State.SUCCEEDED

    when {
        installed -> Text(stringResource(R.string.model_installed, SpeechModel.DISPLAY_NAME))
        info != null && !info.state.isFinished -> {
            val done = info.progress.getLong(ModelDownloadWorker.KEY_DONE, 0)
            val total = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL, 0)
            if (info.state == WorkInfo.State.RUNNING && total > 0) {
                LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.model_progress, done / 1_000_000, total / 1_000_000))
            } else {
                Text(stringResource(R.string.model_waiting))
            }
            OutlinedButton(
                onClick = { ModelDownloadWorker.cancel(context) },
            ) { Text(stringResource(R.string.action_cancel)) }
        }
        else -> {
            Text(stringResource(R.string.model_offer, SpeechModel.DISPLAY_NAME, sizeMb))
            if (info?.state == WorkInfo.State.FAILED) {
                Text(stringResource(R.string.model_failed), color = MaterialTheme.colorScheme.error)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = allowMobileData, role = Role.Checkbox, onClick = {
                        allowMobileData = !allowMobileData
                    }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = allowMobileData, onCheckedChange = null)
                Text(stringResource(R.string.model_allow_mobile), Modifier.padding(start = 12.dp))
            }
            Button(onClick = { ModelDownloadWorker.start(context, allowMobileData) }) {
                Text(stringResource(R.string.action_download_model, sizeMb))
            }
        }
    }
    Footnote(stringResource(R.string.model_footnote))
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun Footnote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TextDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
        title = { Text(title) },
        text = {
            Text(
                body,
                Modifier.verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
            )
        },
    )
}

private fun whatsNewTitle(decision: WhatsNew.Decision) =
    if (decision is WhatsNew.Decision.Welcome) R.string.whats_new_welcome else R.string.whats_new_title
