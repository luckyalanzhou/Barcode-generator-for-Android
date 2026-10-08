package com.luckyalanzhou.barcodegenerator.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme

/** A value picker, not an action menu. Reserve the mark column for every option. */
@Composable
internal fun SingleChoiceMenuItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColorScheme.current
    DropdownMenuItem(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) colors.controls.selectedContainer else androidx.compose.ui.graphics.Color.Transparent)
            .heightIn(min = 40.dp)
            .semantics { selected = isSelected },
        text = { Text(label, color = colors.text.primary, maxLines = 1, softWrap = false) },
        trailingIcon = {
            Box(Modifier.size(20.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                if (isSelected) Text("✓", color = colors.controls.accent, fontSize = 17.sp)
            }
        },
        onClick = onClick,
    )
}
