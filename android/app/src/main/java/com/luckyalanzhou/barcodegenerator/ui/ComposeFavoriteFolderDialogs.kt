package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 文件夹编辑 Compose 弹窗，校验规则与原编辑器一致。 */
internal fun MainActivity.showFolderEditorCompose(initial: String = "", showMetrics: Boolean = false, onSaved: (String) -> Unit) {
    showComposeDialog(compact = false, metricsLabel = if (showMetrics) "文件夹编辑弹窗" else null) { dismiss ->
        val dark = isDark()
        var value by remember { mutableStateOf(initial) }
        ComposeGlassDialogCard(dark) {
            Text(
                if (initial.isBlank()) "新建文件夹" else "重命名文件夹",
                color = LocalBarcodeThemeColors.current.primary,
                fontSize = 18.sp,
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                label = { Text("文件夹名称") },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("保存", dark, {
                    val name = value.trim()
                    val parent = initial.substringBeforeLast('/', "")
                    val targetPath = listOf(parent, name).filter { it.isNotBlank() }.joinToString("/")
                    when {
                        name.isBlank() -> toast("请输入文件夹名称")
                        !isValidFavoriteFolderPath(targetPath) -> toast("文件夹最多支持一级和二级，且名称不能包含斜杠")
                        viewModel.dataState.value.folders.any { it == targetPath && it != initial } -> toast("已存在同名文件夹")
                        else -> { onSaved(name); dismiss() }
                    }
                }, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** 新建二级文件夹 Compose 弹窗，保持原有斜杠和重名校验。 */
internal fun MainActivity.showSubfolderEditorCompose(parent: String, onCreated: ((String) -> Unit)? = null) {
    showComposeDialog(compact = false, metricsLabel = null) { dismiss ->
        val dark = isDark()
        var value by remember { mutableStateOf("") }
        ComposeGlassDialogCard(dark) {
            Text("新建文件夹", color = LocalBarcodeThemeColors.current.primary, fontSize = 18.sp)
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                label = { Text("文件夹名称") },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("保存", dark, {
                    val child = value.trim()
                    val path = "$parent/$child"
                    when {
                        child.isBlank() -> toast("请输入文件夹名称")
                        child.contains('/') -> toast("名称不能包含斜杠")
                        path in viewModel.dataState.value.folders -> toast("已存在同名文件夹")
                        else -> {
                            viewModel.createFavoriteFolder(path)
                            onCreated?.invoke(child)
                            dismiss()
                        }
                    }
                }, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
