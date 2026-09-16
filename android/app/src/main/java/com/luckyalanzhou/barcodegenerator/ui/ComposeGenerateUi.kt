package com.luckyalanzhou.barcodegenerator

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ComposeGeneratePage(activity: MainActivity, initialFormat: String) {
    val values = remember {
        mutableStateListOf<String>().apply {
            addAll(activity.inputDraft.ifEmpty { mutableListOf("") })
        }
    }
    var focusedIndex by remember { mutableStateOf(-1) }
    var formatName by remember { mutableStateOf(initialFormat) }
    var formatExpanded by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    val dark = activity.isDark()
    val textColor = if (dark) Color(0xfff2f4f7) else Color(0xff172033)
    val secondary = if (dark) Color(0xffc5cedb) else Color(0xff667085)
    val cardColor = if (dark) Color(0xff182330).copy(alpha = 0.9f) else Color.White.copy(alpha = 0.88f)
    val inputColor = if (dark) Color(0xff202c3a) else Color(0xfff4f6fa)

    fun syncDraft() { activity.inputDraft = values.toMutableList() }

    DisposableEffect(Unit) {
        activity.composeGenerateTextImport = { imported ->
            values.clear()
            values.addAll(imported)
            focusedIndex = -1
            syncDraft()
        }
        onDispose { activity.composeGenerateTextImport = null }
    }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text("\u6e05\u7a7a\u6240\u6709\u8f93\u5165\uff1f") },
            text = { Text("\u5c06\u5220\u9664\u5f53\u524d\u6240\u6709\u8f93\u5165\u5185\u5bb9\uff0c\u5e76\u4fdd\u7559\u4e00\u4e2a\u7a7a\u767d\u8f93\u5165\u6846\u3002") },
            confirmButton = { Button(onClick = { values.clear(); values.add(""); syncDraft(); clearDialog = false }) { Text("\u6e05\u7a7a") } },
            dismissButton = { OutlinedButton(onClick = { clearDialog = false }) { Text("\u53d6\u6d88") } }
        )
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 296.dp).clip(RoundedCornerShape(18.dp))
                .background(cardColor).padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                values.forEachIndexed { index, value ->
                    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = value,
                            onValueChange = { values[index] = it; syncDraft() },
                            singleLine = true,
                            textStyle = TextStyle(color = textColor, fontSize = 16.sp),
                            cursorBrush = SolidColor(textColor),
                            modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(inputColor)
                                .onFocusChanged { if (it.isFocused) focusedIndex = index }.padding(horizontal = 12.dp, vertical = 13.dp),
                            decorationBox = { field ->
                                Box { if (value.isEmpty()) Text("\u8f93\u5165\u4e00\u884c\u6761\u7801\u5185\u5bb9", color = secondary, fontSize = 16.sp); field() }
                            }
                        )
                        if (values.size > 1) {
                            Spacer(Modifier.width(4.dp))
                            SmallInputAction("\u2191", enabled = index > 0) {
                                val other = values[index - 1]; values[index - 1] = values[index]; values[index] = other; syncDraft()
                            }
                            SmallInputAction("\u2193", enabled = index < values.lastIndex) {
                                val other = values[index + 1]; values[index + 1] = values[index]; values[index] = other; syncDraft()
                            }
                            SmallInputAction("\u00d7", enabled = true, onLongClick = { clearDialog = true }) {
                                if (values.size == 1) values[0] = "" else values.removeAt(index); syncDraft()
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (values.size >= 100) activity.toast("\u6700\u591a\u4fdd\u7559 100 \u884c\u8f93\u5165\u6846")
                    else { val at = (focusedIndex + 1).coerceIn(0, values.size); values.add(at, ""); focusedIndex = at; syncDraft() }
                }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = cardColor, contentColor = textColor)
            ) { Text("+ \u6dfb\u52a0\u4e00\u884c", fontSize = 15.sp) }
            OutlinedButton(onClick = { activity.captureText() }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(18.dp)) {
                Icon(painterResource(R.drawable.ic_camera), "\u62cd\u7167\u53d6\u5b57", Modifier.size(22.dp)); Spacer(Modifier.width(6.dp)); Text("\u62cd\u7167\u53d6\u5b57", fontSize = 15.sp)
            }
        }

        Box {
            Row(
                Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(18.dp)).background(cardColor).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("\u6761\u7801\u7c7b\u578b", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(Modifier.clickable { formatExpanded = true }) {
                    Text(formatName, color = secondary, fontSize = 15.sp, maxLines = 1)
                    DropdownMenu(
                        expanded = formatExpanded,
                        onDismissRequest = { formatExpanded = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = if (dark) Color(0xff252a33).copy(alpha = .98f) else Color.White.copy(alpha = .94f),
                        tonalElevation = 0.dp,
                        shadowElevation = 3.dp,
                    ) {
                        activity.formats.forEachIndexed { index, (name, _) ->
                            if (index > 0) ComposeDropdownDivider(dark)
                            DropdownMenuItem(text = { Text(name) }, onClick = { formatName = name; activity.generateFormatName = name; formatExpanded = false })
                        }
                    }
                }
            }
        }

        val count = values.count { it.trim().isNotEmpty() }
        Button(
            onClick = { syncDraft(); activity.generateFormatName = formatName; activity.generateAll() },
            enabled = count > 0,
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)
        ) { Text("\u751f\u6210 $count \u4e2a\u6761\u7801", fontSize = 16.sp) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmallInputAction(label: String, enabled: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 16.sp, color = if (enabled) Color(0xff667085) else Color(0xffb5bdc9)) }
}
