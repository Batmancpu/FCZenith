package com.music.zenith.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val STAR_REPO_URL = "https://github.com/rezapranata089-bit/Zenith"

/**
 * When to ask for a GitHub star. GitHub can't tell the app who has already
 * starred, so the prompt is a polite nudge: it waits for a few launches and a
 * few days of use, backs off after "Maybe later", and stops for good once the
 * person taps Star or has put it off a few times.
 */
private object StarPrompt {
    private const val PREFS = "star_prompt"
    private const val KEY_DONE = "done"
    private const val KEY_LAUNCHES = "launches"
    private const val KEY_FIRST_AT = "first_at"
    private const val KEY_SNOOZE_UNTIL = "snooze_until"
    private const val KEY_SNOOZES = "snoozes"

    private const val MIN_LAUNCHES = 5
    private const val MIN_AGE_MS = 3L * 24 * 60 * 60 * 1000
    private const val SNOOZE_MS = 14L * 24 * 60 * 60 * 1000
    private const val MAX_SNOOZES = 3

    /** One count per process start, however often the screen is recomposed. */
    @Volatile
    private var counted = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun registerLaunch(context: Context) {
        if (counted) return
        counted = true
        val p = prefs(context)
        val editor = p.edit().putInt(KEY_LAUNCHES, p.getInt(KEY_LAUNCHES, 0) + 1)
        if (p.getLong(KEY_FIRST_AT, 0L) == 0L) {
            editor.putLong(KEY_FIRST_AT, System.currentTimeMillis())
        }
        editor.apply()
    }

    fun shouldShow(context: Context): Boolean {
        val p = prefs(context)
        if (p.getBoolean(KEY_DONE, false)) return false
        val now = System.currentTimeMillis()
        return p.getInt(KEY_LAUNCHES, 0) >= MIN_LAUNCHES &&
            now - p.getLong(KEY_FIRST_AT, now) >= MIN_AGE_MS &&
            now >= p.getLong(KEY_SNOOZE_UNTIL, 0L)
    }

    fun snooze(context: Context) {
        val p = prefs(context)
        val snoozes = p.getInt(KEY_SNOOZES, 0) + 1
        p.edit()
            .putInt(KEY_SNOOZES, snoozes)
            .putLong(KEY_SNOOZE_UNTIL, System.currentTimeMillis() + SNOOZE_MS)
            .putBoolean(KEY_DONE, snoozes >= MAX_SNOOZES)
            .apply()
    }

    fun finish(context: Context) {
        prefs(context).edit().putBoolean(KEY_DONE, true).apply()
    }
}

/** Mount once near the app root; shows the star prompt when it is time. */
@Composable
internal fun StarPromptHost() {
    val context = LocalContext.current
    var show by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        StarPrompt.registerLaunch(context)
        if (show) return@LaunchedEffect
        // Let the app settle before asking for anything.
        delay(8_000)
        if (StarPrompt.shouldShow(context)) show = true
    }

    if (show) {
        StarPromptDialog(
            onStar = {
                StarPrompt.finish(context)
                show = false
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(STAR_REPO_URL))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.onFailure {
                    Toast.makeText(context, STAR_REPO_URL, Toast.LENGTH_SHORT).show()
                }
            },
            onLater = {
                StarPrompt.snooze(context)
                show = false
            },
        )
    }
}

@Composable
private fun StarPromptDialog(
    onStar: () -> Unit,
    onLater: () -> Unit,
) {
    GlassAlertDialog(onDismiss = onLater) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = "Enjoying Zenith?",
                modifier = Modifier.padding(top = 14.dp),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "A star on GitHub is a free way to support the project and helps others find it.",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        AlertRule()
        AlertAction(label = "Star on GitHub", emphasised = true, onClick = onStar)
        AlertRule()
        AlertAction(label = "Maybe later", emphasised = false, onClick = onLater)
    }
}