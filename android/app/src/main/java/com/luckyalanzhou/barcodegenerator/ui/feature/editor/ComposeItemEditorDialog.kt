package com.luckyalanzhou.barcodegenerator.ui.feature.editor

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.barcodeFormats
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.ui.app.*
import com.luckyalanzhou.barcodegenerator.ui.dialogs.*
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun MainActivity.showItemEditorCompose(
    item: CodeItem,
    onDelete: (Long) -> Unit,
    onUpdate: (Long, String, String) -> Unit,
) {
    showComposeDialog(compact = false) { dismiss ->
        val dark = isDark()
        var value by remember { mutableStateOf(item.text) }
        var selectedIndex by remember { mutableIntStateOf(barcodeFormats.indexOfFirst { it.first == item.format }.coerceAtLeast(0)) }
        ComposeGlassDialogCard(dark) {
            Text("编辑条目", color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
            OutlinedTextField(
                value,
                { value = it },
                Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LocalAppColorScheme.current.text.primary,
                    unfocusedTextColor = LocalAppColorScheme.current.text.primary,
                    focusedLabelColor = LocalAppColorScheme.current.text.primary,
                    unfocusedLabelColor = LocalAppColorScheme.current.text.secondary,
                    focusedPlaceholderColor = LocalAppColorScheme.current.text.placeholder,
                    unfocusedPlaceholderColor = LocalAppColorScheme.current.text.placeholder,
                    cursorColor = LocalAppColorScheme.current.text.primary,
                ),
                label = { Text("条码内容", color = LocalAppColorScheme.current.text.secondary) },
            )
            ComposeChoiceField(barcodeFormats[selectedIndex].first, barcodeFormats.map { it.first }, dark) { choice ->
                selectedIndex = barcodeFormats.indexOfFirst { it.first == choice }.coerceAtLeast(0)
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("删除", dark, {
                    dismiss()
                    showComposeConfirmDialog("删除条目", "确定删除此条码吗？", "删除") { onDelete(item.id) }
                }, modifier = Modifier.padding(start = 20.dp))
                DialogAction("保存", dark, {
                    val text = value
                    if (text.isBlank()) toast("请输入条码内容")
                    else {
                        onUpdate(item.id, text, barcodeFormats[selectedIndex].first)
                        dismiss()
                    }
                }, modifier = Modifier.padding(start = 20.dp))
            }
        }
    }
}
