package com.music.zenith.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Earbuds
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.zenith.sharedui.resources.*
import com.music.zenith.ui.haptics.Haptic
import com.music.zenith.ui.haptics.rememberHaptics
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Where the music is coming out, and how loud.
 *
 * Replaces two things that did not work. On Android 14 and up the headphones
 * glyph opened the *system* output switcher — a panel this app does not control,
 * styled like nothing else here, and one that routes the whole phone rather than
 * this player. Below that it opened a stock [android.app.AlertDialog] driven by
 * [android.media.MediaRouter], whose `selectRoute` is a request the framework is
 * free to ignore, and usually did.
 *
 * This one switches by telling our own player which sink to render into, which
 * is the only routing decision the app owns and the one that actually takes
 * effect — see [AudioRouting].
 *
 * A floating [PlayerPopup] over the player, which swells in like jelly and
 * shrinks away like a macOS popover.
 * Outputs come first and volume follows. Every available device stays visible:
 * on a phone there are usually two, so a disclosure would cost a tap merely to
 * reveal a single row.
 */
@Composable
internal fun AudioOutputSheet(
    hazeState: HazeState,
    accountName: String?,
    onDismiss: () -> Unit,
    onOpenPipeline: () -> Unit,
    onOpenCast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outputs = rememberAudioOutputs()
    val cast by PlayerPlatform.host.castState.collectAsStateWithLifecycle()
    val watch by PlayerPlatform.host.watchOutput.collectAsStateWithLifecycle()
    // A watch comes and goes; ask again each time the drawer opens.
    LaunchedEffect(Unit) { PlayerPlatform.host.refreshWatchOutput() }

    PlayerPopup(
        hazeState = hazeState,
        title = stringResource(Res.string.audio_output),
        onDismiss = onDismiss,
        modifier = modifier,
        titleGap = 10.dp,
    ) {
        // The playing output is the lit pill; the rest sit flat beside it.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            outputs.forEach { device ->
                OutputItem(
                    icon = iconFor(device),
                    title = outputLabel(device, accountName),
                    subtitle = if (device.isActive) stringResource(Res.string.audio_output_playing) else null,
                    active = device.isActive,
                    onClick = { PlayerPlatform.host.selectAudioOutput(device.id) },
                )
            }
            // The watch, when one with Zenith on it is in reach: picked
            // directly, since there is only ever the one.
            if (watch.available || watch.active) {
                OutputItem(
                    icon = Icons.Rounded.Watch,
                    title = watch.name?.takeIf(String::isNotBlank) ?: stringResource(Res.string.watch_output),
                    subtitle = stringResource(
                        if (watch.active) Res.string.audio_output_playing else Res.string.watch_output_subtitle,
                    ),
                    active = watch.active,
                    onClick = { PlayerPlatform.host.selectWatchOutput() },
                )
            }
            // The receivers behind Cast are chosen in a popup of their own.
            if (cast.supported) {
                val casting = cast.connectedName != null
                OutputItem(
                    icon = if (casting) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                    title = cast.connectedName ?: stringResource(Res.string.cast),
                    subtitle = stringResource(
                        when {
                            cast.connecting -> Res.string.cast_connecting
                            casting -> Res.string.cast_casting
                            else -> Res.string.cast_row_subtitle
                        },
                    ),
                    active = casting,
                    chevron = true,
                    onClick = onOpenCast,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        VolumeRow(routeKey = outputs)

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(0.5.dp)
                .background(Color.White.copy(alpha = 0.10f)),
        )
        Spacer(Modifier.height(6.dp))
        AudioPipelineRow(onClick = onOpenPipeline)
    }
}

/**
 * Drills into [com.music.zenith.ui.components.AudioPipelineDialog] — the
 * subtitle is the negotiated output itself, read live off
 * [AudioOutputStatus], so the row states what's actually leaving the phone
 * before anyone taps in for the rest of the chain.
 */
