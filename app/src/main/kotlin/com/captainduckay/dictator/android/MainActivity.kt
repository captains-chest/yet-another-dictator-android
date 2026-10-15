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
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.work.WorkInfo
import com.captainduckay.dictator.core.AppVersion
import com.captainduckay.dictator.core.Changelog
import com.captainduckay.dictator.core.ModelStartup
import com.captainduckay.dictator.core.SpeechModelId
import com.captainduckay.dictator.core.WhatsNew
import java.util.Locale
import android.provider.Settings as AndroidSettings

/**
 * Setup only: microphone, typing, the floating button and the model, plus a field to try it.
 * Dictation itself runs in [FloatingButtonService] (ADR 0007).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settings = Settings(this)
        val whatsNew = whatsNewDecision(settings)
        setContent { DictatorTheme { RootScreen(whatsNew) } }
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

@Composable
private fun RootScreen(initialWhatsNew: WhatsNew.Decision) {
    val activity = LocalActivity.current as MainActivity
    var microphone by remember { mutableStateOf(activity.hasMicrophone()) }
    var typing by remember { mutableStateOf(activity.isTypingEnabled()) }
    val buttonOn by FloatingButtonService.running.collectAsState()
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
    // After a permanent "Don't allow", only the app's settings screen can grant the microphone.
    val micBlocked =
        !microphone && askedOnce && !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
    // Settings can change while we're away (permission, accessibility): re-check on return.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        microphone = activity.hasMicrophone()
        typing = activity.isTypingEnabled()
    }

    Column(
        Modifier
            .fillMaxSize()
            .sea()
            // Insets outside the scroll, so the boards never slide under the status bar or keyboard.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        HeaderPlank(stringResource(R.string.app_name), Modifier.fillMaxWidth().semantics { heading() })

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BoardCard(
                stringResource(R.string.section_ready),
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(top = 14.dp, bottom = 4.dp),
                titleInset = 16.dp,
            ) {
                Column {
                    SetupRow(
                        icon = R.drawable.ic_mic,
                        title = stringResource(R.string.setup_microphone),
                        detail =
                            stringResource(
                                when {
                                    microphone -> R.string.setup_microphone_on
                                    micBlocked -> R.string.setup_microphone_blocked
                                    else -> R.string.setup_microphone_off
                                },
                            ),
                        checked = microphone,
                        onClick = {
                            if (microphone || micBlocked) {
                                activity.openAppSettings()
                            } else {
                                permission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    )
                    SetupRow(
                        icon = R.drawable.ic_keyboard,
                        title = stringResource(R.string.setup_typing),
                        detail = stringResource(if (typing) R.string.setup_typing_on else R.string.setup_typing_off),
                        checked = typing,
                        onClick = activity::openTypingSettings,
                        divider = true,
                        tilt = -6f,
                    )
                    SetupRow(
                        icon = R.drawable.ic_sparkle,
                        title = stringResource(R.string.setup_button),
                        detail = stringResource(if (buttonOn) R.string.setup_button_on else R.string.setup_button_off),
                        checked = buttonOn,
                        onClick = { if (buttonOn) activity.hideButton() else showButton() },
                        divider = true,
                        tilt = 6f,
                    )
                }
            }
            if (!typing) {
                Note(stringResource(R.string.typing_needed))
                Note(stringResource(R.string.restricted_settings_hint))
            } else {
                Note(stringResource(R.string.button_ready))
                Note(stringResource(R.string.shortcut_hint))
            }
        }

        BoardCard(
            stringResource(R.string.section_model),
            Modifier.fillMaxWidth(),
            rivets = Rivets.Corners,
            decorated = true,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 14.dp),
            titleInset = 14.dp,
        ) { ModelCard(chosenModel, chooseModel) }

        TryIt()

        BoardCard(stringResource(R.string.section_about), Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.titleSmall,
                color = Chest.CreamBright,
            )
            Footnote(stringResource(R.string.about_privacy))
            Footnote(stringResource(R.string.microphone_footnote))
            Footnote(stringResource(R.string.button_footnote))
            ChestButton(stringResource(R.string.action_notices), { showNotices = true }, primary = false)
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

/** One setup step: a coin icon, what it is, and a switch showing whether it is done. */
@Composable
private fun SetupRow(
    @DrawableRes icon: Int,
    title: String,
    detail: String,
    checked: Boolean,
    onClick: () -> Unit,
    divider: Boolean = false,
    tilt: Float = 0f,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (divider) {
                    Modifier.drawBehind {
                        val line = 1.dp.toPx()
                        drawRect(Color.Black.copy(alpha = 0.35f), size = Size(size.width, line))
                        drawRect(
                            Chest.Rivet.copy(alpha = 0.08f),
                            topLeft = Offset(0f, line),
                            size = Size(size.width, line),
                        )
                    }
                } else {
                    Modifier
                },
            ).toggleable(value = checked, role = Role.Switch, onValueChange = { onClick() })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Coin(icon, tilt = tilt)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Chest.CreamBright)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Chest.Muted)
        }
        ChestSwitch(checked)
    }
}

