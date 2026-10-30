package com.luckyalanzhou.barcodegenerator.ui.app.favorites

import com.luckyalanzhou.barcodegenerator.icons.CreateNewFolderIcon
import com.luckyalanzhou.barcodegenerator.icons.FolderIcon
import com.luckyalanzhou.barcodegenerator.icons.KeyboardArrowDownIcon
import com.luckyalanzhou.barcodegenerator.icons.KeyboardArrowRightIcon
import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState
import com.luckyalanzhou.barcodegenerator.presentation.ResultUiState
import com.luckyalanzhou.barcodegenerator.ui.component.iosPressFeedback
import com.luckyalanzhou.barcodegenerator.ui.component.glass.resultActionEdgeChrome
import com.luckyalanzhou.barcodegenerator.ui.dialogs.ComposeGlassDialogCard
import com.luckyalanzhou.barcodegenerator.ui.dialogs.DialogAction
import com.luckyalanzhou.barcodegenerator.ui.feature.editor.ComposeChoiceField
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.favoriteFolderDestination
import com.luckyalanzhou.barcodegenerator.ui.app.AppRoute
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 收藏保存表单提交给 Activity 的完整请求。 */
internal data class FavoriteSaveRequest(
    val itemIds: List<Long>,
    val editingGroupId: Long?,
    val targetGroupId: Long?,
    val folder: String,
    val name: String,
)

/**
 * 收藏保存表单负责目录选择、名称输入与覆盖确认；持久化和 Activity 导航由调用方管理。
 * 通过显式回调接入系统目录编辑器，便于 Compose 交互测试覆盖完整表单提交。
 */
