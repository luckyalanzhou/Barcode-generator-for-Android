package com.luckyalanzhou.barcodegenerator.ui.component.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy

/** 值选项共用显示：勾号表示保存状态，底色只表示当前按压或悬停，不参与业务状态更新。 */
@Composable
internal fun MenuChoiceItem(label: String, checked: Boolean, multiple: Boolean = false, onClick: () -> Unit) {
    val colors = LocalAppColorScheme.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    val feedback = colors.controls.accent.copy(alpha = if (LocalVisualEffectsPolicy.current.highContrast) .20f else .10f)
    DropdownMenuItem(
        modifier = Modifier.heightIn(min = 40.dp)
            .hoverable(interactions)
            .background(if (pressed || hovered) feedback else Color.Transparent)
            .semantics {
                role = if (multiple) Role.Checkbox else Role.RadioButton
                if (multiple) toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                else selected = checked
            },
        text = { Text(label, color = colors.text.primary, maxLines = 2) },
        trailingIcon = {
            Box(Modifier.size(20.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                if (checked) Text("✓", color = colors.controls.accent, fontSize = 17.sp)
            }
        },
        interactionSource = interactions,
        onClick = onClick,
    )
}
