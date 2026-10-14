package com.luckyalanzhou.barcodegenerator.ui.dialogs

import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun UpdateAvailableDialogContent(
    latest: String,
    dark: Boolean,
    onLater: () -> Unit,
    onUpdate: () -> Unit,
) {
    ComposeGlassDialogCard(dark, horizontalPadding = 14.dp) {
        Text("发现新版本", color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
        Text(
            "检测到版本 $latest，是否立即更新？",
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            color = LocalAppColorScheme.current.text.secondary,
            fontSize = 15.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DialogAction("稍后更新", dark, onClick = onLater)
            DialogAction("立即更新", dark, primary = true, onClick = onUpdate)
        }
    }
}
