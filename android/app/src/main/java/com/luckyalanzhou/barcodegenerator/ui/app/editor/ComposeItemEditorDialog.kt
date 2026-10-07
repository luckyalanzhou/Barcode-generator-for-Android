package com.luckyalanzhou.barcodegenerator.ui.app.editor

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.barcodeFormats
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.BarcodeValidator
import com.luckyalanzhou.barcodegenerator.ui.app.*
import com.luckyalanzhou.barcodegenerator.ui.dialogs.*
import com.luckyalanzhou.barcodegenerator.ui.feature.editor.ComposeChoiceField
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun MainActivity.showItemEditorCompose(
    item: CodeItem,
    onUpdate: (Long, String, String) -> Unit,
) {
    val itemId = item.id
    val initialText = item.text
    val initialFormat = item.format
    showComposeDialog(compact = false) { dismiss ->
        val dark = isDark()
        val availableFormats = barcodeFormats
        val fallbackFormat = availableFormats.firstOrNull()?.first.orEmpty()
        var value by remember { mutableStateOf(initialText) }
        var selectedFormat by remember {
            mutableStateOf(availableFormats.firstOrNull { it.first == initialFormat }?.first ?: fallbackFormat)
        }
        ComposeGlassDialogCard(dark) {
            Text("编辑条目", color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
            OutlinedTextField(
                value,
                { value = it },
                Modifier.fillMaxWidth().padding(top = 12.dp)
                    .semantics { contentDescription = "条码内容" },
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
            )
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                ComposeChoiceField(selectedFormat, availableFormats.map { it.first }, dark,
                    modifier = Modifier.widthIn(max = 200.dp), compact = true) { choice ->
                    selectedFormat = choice
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("保存", dark, {
                    val text = value
                    val format = selectedFormat
                    val validation = BarcodeValidator.validate(text, format)
                    if (text.isBlank()) toast("请输入条码内容")
                    else if (!validation.valid) toast(validation.message)
                    else {
                        onUpdate(itemId, text, format)
                        dismiss()
                    }
                })
            }
        }
    }
}
