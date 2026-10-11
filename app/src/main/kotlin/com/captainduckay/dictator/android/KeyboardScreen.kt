package com.captainduckay.dictator.android

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.captainduckay.dictator.core.DictationController
import com.captainduckay.dictator.core.DictationState

/** Same layout as the iOS keyboard: a switch-keyboard button, one big mic button, one caption. */
@Composable
fun KeyboardScreen(
    controller: DictationController,
    hasMicrophone: () -> Boolean,
    onSwitchKeyboard: () -> Unit,
    onOpenApp: () -> Unit,
    onRetry: () -> Unit,
    onBeforePress: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val level by controller.level.collectAsStateWithLifecycle()
    val error by controller.lastError.collectAsStateWithLifecycle()
    val undelivered by controller.undeliveredTranscript.collectAsStateWithLifecycle()

    DictatorTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .navigationBarsPadding()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = onSwitchKeyboard) { Text("🌐  " + stringResource(R.string.keyboard_switch)) }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when {
                        !hasMicrophone() -> Explanation(stringResource(R.string.keyboard_needs_microphone), onOpenApp)
                        state == DictationState.LoadingModel -> {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.status_loading), style = MaterialTheme.typography.bodySmall)
                        }
                        state is DictationState.Unavailable ->
                            Explanation((state as DictationState.Unavailable).message, onOpenApp)
                        else -> {
                            MicButton(state, level, onPress = {
                                onBeforePress()
                                controller.pressed()
                            }, onRelease = controller::released)
                            Spacer(Modifier.height(8.dp))
                            Text(caption(state), style = MaterialTheme.typography.bodySmall)
                            val banner = error
                            if (banner != null) {
                                Text(
                                    banner,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            if (undelivered != null && state == DictationState.Ready) {
                                Row {
                                    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_try_again)) }
                                    TextButton(onClick = controller::discardUndelivered) {
                                        Text(stringResource(R.string.action_discard))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun caption(state: DictationState) =
    when (state) {
        DictationState.Recording -> stringResource(R.string.caption_recording)
        DictationState.Transcribing -> stringResource(R.string.caption_transcribing)
        else -> stringResource(R.string.caption_ready)
    }

@Composable
private fun MicButton(state: DictationState, level: Float, onPress: () -> Unit, onRelease: () -> Unit) {
    val color =
        when (state) {
            DictationState.Recording -> Color(0xFFD32F2F)
            DictationState.Transcribing -> Color(0xFFF57C00)
            else -> MaterialTheme.colorScheme.primary
        }
    val label = stringResource(R.string.mic_label)
    val hint = stringResource(R.string.mic_hint)
    val description = caption(state)
    Box(
        Modifier
            .size(96.dp)
            .scale(if (state == DictationState.Recording) 1f + level * 0.15f else 1f)
            .clip(CircleShape)
            .background(color)
            .semantics {
                role = Role.Button
                contentDescription = label
                stateDescription = description
                onClick(label = hint) {
                    onPress()
                    true
                }
            }.pointerInput(Unit) {
                // Press starts (or stops) a dictation; release after a hold ends push-to-talk.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPress()
                    waitForUpOrCancellation()
                    onRelease()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (state == DictationState.Transcribing) "…" else "🎙", style = MaterialTheme.typography.headlineLarge)
    }
}

@Composable
private fun Explanation(text: String, onOpenApp: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
    TextButton(onClick = onOpenApp) { Text(stringResource(R.string.action_open_dictator)) }
}
