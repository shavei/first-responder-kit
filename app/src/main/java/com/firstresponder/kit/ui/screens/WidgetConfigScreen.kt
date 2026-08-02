package com.firstresponder.kit.ui.screens

import android.appwidget.AppWidgetManager
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.firstresponder.kit.R
import com.firstresponder.kit.domain.PatientType
import com.firstresponder.kit.settings.ThemeMode
import com.firstresponder.kit.ui.components.BigActionButton
import com.firstresponder.kit.ui.components.BpmStepper
import com.firstresponder.kit.ui.components.ScreenHeader
import com.firstresponder.kit.ui.components.SectionHeader
import com.firstresponder.kit.ui.components.SettingBlock
import com.firstresponder.kit.ui.components.SettingToggleRow
import com.firstresponder.kit.ui.components.SingleChoiceRow
import com.firstresponder.kit.ui.theme.FirstResponderKitTheme
import com.firstresponder.kit.util.Bpm
import com.firstresponder.kit.widget.WidgetAction
import com.firstresponder.kit.widget.WidgetColor
import com.firstresponder.kit.widget.WidgetConfig
import com.firstresponder.kit.widget.WidgetIcon
import com.firstresponder.kit.widget.WidgetRenderer
import com.firstresponder.kit.widget.WidgetValue
import kotlin.math.roundToInt

/**
 * Everything one home-screen widget can be, on one screen.
 *
 * The preview at the top is not a mock-up: it is the same [WidgetRenderer] output the
 * launcher will be given, inflated in place. Every control below therefore shows its effect
 * immediately and at the size the widget will actually be — including whether the caption
 * still fits in a single cell, which is the question a 1×1 widget lives or dies by.
 *
 * The screen is stateless. [WidgetConfigActivity] holds the config being edited and is the
 * only thing that writes it to storage, so backing out leaves an existing widget untouched.
 */
