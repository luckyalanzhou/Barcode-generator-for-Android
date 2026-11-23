package com.luckyalanzhou.barcodegenerator.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luckyalanzhou.barcodegenerator.R
import com.luckyalanzhou.barcodegenerator.icons.CloseSmallIcon
import com.luckyalanzhou.barcodegenerator.icons.SearchIcon
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.MiSansFontFamily

@Composable
internal fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
) {
    val themeColors = LocalAppColorScheme.current
    val primary = themeColors.text.primary
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .border(1.dp, themeColors.borders.input, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = SearchIcon,
                    contentDescription = null,
                    tint = themeColors.text.secondary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f).heightIn(min = 28.dp)
                        .padding(vertical = 8.dp).semantics { contentDescription = accessibilityLabel },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }),
                    textStyle = TextStyle(
                        color = primary,
                        fontFamily = MiSansFontFamily,
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                    ),
                    cursorBrush = SolidColor(primary),
                    decorationBox = { field ->
                        Box(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    placeholder,
                                    style = TextStyle(
                                        color = themeColors.text.secondary,
                                        fontFamily = MiSansFontFamily,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.Both,
                                        ),
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                )
                            }
                            field()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    val clearSearchInteractions = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = { onQueryChange("") },
                        interactionSource = clearSearchInteractions,
                        modifier = Modifier.size(48.dp).iosPressFeedback(clearSearchInteractions),
                    ) {
                        Box(
                            Modifier.size(24.dp).background(
                                themeColors.text.secondary.copy(alpha = .20f),
                                CircleShape,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = CloseSmallIcon,
                                contentDescription = stringResource(R.string.clear_search),
                                tint = themeColors.text.primary,
                                modifier = Modifier.requiredSize(28.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