/** The field to try the floating microphone in, on parchment. */
@Composable
private fun TryIt() {
    var text by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxWidth()
            .wood(Wood.Board)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.try_title),
            style = MaterialTheme.typography.titleMedium,
            color = Chest.CreamBright,
            modifier = Modifier.semantics { heading() },
        )
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Chest.ParchmentInk),
            cursorBrush = SolidColor(Chest.Brown),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            decorationBox = { field ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val r = CornerRadius(9.dp.toPx())
                            drawRoundRect(
                                Brush.verticalGradient(listOf(Chest.Parchment, Chest.ParchmentDark)),
                                cornerRadius = r,
                            )
                            drawRoundRect(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent),
                                    endY = 6.dp.toPx(),
                                ),
                                cornerRadius = r,
                            )
                            val ink = 2.dp.toPx()
                            drawRoundRect(
                                Chest.Ink,
                                topLeft = Offset(ink / 2, ink / 2),
                                size = Size(size.width - ink, size.height - ink),
                                cornerRadius = r,
                                style = Stroke(ink),
                            )
                        }.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (text.isEmpty()) {
                        Text(
                            stringResource(R.string.try_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Chest.ParchmentHint,
                        )
                    }
                    field()
                }
            },
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

/** The card's short name; the full one is in the picker and the download text. */
private fun shortName(model: SpeechModelId) =
    when (model) {
        SpeechModelId.PARAKEET_V2 -> "Parakeet"
        SpeechModelId.WHISPER_SMALL -> "Whisper Small"
    }

@Composable
private fun shortNote(model: SpeechModelId) =
    stringResource(
        when (model) {
            SpeechModelId.PARAKEET_V2 -> R.string.model_parakeet_note
            SpeechModelId.WHISPER_SMALL -> R.string.model_whisper_note
        },
    )

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

/** One model's name, size, accuracy and speed, for the first-launch picker. */
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
            color = Chest.CreamBright,
        )
        Footnote(stringResource(R.string.model_meta_english, size))
        Text(accuracyNote(model), style = MaterialTheme.typography.bodyMedium)
        Footnote(speedNote(model))
    }
}

/**
 * Shown at launch only while no model is chosen. Lists the models best first. Choosing one downloads
 * nothing: the model card then asks, with the usual Wi-Fi / mobile data choice.
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
                        ChestRadio(selected == model)
                        Column(Modifier.padding(start = 12.dp)) { ModelSummary(model, recommended = index == 0) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoose(selected) }) { Text(stringResource(R.string.action_choose_model)) }
        },
        dismissButton = { TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.action_later)) } },
    )
}

/**
 * The speech model card: pick a model (download on demand, same opt-in), see its download, and delete
 * one that isn't in use.
 */