@Composable
private fun AudioPipelineRow(onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val outputFormat by PlayerPlatform.host.outputFormat.collectAsStateWithLifecycle()
    val subtitle = outputFormat.summary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.play(Haptic.Select)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.audio_pipeline),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Dark ink for text and glyphs on the lit (white) pill. */
private val OUTPUT_INK = Color(0xFF1C1C1E)

/**
 * One place the music can go. The playing one is a lit white pill with a
 * speaker mark; the rest are flat rows that only fill under a finger. [chevron]
 * marks a row that opens a picker of its own rather than switching.
 */
@Composable
private fun OutputItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    active: Boolean,
    onClick: () -> Unit,
    chevron: Boolean = false,
) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateColorAsState(
        targetValue = when {
            active -> Color.White
            pressed -> Color.White.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        animationSpec = tween(160),
        label = "outputItemFill",
    )
    val ink = if (active) OUTPUT_INK else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(fill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = chevron || !active,
            ) {
                haptics.play(Haptic.Select)
                onClick()
            }
            .heightIn(min = 54.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ink.copy(alpha = if (active) 1f else 0.72f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    letterSpacing = (-0.2).sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = ink.copy(alpha = if (active) 0.55f else 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            chevron -> Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = ink.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp),
            )
            active -> Icon(
                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The phone's media volume, on the player's own slider rather than Material's.
 *
 * Two things move it, and only one of them announces itself. The hardware keys
 * broadcast `VOLUME_CHANGED_ACTION`, which is easy. A *route* change does not:
 * Android keeps one media volume index per output and silently swaps which one
 * is in force, so the number behind this bar changes with no event at all. Read
 * once at composition, as this was, the bar went on showing the level of the
 * device the listener had just moved away from.
 *
 * So [routeKey] — the outputs and the current choice — re-reads it, and re-reads
 * it again a beat later, because the framework swaps the index a moment after
 * the device list changes rather than in the same breath.
 */
@Composable
private fun VolumeRow(routeKey: Any) {
    val system = PlayerPlatform.host.volume
    val systemLevel by system.level.collectAsStateWithLifecycle()
    var level by remember(system) { mutableFloatStateOf(systemLevel) }
    var dragging by remember { mutableStateOf(false) }

    // The hardware keys move the system level; follow it, but never over a
    // finger: the listener's own drag is the one source of truth this must
    // not fight.
    LaunchedEffect(systemLevel) {
        if (!dragging) level = systemLevel
    }

    LaunchedEffect(routeKey) {
        repeat(VOLUME_REREADS) {
            if (!dragging) system.refresh()
            delay(VOLUME_REREAD_GAP_MS)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (level > 0f) Icons.AutoMirrored.Rounded.VolumeDown else Icons.AutoMirrored.Rounded.VolumeOff,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        ThinSlider(
            value = level,
            onValueChange = {
                dragging = true
                level = it
                system.set(it)
            },
            onValueChangeFinished = { dragging = false },
            idleHeight = 6.dp,
            activeHeight = 10.dp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun iconFor(device: AudioOutputDevice): ImageVector = when (device.kind) {
    AudioOutputKind.PHONE -> Icons.Rounded.PhoneAndroid
    AudioOutputKind.WIRED -> Icons.Rounded.Headphones
    AudioOutputKind.USB -> Icons.Rounded.Usb
    // The same reading the output pill draws — see AudioOutputDevice.form.
    AudioOutputKind.BLUETOOTH -> when (device.form) {
        BluetoothForm.BUDS -> Icons.Rounded.Earbuds
        BluetoothForm.HEADPHONES -> Icons.Rounded.Headphones
        BluetoothForm.SPEAKER -> Icons.Rounded.Speaker
        BluetoothForm.CAR -> Icons.Rounded.DirectionsCar
        BluetoothForm.UNKNOWN -> Icons.Rounded.Bluetooth
    }
    AudioOutputKind.HDMI -> Icons.Rounded.Tv
    AudioOutputKind.OTHER -> Icons.Rounded.Speaker
}

/** How many times the level is re-read after a route change, and how far apart. */
private const val VOLUME_REREADS = 4
private const val VOLUME_REREAD_GAP_MS = 250L
