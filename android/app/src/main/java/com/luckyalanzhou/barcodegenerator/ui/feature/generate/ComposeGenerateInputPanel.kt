package com.luckyalanzhou.barcodegenerator.ui.feature.generate

import androidx.compose.foundation.interaction.MutableInteractionSource
import com.luckyalanzhou.barcodegenerator.ui.component.unframedActionPressFeedback
import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.icons.ArrowDownwardIcon
import com.luckyalanzhou.barcodegenerator.icons.ArrowUpwardIcon
import com.luckyalanzhou.barcodegenerator.icons.DeleteIcon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.clickable
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.semantics.error
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.layout

private val GenerateInputRowHeight = 48.dp
private val GenerateInputRowSpacing = 6.dp
private val GenerateInputPanelPadding = 2.dp

/**
 * 渲染可编辑的多行条码输入区。每行支持输入、调整顺序和删除；仅选中行右侧显示直接操作图标。
 * 业务数据由父级回调维护，本组件只呈现输入状态和行操作。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ComposeGenerateInputPanel(
    values: List<String>,
    dark: Boolean,
    focusedIndex: Int,
    onValueChange: (Int, String) -> Unit,
    onFocus: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    rowKeys: List<Long> = values.indices.map { it.toLong() },
    focusRequest: Int = 0,
    focusRequestRowKey: Long? = null,
    validationError: GenerateInputError? = null,
    maxPanelHeight: Dp = 264.dp,
    fitWholeRows: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val themeColors = LocalAppColorScheme.current
    val textColor = themeColors.text.primary
    val secondary = themeColors.text.secondary
    val inputColor = themeColors.surfaces.input
    val inputBorder = themeColors.borders.input
    val focusedInputBorder = themeColors.borders.focusedInput
    val scrollState = rememberScrollState()
    val background = themeColors.surfaces.background
    Column(
        modifier = modifier
            .testTag("generate-inputs")
            .heightIn(min = 52.dp, max = maxPanelHeight.coerceAtLeast(52.dp))
            .layout { measurable, constraints ->
                // Use the rounded pixel sizes that Column uses to measure each row and gap.
                val rowHeight = GenerateInputRowHeight.roundToPx()
                val spacing = GenerateInputRowSpacing.roundToPx()
                val padding = GenerateInputPanelPadding.roundToPx() * 2
                val rows = (constraints.maxHeight - padding + spacing) / (rowHeight + spacing)
                val height = if (fitWholeRows && rows > 0) {
                    (rows * (rowHeight + spacing) - spacing + padding).coerceAtLeast(constraints.minHeight)
                } else {
                    constraints.maxHeight
                }
                val panel = measurable.measure(constraints.copy(maxHeight = height))
                layout(panel.width, panel.height) { panel.placeRelative(0, 0) }
            }
            .fillMaxWidth()
            .drawWithCache {
                val edge = 10.dp.toPx().coerceAtMost(size.height / 2f)
                val transparent = background.copy(alpha = 0f)
                val top = Brush.verticalGradient(listOf(background, transparent), endY = edge)
                val bottom = Brush.verticalGradient(listOf(transparent, background),
                    startY = size.height - edge, endY = size.height)
                onDrawWithContent {
                    drawContent()
                    if (edge > 0f && scrollState.canScrollBackward) {
                        drawRect(top, size = Size(size.width, edge))
                    }
                    if (edge > 0f && scrollState.canScrollForward) {
                        drawRect(bottom, topLeft = Offset(0f, size.height - edge), size = Size(size.width, edge))
                    }
                }
            }
            .verticalScroll(scrollState)
            // 输入框及排序/删除按钮占满一整行，与下方“添加一行/拍照填充”操作区保持对齐。
            .padding(vertical = GenerateInputPanelPadding),
        verticalArrangement = Arrangement.spacedBy(GenerateInputRowSpacing),
    ) {
        values.forEachIndexed { index, value ->
            key(rowKeys.getOrElse(index) { index.toLong() }) {
                val focusRequester = remember { FocusRequester() }
                val bringIntoView = remember { BringIntoViewRequester() }
                LaunchedEffect(focusRequest, focusRequestRowKey) {
                    if (focusedIndex == index && focusRequest > 0 && focusRequestRowKey == rowKeys.getOrElse(index) { index.toLong() }) focusRequester.requestFocus()
                }
                LaunchedEffect(focusedIndex, focusRequest) {
                    if (focusedIndex == index) {
                        withFrameNanos { }
                        bringIntoView.bringIntoView()
                    }
                }
                Column(Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(GenerateInputRowHeight).testTag("generate-row-$index"),
                        shape = RoundedCornerShape(14.dp),
                        color = inputColor,
                        contentColor = textColor,
                        border = BorderStroke(1.dp, if (validationError?.rowIndex == index) themeColors.text.destructive else if (focusedIndex == index) focusedInputBorder else inputBorder),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(GenerateInputRowHeight),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                // 三位序号需随文字宽度扩展，避免大字体时折行并挤压输入内容。
                                modifier = Modifier.widthIn(min = 40.dp).clickable(onClickLabel = "选中第 ${index + 1} 行") { focusRequester.requestFocus(); onFocus(index) },
                                color = textColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            BasicTextField(
                                value = value,
                                onValueChange = { onValueChange(index, it) },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = textColor,
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp,
                                    background = Color.Transparent,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.Both,
                                    ),
                                ),
                                cursorBrush = SolidColor(textColor),
                                modifier = Modifier.weight(1f).height(48.dp)
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { if (it.isFocused) onFocus(index) }
                                    .semantics {
                                        contentDescription = "第 ${index + 1} 行条码内容"
                                        validationError?.takeIf { it.rowIndex == index }?.let { error(it.message) }
                                    }
                                    .padding(horizontal = 6.dp),
                                decorationBox = { field ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        if (value.isEmpty()) {
                                            Text(
                                                text = "输入一行条码内容",
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                style = LocalTextStyle.current.copy(
                                                    color = themeColors.text.secondary,
                                                    fontSize = 14.sp,
                                                    lineHeight = 20.sp,
                                                    background = Color.Transparent,
                                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                    lineHeightStyle = LineHeightStyle(
                                                        alignment = LineHeightStyle.Alignment.Center,
                                                        trim = LineHeightStyle.Trim.Both,
                                                    ),
                                                ),
                                            )
                                        }
                                        field()
                                    }
                                },
                            )
                    if (focusedIndex == index) {
                        GenerateInputAction(ArrowUpwardIcon, "上移", index > 0) { onMoveUp(index) }
                        GenerateInputAction(ArrowDownwardIcon, "下移", index < values.lastIndex) { onMoveDown(index) }
                        GenerateInputAction(DeleteIcon, "删除", values.size > 1 || value.isNotEmpty()) { onDelete(index) }
                    }
                        }
                    }
                    if (validationError?.rowIndex == index) {
                        Text("第 ${index + 1} 行：${validationError.message}", color = themeColors.text.destructive,
                            fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                    }

                }
            }
        }
    }
}

@Composable
private fun GenerateInputAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAppColorScheme.current
    val tint = if (description == "删除") colors.text.destructive else colors.text.primary
    val interactions = remember { MutableInteractionSource() }
    androidx.compose.material3.IconButton(onClick = onClick, enabled = enabled, interactionSource = interactions,
        modifier = Modifier.size(48.dp).unframedActionPressFeedback(interactions),
    ) {
        Icon(icon, description, tint = if (enabled) tint else colors.text.disabled,
            modifier = Modifier.size(if (description == "删除") 20.dp else 22.dp))
    }
}
