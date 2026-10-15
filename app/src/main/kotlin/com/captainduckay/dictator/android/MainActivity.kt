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
import com.captainduckay.dictator.core.ModelStartup
import com.captainduckay.dictator.core.SpeechModelId
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
    var typing by remember { mutableStateOf(activity.isTypingEnabled()) }
    val buttonOn by FloatingButtonService.running.collectAsState()
    var language by remember { mutableStateOf(settings.language) }
    var whatsNew by remember { mutableStateOf(initialWhatsNew) }
    var showNotices by remember { mutableStateOf(false) }
    var askedOnce by remember { mutableStateOf(false) }
    // Nothing downloads at launch. With no model chosen, the picker asks (ADR 0008).
    var chosenModel by remember { mutableStateOf(startupModel(activity)) }
    var showModelPicker by remember { mutableStateOf(chosenModel == null) }
    val chooseModel = { model: SpeechModelId ->
        SpeechModel.choose(activity, model)
        chosenModel = model
        showModelPicker = false
    }
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
    // Settings can change while we're away (permission, accessibility): re-check on return.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        microphone = activity.hasMicrophone()
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
                    !typing -> {
                        Text(stringResource(R.string.typing_needed))
                        Footnote(stringResource(R.string.restricted_settings_hint))
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
                Footnote(stringResource(R.string.shortcut_hint))
            }
            Section(stringResource(R.string.section_language)) {
                if (chosenModel?.englishOnly == true) Footnote(stringResource(R.string.language_english_only))
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
            Section(stringResource(R.string.section_model)) { ModelSection(chosenModel) }
            Section(stringResource(R.string.section_models)) { ModelList(chosenModel, chooseModel) }
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
    if (showModelPicker) {
        ModelPickerDialog(onChoose = chooseModel, onDismiss = { showModelPicker = false })
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

/** The model at launch: the saved choice, or a verified Whisper download from before the picker. */
private fun startupModel(context: android.content.Context): SpeechModelId? =
    when (val start = ModelStartup.decide(SpeechModel.chosen(context), SpeechModel.installed(context))) {
        ModelStartup.ShowPicker -> null
        is ModelStartup.Use ->
            start.model.also {
                if (SpeechModel.chosen(context) ==
                    null
                ) {
                    SpeechModel.choose(context, it)
                }
            }
    }

private fun megabytes(bytes: Long) = String.format(Locale.ROOT, "%.0f", bytes / 1_000_000.0)

@Composable
private fun accuracyNote(model: SpeechModelId) =
    stringResource(
        when (model) {
            SpeechModelId.PARAKEET_V2 -> R.string.model_parakeet_accuracy
            SpeechModelId.WHISPER_SMALL -> R.string.model_whisper_accuracy
        },
    )

@Composable
private fun speedNote(model: SpeechModelId) =
    stringResource(
        when (model) {
            SpeechModelId.PARAKEET_V2 -> R.string.model_parakeet_speed
            SpeechModelId.WHISPER_SMALL -> R.string.model_whisper_speed
        },
    )

/** One model's name, size, languages, accuracy and speed. Used by the picker and the list. */
@Composable
private fun ModelSummary(model: SpeechModelId, recommended: Boolean) {
    val context = LocalContext.current
    val size = remember(model) { megabytes(SpeechModel.downloadBytes(context, model)) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            if (recommended) {
                stringResource(R.string.model_recommended, SpeechModel.displayName(model))
            } else {
                SpeechModel.displayName(model)
            },
            style = MaterialTheme.typography.titleSmall,
        )
        Footnote(
            stringResource(
                if (model.englishOnly) R.string.model_meta_english else R.string.model_meta_multilingual,
                size,
            ),
        )
        Text(accuracyNote(model), style = MaterialTheme.typography.bodyMedium)
        Footnote(speedNote(model))
    }
}

/**
 * Shown at launch only while no model is chosen. Lists the models best first. Choosing one downloads
 * nothing: the Model section then asks, with the usual Wi-Fi / mobile data choice.
 */
@Composable
private fun ModelPickerDialog(onChoose: (SpeechModelId) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(SpeechModelId.ranked.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.model_picker_title)) },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.model_picker_intro), style = MaterialTheme.typography.bodyMedium)
                SpeechModelId.ranked.forEachIndexed { index, model ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == model, role = Role.RadioButton, onClick = {
                                selected = model
                            }),
                        verticalAlignment = Alignment.Top,
                    ) {
                        RadioButton(selected = selected == model, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) { ModelSummary(model, recommended = index == 0) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoose(selected) }) { Text(stringResource(R.string.action_choose_model)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_later)) } },
    )
}

/** Switch to another model (download on demand, same opt-in) or delete one that isn't in use. */
@Composable
private fun ModelList(chosen: SpeechModelId?, onChoose: (SpeechModelId) -> Unit) {
    val context = LocalContext.current
    val work by remember { ModelDownloadWorker.observe(context) }.collectAsState(initial = emptyList())
    val busy = work.any { !it.state.isFinished }
    var revision by remember { mutableStateOf(0) }
    SpeechModelId.ranked.forEachIndexed { index, model ->
        val installed = remember(revision, work) { SpeechModel.isInstalled(context, model) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ModelSummary(model, recommended = index == 0)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (model == chosen) {
                    Footnote(stringResource(R.string.model_in_use))
                } else {
                    OutlinedButton(onClick = { onChoose(model) }, enabled = !busy) {
                        Text(stringResource(if (installed) R.string.action_use_model else R.string.action_choose_model))
                    }
                    if (installed) {
                        OutlinedButton(onClick = {
                            SpeechModel.delete(context, model)
                            revision++
                        }) { Text(stringResource(R.string.action_delete_model)) }
                    }
                }
            }
        }
    }
    if (busy) Footnote(stringResource(R.string.model_switch_busy))
}

/**
 * Explicit opt-in download of the chosen model (ADR 0006, ADR 0008): shows the size, recommends
 * Wi-Fi, and only uses mobile data when the user ticks the box. Nothing downloads until they tap.
 */
@Composable
private fun ModelSection(chosen: SpeechModelId?) {
    if (chosen == null) {
        Text(stringResource(R.string.model_none_chosen))
        Footnote(stringResource(R.string.model_footnote))
        return
    }
    val context = LocalContext.current
    val name = SpeechModel.displayName(chosen)
    val sizeMb = remember(chosen) { megabytes(SpeechModel.downloadBytes(context, chosen)) }
    val work by remember { ModelDownloadWorker.observe(context) }.collectAsState(initial = emptyList())
    val info = work.firstOrNull { ModelDownloadWorker.tag(chosen) in it.tags }
    var allowMobileData by remember { mutableStateOf(false) }
    val installed = SpeechModel.isInstalled(context, chosen) || info?.state == WorkInfo.State.SUCCEEDED

    when {
        installed -> Text(stringResource(R.string.model_installed, name))
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
            Text(stringResource(R.string.model_offer, name, sizeMb))
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
            Button(onClick = { ModelDownloadWorker.start(context, chosen, allowMobileData) }) {
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
