package com.luckyalanzhou.barcodegenerator.ui.dialogs

import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun UpdateAvailableDialogContent(
    latest: String,
    dark: Boolean,
    onIgnore: () -> Unit,
    onLater: () -> Unit,
    onUpdate: () -> Unit,
) {
    ComposeGlassDialogCard(dark) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("发现新版本", color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(
                "检测到版本 $latest，是否立即更新？",
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                color = LocalAppColorScheme.current.text.secondary,
                fontSize = 15.sp,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DialogAction("立即更新", dark, primary = true, onClick = onUpdate)
                DialogAction("稍后更新", dark, onClick = onLater)
                DialogAction("忽略更新", dark, onClick = onIgnore)
            }
        }
    }
}