@Composable
private fun ModelCard(chosen: SpeechModelId?, onChoose: (SpeechModelId) -> Unit) {
    val context = LocalContext.current
    val work by remember { ModelDownloadWorker.observe(context) }.collectAsState(initial = emptyList())
    val busy = work.any { !it.state.isFinished }
    var revision by remember { mutableIntStateOf(0) }
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SpeechModelId.ranked.forEach { model ->
            ModelOption(
                model,
                selected = model == chosen,
                enabled = model == chosen || !busy,
                onClick = { if (model != chosen) onChoose(model) },
            )
        }
    }
    Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ModelDownload(chosen)
        if (busy) Footnote(stringResource(R.string.model_switch_busy))
        val installed =
            remember(revision, work) { SpeechModelId.ranked.filter { SpeechModel.isInstalled(context, it) } }
        installed
            .filter { it != chosen }
            .forEach { model ->
                ChestButton(
                    stringResource(R.string.action_delete_named, shortName(model)),
                    {
                        SpeechModel.delete(context, model)
                        revision++
                    },
                    primary = false,
                )
            }
        Footnote(stringResource(R.string.model_footnote))
    }
}

@Composable
private fun ModelOption(model: SpeechModelId, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val megs = remember(model) { megabytes(SpeechModel.downloadBytes(context, model)) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.drawBehind {
                        val r = CornerRadius(10.dp.toPx())
                        drawRoundRect(Color.Black.copy(alpha = 0.28f), cornerRadius = r)
                        drawRoundRect(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent),
                                endY = 8.dp.toPx(),
                            ),
                            cornerRadius = r,
                        )
                        val line = 2.dp.toPx()
                        drawRoundRect(
                            Chest.Rivet.copy(alpha = 0.25f),
                            topLeft = Offset(line / 2, line / 2),
                            size = Size(size.width - line, size.height - line),
                            cornerRadius = r,
                            style = Stroke(line),
                        )
                    }
                } else {
                    Modifier
                },
            ).clip(shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChestRadio(selected)
        Column(Modifier.weight(1f)) {
            Text(
                shortName(model),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = Chest.CreamBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(shortNote(model), style = MaterialTheme.typography.bodySmall, color = Chest.Muted, maxLines = 1)
        }
        // A fixed column, so "In use" and the sizes line up on the right whatever their length.
        Box(Modifier.width(78.dp), contentAlignment = Alignment.CenterEnd) {
            Pill(
                if (selected) stringResource(R.string.model_in_use) else stringResource(R.string.model_size, megs),
                on = selected,
            )
        }
    }
}

/**
 * Explicit opt-in download of the chosen model (ADR 0006, ADR 0008): shows the size, recommends
 * Wi-Fi, and only uses mobile data when the user ticks the box. Nothing downloads until they tap.
 */
@Composable
private fun ModelDownload(chosen: SpeechModelId?) {
    if (chosen == null) {
        Body(stringResource(R.string.model_none_chosen))
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
        installed -> Body(stringResource(R.string.model_installed, name))
        info != null && !info.state.isFinished -> {
            val done = info.progress.getLong(ModelDownloadWorker.KEY_DONE, 0)
            val total = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL, 0)
            if (info.state == WorkInfo.State.RUNNING && total > 0) {
                LinearProgressIndicator(
                    progress = { done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                    color = Chest.Gold,
                    trackColor = Chest.SlateDark,
                )
                Body(stringResource(R.string.model_progress, done / 1_000_000, total / 1_000_000))
            } else {
                Body(stringResource(R.string.model_waiting))
            }
            ChestButton(
                stringResource(R.string.action_cancel),
                { ModelDownloadWorker.cancel(context) },
                primary = false,
            )
        }
        else -> {
            Body(stringResource(R.string.model_offer, name, sizeMb))
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
                Body(stringResource(R.string.model_allow_mobile), Modifier.padding(start = 12.dp))
            }
            ChestButton(
                stringResource(R.string.action_download_model, sizeMb),
                { ModelDownloadWorker.start(context, chosen, allowMobileData) },
            )
        }
    }
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = Chest.Cream)
}

@Composable
private fun Footnote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Chest.Muted)
}

/** Small print on the sea, under a board. */
@Composable
private fun Note(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = Chest.Muted.copy(alpha = 0.85f),
    )
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
