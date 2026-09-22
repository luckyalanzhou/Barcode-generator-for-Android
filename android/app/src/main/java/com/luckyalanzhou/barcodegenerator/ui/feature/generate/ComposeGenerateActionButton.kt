package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.ui.component.globalButtonChrome
import com.luckyalanzhou.barcodegenerator.ui.component.iosPressFeedback

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 生成页操作按钮：单一 Surface 容器，避免 Material Button 的内部背景层。 */
@Composable
internal fun ComposeGenerateActionButton(
    iconDescription: String,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    borderColor: Color? = null,
    iconSize: Dp = 22.dp,
    contentSpacing: Dp = 6.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    val transparentTextStyle = LocalTextStyle.current.merge(
        TextStyle(color = contentColor, background = Color.Transparent),
    )
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.iosPressFeedback(interactionSource).height(52.dp).globalButtonChrome(shape, 0.5.dp, borderColor).semantics { role = Role.Button },
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = borderColor?.let { BorderStroke(1.dp, it) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = iconDescription,
                    tint = contentColor,
                    modifier = Modifier.size(iconSize),
                )
                Spacer(Modifier.width(contentSpacing))
            }
            Text(label, style = transparentTextStyle.copy(fontSize = 15.sp))
        }
    }
}
