package com.luckyalanzhou.barcodegenerator

import androidx.compose.ui.graphics.vector.ImageVector
import com.luckyalanzhou.barcodegenerator.icons.*

/** Compatibility facade; actual action icon Paths live in individual icon files. */
internal object MaterialActionIcons {
    val add: ImageVector get() = AddIcon
    val search: ImageVector get() = SearchIcon
    val photoCamera: ImageVector get() = PhotoCameraIcon
    val folder: ImageVector get() = FolderIcon
    val attachFile: ImageVector get() = AttachFileIcon
    val checkBoxOutlineBlank: ImageVector get() = CheckBoxOutlineBlankIcon
    val checkBox: ImageVector get() = CheckBoxIcon
    val iosShare: ImageVector get() = IosShareIcon
    val contentCopy: ImageVector get() = ContentCopyIcon
    val qrCode2: ImageVector get() = QrCode2Icon
    val createNewFolder: ImageVector get() = CreateNewFolderIcon
    val keyboardArrowRight: ImageVector get() = KeyboardArrowRightIcon
    val keyboardArrowDown: ImageVector get() = KeyboardArrowDownIcon
    val driveFileMove: ImageVector get() = DriveFileMoveIcon
    val favoriteFilled: ImageVector get() = FavoriteFilledIcon
    val edit: ImageVector get() = EditIcon
}
