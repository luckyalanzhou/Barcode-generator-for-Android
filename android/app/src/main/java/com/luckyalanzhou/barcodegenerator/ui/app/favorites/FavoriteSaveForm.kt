package com.luckyalanzhou.barcodegenerator.ui.app.favorites

import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.luckyalanzhou.barcodegenerator.R
import com.luckyalanzhou.barcodegenerator.icons.CreateNewFolderIcon
import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState
import com.luckyalanzhou.barcodegenerator.presentation.ResultUiState
import com.luckyalanzhou.barcodegenerator.ui.component.iosPressFeedback
import com.luckyalanzhou.barcodegenerator.ui.dialogs.ComposeGlassDialogCard
import com.luckyalanzhou.barcodegenerator.ui.dialogs.DialogAction
import com.luckyalanzhou.barcodegenerator.ui.feature.editor.ComposeChoiceField
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.favoriteFolderDestination
import com.luckyalanzhou.barcodegenerator.ui.app.AppRoute
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val localizedResources = LocalResources.current
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
    var rootOverflow by remember(selectedRoot) { mutableStateOf(false) }
    var childOverflow by remember(selectedChild) { mutableStateOf(false) }
    val chooseFolder = stringResource(R.string.favorite_folder_choose_value)
    val noSubfolder = stringResource(R.string.favorite_subfolder_none_value)

    ComposeGlassDialogCard(dark) {
        Text(
            if (editingGroup == null) stringResource(R.string.favorite_save) else stringResource(R.string.favorite_edit),
            color = LocalAppColorScheme.current.text.primary,
            fontSize = 18.sp,
        )
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.favorite_save_location),
                color = LocalAppColorScheme.current.text.secondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ComposeChoiceField(
                        value = selectedRoot,
                        options = roots,
                        dark = dark,
                        modifier = Modifier.weight(1f),
                        enabled = roots.isNotEmpty(),
                        scrollableFolderMenu = true,
                        fieldLabel = stringResource(R.string.favorite_root_folder),
                        optionLabel = { it.ifBlank { chooseFolder } },
                        onValueOverflowChanged = { rootOverflow = it },
                        onSelected = { selectedRoot = it; selectedChild = "" },
                    )
                    CreateFolderButton(stringResource(R.string.favorite_create_root_folder), enabled = !saving) {
                        onOpenRootFolderEditor { folder ->
                            if (folder !in folders) folders = folders + folder
                            selectedRoot = folder
                            selectedChild = ""
                        }
                    }
                }
                if (roots.isEmpty()) {
                    Text(
                        stringResource(R.string.favorite_no_root_folders),
                        color = LocalAppColorScheme.current.text.secondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ComposeChoiceField(
                        value = selectedChild,
                        options = listOf("") + childOptions,
                        dark = dark,
                        modifier = Modifier.weight(1f),
                        enabled = selectedRoot.isNotBlank() && childOptions.isNotEmpty(),
                        scrollableFolderMenu = true,
                        fieldLabel = stringResource(R.string.favorite_nested_folder),
                        optionLabel = { it.ifBlank { noSubfolder } },
                        onValueOverflowChanged = { childOverflow = it },
                        onSelected = { selectedChild = it },
                    )
                    CreateFolderButton(
                        stringResource(R.string.favorite_create_nested_folder),
                        enabled = selectedRoot.isNotBlank() && !saving,
                    ) {
                        val root = selectedRoot
                        onOpenChildFolderEditor(root) { child ->
                            val path = "$root/$child"
                            if (path !in folders) folders = folders + path
                            if (selectedRoot == root) selectedChild = child
                        }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LocalAppColorScheme.current.text.primary,
                    unfocusedTextColor = LocalAppColorScheme.current.text.primary,
                    focusedLabelColor = LocalAppColorScheme.current.text.primary,
                    unfocusedLabelColor = LocalAppColorScheme.current.text.secondary,
                    cursorColor = LocalAppColorScheme.current.text.primary,
                ),
                label = { Text(stringResource(R.string.favorite_filename)) },
            )
            if (selectedRoot.isNotBlank() && (rootOverflow || (selectedChild.isNotBlank() && childOverflow))) {
                val destination = listOf(selectedRoot, selectedChild, name.trim())
                    .filter(String::isNotEmpty).joinToString(" / ")
                Text(
                    stringResource(R.string.favorite_save_destination, destination),
                    color = LocalAppColorScheme.current.text.secondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
            DialogAction(stringResource(R.string.action_cancel), dark, { if (!saving) onCancel() })
            DialogAction(
                if (saving) stringResource(R.string.saving) else stringResource(R.string.action_save),
                dark,
                {
                    val cleanName = name.trim()
                    when {
                        cleanName.isEmpty() -> onValidationMessage(localizedResources.getString(R.string.favorite_filename_required))
                        selectedFolder.isBlank() -> onValidationMessage(localizedResources.getString(R.string.favorite_choose_folder))
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
                                    localizedResources.getString(R.string.favorite_overwrite),
                                    localizedResources.getString(R.string.favorite_overwrite_confirm, "$selectedFolder/$cleanName"),
                                    save,
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.padding(start = 12.dp),
                primary = true,
                enabled = !saving && selectedFolder.isNotBlank() && name.isNotBlank(),
            )
        }
    }
}


@Composable
private fun CreateFolderButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColorScheme.current
    val interactions = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactions,
        modifier = Modifier.size(48.dp).iosPressFeedback(interactions),
    ) {
        Icon(
            CreateNewFolderIcon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = if (enabled) colors.text.link else colors.text.disabled,
        )
    }
}