@Composable
internal fun FavoriteSaveForm(
    resultState: ResultUiState,
    dataState: BarcodeDataState,
    dark: Boolean,
    saving: Boolean,
    findExistingGroupId: (String, String, Set<Long>) -> Long?,
    onSaveRequest: (FavoriteSaveRequest) -> Unit,
    onOpenRootFolderEditor: (onCreated: (String) -> Unit) -> Unit,
    onOpenChildFolderEditor: (root: String, onCreated: (String) -> Unit) -> Unit,
    onShowOverwriteConfirmation: (title: String, message: String, onConfirm: () -> Unit) -> Unit,
    onValidationMessage: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val editingGroup = resultState.selectedFavoriteGroup
        .takeIf { resultState.returnPage == AppRoute.Favorites }
    var folders by remember {
        mutableStateOf(
            (dataState.folders + dataState.groups.map { it.folder })
                .filter { it.isNotBlank() }
                .distinct(),
        )
    }
    val roots = folders.map { it.substringBefore('/') }.distinct().sorted()
    var selectedRoot by remember {
        mutableStateOf(editingGroup?.folder?.substringBefore('/')?.takeIf { it in roots }.orEmpty())
    }
    var selectedChild by remember {
        mutableStateOf(
            editingGroup?.folder.orEmpty().substringAfter('/', "")
                .takeIf { it.isNotBlank() }.orEmpty(),
        )
    }
    val childOptions = folders
        .filter { it.startsWith("$selectedRoot/") }
        .map { it.removePrefix("$selectedRoot/") }
        .filter { !it.contains('/') }
        .distinct()
        .sorted()
    val selectedFolder = favoriteFolderDestination(selectedRoot, selectedChild)
    var name by remember { mutableStateOf(editingGroup?.name.orEmpty()) }

    ComposeGlassDialogCard(dark) {
        Text(
            if (editingGroup == null) "保存到收藏" else "编辑收藏",
            color = LocalAppColorScheme.current.text.primary,
            fontSize = 18.sp,
        )
        Text(
            "选择收藏保存位置",
            color = LocalAppColorScheme.current.text.secondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (roots.isEmpty()) {
            Text(
                "暂无一级文件夹，请先新建",
                color = LocalAppColorScheme.current.text.secondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(
                        FolderIcon,
                        "一级文件夹",
                        tint = LocalAppColorScheme.current.content.folder,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        "① 一级文件夹",
                        color = LocalAppColorScheme.current.text.primary,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                ComposeChoiceField(
                    value = selectedRoot.ifBlank { "选择一级文件夹" },
                    options = roots,
                    dark = dark,
                    modifier = Modifier.padding(start = 32.dp),
                    onSelected = { selectedRoot = it; selectedChild = "" },
                )
                Row(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(20.dp).height(28.dp)) {
                        Box(
                            Modifier.padding(start = 7.dp).width(2.dp).height(28.dp)
                                .background(LocalAppColorScheme.current.controls.button),
                        )
                    }
                    Icon(
                        FolderIcon,
                        "二级文件夹",
                        tint = LocalAppColorScheme.current.content.childFolder,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        "② 二级文件夹",
                        color = LocalAppColorScheme.current.text.primary,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                ComposeChoiceField(
                    value = selectedChild.ifBlank { "选择二级文件夹" },
                    options = childOptions,
                    dark = dark,
                    modifier = Modifier.padding(start = 52.dp),
                    enabled = selectedRoot.isNotBlank() && childOptions.isNotEmpty(),
                    onSelected = { selectedChild = it },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    .background(LocalAppColorScheme.current.surfaces.input, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("当前位置", color = LocalAppColorScheme.current.text.secondary, fontSize = 12.sp)
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        if (selectedRoot.isBlank()) {
                            Text("未选择一级文件夹", color = LocalAppColorScheme.current.text.secondary, fontSize = 14.sp)
                        } else {
                            Icon(FolderIcon, "当前一级文件夹", tint = LocalAppColorScheme.current.controls.accent, modifier = Modifier.size(18.dp))
                            Text(selectedRoot, color = LocalAppColorScheme.current.text.primary, modifier = Modifier.padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                            if (selectedChild.isNotBlank()) {
                                Icon(KeyboardArrowRightIcon, "层级", tint = LocalAppColorScheme.current.text.secondary, modifier = Modifier.size(20.dp).padding(horizontal = 2.dp))
                                Icon(FolderIcon, "当前二级文件夹", tint = LocalAppColorScheme.current.controls.accent, modifier = Modifier.size(18.dp))
                                Text(selectedChild, color = LocalAppColorScheme.current.text.primary, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Icon(KeyboardArrowDownIcon, "展开层级", tint = LocalAppColorScheme.current.text.secondary, modifier = Modifier.size(20.dp))
            }
            if (selectedRoot.isNotBlank() && childOptions.isEmpty()) {
                Text(
                    "该一级文件夹暂无二级文件夹，请先新建",
                    color = LocalAppColorScheme.current.text.secondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        val rootInteractions = remember { MutableInteractionSource() }
        val childInteractions = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    onOpenRootFolderEditor { folder ->
                        if (folder !in folders) folders = (folders + folder).distinct()
                        selectedRoot = folder
                        selectedChild = ""
                    }
                },
                interactionSource = rootInteractions,
                modifier = Modifier.weight(1f).height(40.dp).iosPressFeedback(rootInteractions).resultActionEdgeChrome(RoundedCornerShape(8.dp)),
                contentPadding = PaddingValues(horizontal = 6.dp),
                shape = RoundedCornerShape(8.dp),
                border = null,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalAppColorScheme.current.text.primary),
            ) {
                Icon(CreateNewFolderIcon, "新建一级文件夹", tint = LocalAppColorScheme.current.text.primary, modifier = Modifier.size(20.dp))
                Text("新建一级文件夹", color = LocalAppColorScheme.current.text.primary, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp), maxLines = 1)
            }
            OutlinedButton(
                onClick = {
                    if (selectedRoot.isBlank()) onValidationMessage("请先选择一级文件夹")
                    else onOpenChildFolderEditor(selectedRoot) { child ->
                        val path = "$selectedRoot/$child"
                        if (path !in folders) folders = (folders + path).distinct()
                        selectedChild = child
                    }
                },
                interactionSource = childInteractions,
                modifier = Modifier.weight(1f).height(40.dp).iosPressFeedback(childInteractions).resultActionEdgeChrome(RoundedCornerShape(8.dp)),
                contentPadding = PaddingValues(horizontal = 6.dp),
                shape = RoundedCornerShape(8.dp),
                border = null,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalAppColorScheme.current.text.primary),
            ) {
                Icon(CreateNewFolderIcon, "新建二级文件夹", tint = LocalAppColorScheme.current.text.primary, modifier = Modifier.size(20.dp))
                Text("新建二级文件夹", color = LocalAppColorScheme.current.text.primary, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp), maxLines = 1)
            }
        }

        Text("收藏文件名", color = LocalAppColorScheme.current.text.primary, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = LocalAppColorScheme.current.text.primary,
                unfocusedTextColor = LocalAppColorScheme.current.text.primary,
                focusedLabelColor = LocalAppColorScheme.current.text.primary,
                unfocusedLabelColor = LocalAppColorScheme.current.text.primary,
                cursorColor = LocalAppColorScheme.current.text.primary,
            ),
            label = { Text("收藏文件名", color = LocalAppColorScheme.current.text.primary) },
        )
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
            DialogAction("取消", dark, { if (!saving) onCancel() })
            DialogAction(
                if (saving) "保存中…" else "保存",
                dark,
                {
                    val cleanName = name.trim()
                    when {
                        cleanName.isEmpty() -> onValidationMessage("请输入收藏文件名")
                        selectedFolder.isBlank() -> onValidationMessage("请选择文件夹")
                        else -> {
                            val conflictGroupId = findExistingGroupId(
                                selectedFolder,
                                cleanName,
                                setOfNotNull(editingGroup?.id),
                            )
                            val request = FavoriteSaveRequest(
                                itemIds = resultState.items.map { it.id },
                                editingGroupId = editingGroup?.id,
                                targetGroupId = conflictGroupId ?: editingGroup?.id,
                                folder = selectedFolder,
                                name = cleanName,
                            )
                            val save = { onSaveRequest(request) }
                            if (conflictGroupId == null) save()
                            else {
                                onShowOverwriteConfirmation(
                                    "覆盖收藏",
                                    "“" + selectedFolder + "/" + cleanName + "”已存在，是否覆盖？",
                                    save,
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.padding(start = 20.dp),
            )
        }
    }
}
