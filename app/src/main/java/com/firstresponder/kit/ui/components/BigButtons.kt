package com.firstresponder.kit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared minimum height for the primary controls. Comfortably above the 48 dp guideline. */
val LargeTouchTarget = 72.dp

/**
 * A full-width action button sized for one-handed use under stress.
 *
 * @param modifier callers usually pass `Modifier.weight(1f)` to share the screen height.
 */
@Composable
fun BigActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    icon: ImageVector? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = LargeTouchTarget),
        shape = MaterialTheme.shapes.large,
        colors = colors,
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (emoji != null) {
                Text(
                    text = emoji,
                    fontSize = 44.sp,
                    // Decoration only — the label already names the button.
                    modifier = Modifier
                        .padding(end = 20.dp)
                        .clearAndSetSemantics { },
                )
            }
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(32.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Secondary full-width button, used for Settings and Back. */
@Composable
fun BigOutlinedButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .padding(end = 12.dp)
                .size(28.dp),
        )
        Text(text = label, style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * A row of mutually exclusive options (theme, patient type, …).
 *
 * Generic so every "pick one of N" setting looks and behaves the same. [emoji] is optional
 * and sits above the label on its own line.
 */
@Composable
fun <T> SingleChoiceRow(
    options: List<T>,
    selected: T,
    // Composable so callers can resolve string resources per option.
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    emoji: ((T) -> String)? = null,
) {
    val unselectedBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Button(
                onClick = { onSelect(option) },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 56.dp)
                    .semantics { this.selected = isSelected },
                shape = MaterialTheme.shapes.medium,
                colors = if (isSelected) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
                border = if (isSelected) null else unselectedBorder,
                // Tighter than the button default: four options share the screen width and
                // every dp of it belongs to the label.
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (emoji != null) {
                        Text(
                            text = emoji(option),
                            fontSize = 22.sp,
                            lineHeight = 26.sp,
                            // Decoration only — the label already names the option.
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                    }
                    FitOnOneLineText(
                        text = label(option),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

/** Heading above a related block of settings. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
    )
}