@Composable
fun WidgetConfigScreen(
    config: WidgetConfig,
    defaultBpm: Int,
    isNewWidget: Boolean,
    onConfigChange: (WidgetConfig) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.widget_config_title), onBack = onCancel)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            PreviewBlock(config = config, defaultBpm = defaultBpm)

            HorizontalDivider()

            SectionHeader(stringResource(R.string.widget_config_action))
            ActionChoiceList(
                selected = config.action,
                onSelect = { action -> onConfigChange(config.copy(action = action)) },
            )
            if (config.action.usesPatient) {
                SettingBlock(title = stringResource(R.string.widget_config_patient)) {
                    SingleChoiceRow(
                        options = PatientType.entries,
                        selected = config.patientType,
                        emoji = { type -> type.emoji },
                        label = { type -> stringResource(type.labelRes) },
                        // A rate the new patient's protocol does not allow is pulled into
                        // range rather than silently kept.
                        onSelect = { type -> onConfigChange(config.copy(patientType = type).sanitized()) },
                    )
                }
                SettingBlock(title = stringResource(R.string.widget_config_rate)) {
                    SettingToggleRow(
                        title = stringResource(R.string.widget_config_rate_follow),
                        checked = config.bpm == null,
                        onCheckedChange = { follow ->
                            onConfigChange(
                                config.copy(bpm = if (follow) null else defaultBpm).sanitized(),
                            )
                        },
                    )
                    // Only when the widget carries a rate of its own. A newborn's protocol
                    // fixes it at 120, and the stepper says so with both buttons disabled.
                    config.bpm?.let { bpm ->
                        BpmStepper(
                            bpm = bpm,
                            onAdjust = { delta ->
                                onConfigChange(config.copy(bpm = bpm + delta).sanitized())
                            },
                            range = config.patientType.rateRange,
                        )
                    }
                }
            }

            HorizontalDivider()

            SectionHeader(stringResource(R.string.widget_config_display))
            SettingBlock(title = stringResource(R.string.widget_config_icon)) {
                IconChoiceGrid(
                    selected = config.icon,
                    action = config.action,
                    onSelect = { icon -> onConfigChange(config.copy(icon = icon)) },
                )
            }
            SettingBlock(title = stringResource(R.string.widget_config_value)) {
                SingleChoiceRow(
                    options = WidgetValue.entries,
                    selected = config.value,
                    label = { value -> stringResource(value.labelRes) },
                    onSelect = { value -> onConfigChange(config.copy(value = value)) },
                )
            }
            SettingToggleRow(
                title = stringResource(R.string.widget_config_show_label),
                checked = config.showLabel,
                onCheckedChange = { show -> onConfigChange(config.copy(showLabel = show)) },
            )
            if (config.showLabel) {
                SettingBlock(
                    title = stringResource(R.string.widget_config_label),
                    summary = stringResource(R.string.widget_config_label_summary),
                ) {
                    OutlinedTextField(
                        value = config.label.orEmpty(),
                        onValueChange = { text ->
                            onConfigChange(config.copy(label = text.take(WidgetConfig.MAX_LABEL_LENGTH)))
                        },
                        placeholder = { Text(stringResource(config.action.shortLabelRes)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            HorizontalDivider()

            SectionHeader(stringResource(R.string.widget_config_appearance))
            SettingBlock(title = stringResource(R.string.widget_config_background)) {
                ColorChoiceGrid(
                    // The background is always a colour; only the ink can be automatic,
                    // which is why the grid deals in nullable ones and this one cannot be.
                    options = WidgetColor.entries,
                    selected = config.background,
                    onSelect = { color ->
                        color?.let { onConfigChange(config.copy(background = it)) }
                    },
                )
            }
            SettingBlock(title = stringResource(R.string.widget_config_foreground)) {
                ColorChoiceGrid(
                    options = listOf(null) + WidgetColor.entries,
                    selected = config.foreground,
                    onSelect = { color -> onConfigChange(config.copy(foreground = color)) },
                )
            }
            PercentSlider(
                title = stringResource(R.string.widget_config_opacity),
                value = config.backgroundOpacity,
                range = 0..100,
                onChange = { percent -> onConfigChange(config.copy(backgroundOpacity = percent)) },
            )
            PercentSlider(
                title = stringResource(R.string.widget_config_corners),
                // 50% of the shorter side is a circle; past that there is nothing left to round.
                value = config.cornerPercent,
                range = 0..50,
                onChange = { percent -> onConfigChange(config.copy(cornerPercent = percent)) },
            )
            PercentSlider(
                title = stringResource(R.string.widget_config_text_size),
                value = config.textScale,
                range = WidgetConfig.MIN_TEXT_SCALE..WidgetConfig.MAX_TEXT_SCALE,
                onChange = { percent -> onConfigChange(config.copy(textScale = percent)) },
            )

            TextButton(
                onClick = { onConfigChange(WidgetConfig(patientType = config.patientType)) },
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            ) {
                Text(stringResource(R.string.widget_config_reset))
            }
        }

        // Outside the scrolling region: the way out of this screen should never be several
        // sliders away.
        BigActionButton(
            label = stringResource(
                if (isNewWidget) R.string.widget_config_add else R.string.widget_config_save,
            ),
            onClick = onSave,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

/** The live preview, at whichever of the three common widget shapes the user picks. */
@Composable
private fun PreviewBlock(config: WidgetConfig, defaultBpm: Int) {
    var size by remember { mutableStateOf(PreviewSize.ONE_BY_ONE) }

    SettingBlock(title = stringResource(R.string.widget_config_preview)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                WidgetPreview(
                    config = config,
                    defaultBpm = defaultBpm,
                    width = size.width,
                    height = size.height,
                )
            }
        }
        Text(
            text = stringResource(R.string.widget_config_preview_size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceRow(
            options = PreviewSize.entries,
            selected = size,
            label = { option -> stringResource(option.labelRes) },
            onSelect = { option -> size = option },
        )
    }
}

/**
 * The widget itself, inflated from the real `RemoteViews`.
 *
 * `RemoteViews.apply` is what the launcher does with the update it is sent, so anything
 * that would not survive the trip — an unsupported call, a colour that turns out to be
 * unreadable — is visible here rather than after the widget is on the home screen.
 */
@Composable
private fun WidgetPreview(config: WidgetConfig, defaultBpm: Int, width: Dp, height: Dp) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.size(width, height),
        factory = { hostContext -> FrameLayout(hostContext) },
        update = { host ->
            val views = WidgetRenderer.render(
                context = context,
                // No widget of its own yet: the preview is never tapped.
                appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID,
                config = config,
                widthDp = width.value,
                heightDp = height.value,
                defaultBpm = defaultBpm,
            )
            host.removeAllViews()
            host.addView(views.apply(host.context, host))
        },
    )
}

/** The sizes the preview offers, in launcher cells. */
private enum class PreviewSize(val labelRes: Int, val width: Dp, val height: Dp) {
    ONE_BY_ONE(R.string.widget_preview_1x1, 72.dp, 72.dp),
    TWO_BY_ONE(R.string.widget_preview_2x1, 156.dp, 72.dp),
    TWO_BY_TWO(R.string.widget_preview_2x2, 156.dp, 156.dp),
}

/** The actions, stacked: each one is a sentence, and five of them do not fit across. */
@Composable
private fun ActionChoiceList(selected: WidgetAction, onSelect: (WidgetAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WidgetAction.entries.forEach { action ->
            val isSelected = action == selected
            Button(
                onClick = { onSelect(action) },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .semantics { this.selected = isSelected },
                shape = MaterialTheme.shapes.medium,
                colors = if (isSelected) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
                border = if (isSelected) null else unselectedBorder(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                action.defaultIcon.drawableRes?.let { drawable ->
                    Icon(
                        painter = painterResource(drawable),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                }
                Text(
                    text = stringResource(action.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

/** The glyphs, each shown as itself. "Auto" draws whichever one the action implies. */
@Composable
private fun IconChoiceGrid(
    selected: WidgetIcon,
    action: WidgetAction,
    onSelect: (WidgetIcon) -> Unit,
) {
    ChoiceGrid(options = WidgetIcon.entries, columns = 4) { icon ->
        ChoiceTile(
            isSelected = icon == selected,
            label = stringResource(icon.labelRes),
            onClick = { onSelect(icon) },
        ) {
            val drawable = icon.resolve(action)
            if (drawable != null) {
                Icon(
                    painter = painterResource(drawable),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                )
            } else {
                // "None": an empty box the same height, so the row does not jump.
                Spacer(Modifier.size(26.dp))
            }
        }
    }
}

/** The palette. A null option means "work it out from the background". */
@Composable
private fun ColorChoiceGrid(
    options: List<WidgetColor?>,
    selected: WidgetColor?,
    onSelect: (WidgetColor?) -> Unit,
) {
    ChoiceGrid(options = options, columns = 4) { color ->
        ChoiceTile(
            isSelected = color == selected,
            label = stringResource(color?.labelRes ?: R.string.widget_color_auto),
            onClick = { onSelect(color) },
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(
                        color = color?.let { Color(it.argb) } ?: Color.Transparent,
                        shape = CircleShape,
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
    }
}

/**
 * A wrapping grid of equal-width tiles.
 *
 * Hand-rolled rather than `FlowRow`, which is still experimental, and rather than a lazy
 * grid, which cannot be nested in a scrolling column without a fixed height.
 */
@Composable
private fun <T> ChoiceGrid(
    options: List<T>,
    columns: Int,
    tile: @Composable RowScope.(T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(columns).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowOptions.forEach { option -> tile(option) }
                // Keeps the last row's tiles the same width as every other row's.
                repeat(columns - rowOptions.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** One option in a [ChoiceGrid], styled to match the app's other single-choice rows. */
@Composable
private fun RowScope.ChoiceTile(
    isSelected: Boolean,
    label: String,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 64.dp)
            .semantics { selected = isSelected },
        shape = MaterialTheme.shapes.medium,
        colors = if (isSelected) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
        border = if (isSelected) null else unselectedBorder(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

/** A percentage with a slider and a readout, used by the three appearance settings. */
@Composable
private fun PercentSlider(
    title: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    SettingBlock(title = title) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = value.toFloat(),
                // Live rather than on release: the preview above is the readout that matters.
                onValueChange = { position -> onChange(position.roundToInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.percent, value),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .padding(start = 12.dp)
                    // Reserved so the slider does not shuffle as the number gets wider.
                    .widthIn(min = 56.dp)
                    .clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun unselectedBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun WidgetConfigScreenPreview() {
    FirstResponderKitTheme(ThemeMode.DARK) {
        Surface {
            WidgetConfigScreen(
                config = WidgetConfig(),
                defaultBpm = Bpm.DEFAULT,
                isNewWidget = true,
                onConfigChange = {},
                onSave = {},
                onCancel = {},
            )
        }
    }
}
