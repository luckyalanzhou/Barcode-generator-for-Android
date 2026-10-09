package com.luckyalanzhou.barcodegenerator.architecture

import com.lemonappdev.konsist.api.Konsist
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 菜单绘制边界的本地回归约束；不替代 Popup 跨窗口重放与真机视觉验收。 */
class MenuRenderingContractTest {
    @Test fun `context menu heading uses smaller twelve sp text`() {
        val heading = source("TabLongPressActionDialog.kt")
            .substringAfter("text = state.title,").substringBefore("actions.forEachIndexed")
        assertTrue(heading.contains("fontSize = 12.sp"))
        assertTrue(heading.contains("color = menuColors.title"))
    }

    @Test fun `barcode format menus use whole panel reveal at both entry points`() {
        val generate = source("GenerateContent.kt").substringAfter("AnchoredDropdownMenu(")
        assertTrue(generate.contains("cornerReveal = true"))
        assertTrue(generate.contains("anchorHeight = with(density) { formatButtonHeight.toDp() }"))
        assertTrue(generate.contains("highlightSelection = false"))
        val editor = source("EditorChoiceField.kt")
        assertTrue(editor.contains("cornerReveal = compact"))
        assertTrue(editor.contains("anchorHeight = with(density) { buttonHeight.toDp() }"))
        assertTrue(editor.contains("SingleChoiceMenuItem(option, option == value, highlightSelection = false)"))
    }
    private fun source(name: String): String = File(Konsist.scopeFromProduction().files.single {
        File(it.path).name == name
    }.path).readText()

    @Test fun `both menu hosts use one shared material without independent content fade`() {
        for (name in listOf("CornerDropdownMenu.kt", "TabLongPressActionDialog.kt")) {
            val host = source(name)
            assertTrue(host.contains("MenuSurface("))
            assertFalse(host.contains("contentAlpha"))
        }
        assertTrue(source("CornerDropdownMenu.kt").contains("dropdownPanelTransform("))
    }

    @Test fun `menu samples backdrop before foreground with no displacement or CPU readback`() {
        val surface = source("MenuSurface.kt")
        assertTrue(surface.indexOf("GlassBackdropSurface(") < surface.lastIndexOf("content()"))
        assertTrue(surface.contains("screenCoordinates = true"))
        assertTrue(surface.contains("refractionDp = { 0f }"))
        assertTrue(surface.contains("menuMaterial = true"))
        for (forbidden in listOf("toImageBitmap", "asAndroidBitmap", "onClick", "onDismiss")) {
            assertFalse(surface.contains(forbidden))
        }
    }

    @Test fun `choice state is semantic and transient feedback cannot fill saved selection`() {
        val item = source("MenuChoiceItem.kt")
        assertTrue(item.contains("toggleableState"))
        assertTrue(item.contains("selected = checked"))
        assertTrue(item.contains("if (pressed || hovered)"))
        assertFalse(item.contains("heightIn(min ="))
        assertTrue(item.contains("highlightSelection && checked"))
    }

    @Test fun `value menu rows keep compact height without material minimum height`() {
        val item = source("MenuChoiceItem.kt")
        assertFalse(item.contains("DropdownMenuItem("))
        assertTrue(item.contains("fontSize = 16.sp, lineHeight = 20.sp"))
        assertTrue(item.contains("padding(horizontal = 12.dp, vertical = 8.dp)"))
        assertTrue(item.contains("verticalAlignment = Alignment.CenterVertically"))
        assertTrue(item.contains(".clickable("))
    }

    @Test fun `choice check precedes label and shares its text color`() {
        val item = source("MenuChoiceItem.kt")
        assertTrue(item.indexOf("Box(Modifier.width(iconSize)") < item.indexOf("Text(label,"))
        assertTrue(item.contains("Canvas(Modifier.size(iconSize)"))
        assertTrue(item.contains("checkmarkGlyphScale = .84f"))
        assertTrue(item.contains("drawLine(colors.text.primary"))
        assertTrue(item.contains("Text(label, color = colors.text.primary"))
        assertTrue(item.contains("clearAndSetSemantics"))
    }

    @Test fun `context rows use natural sixteen sp layout and heading follows measured row`() {
        val host = source("TabLongPressActionDialog.kt")
        assertTrue(host.contains("fontSize = 16.sp,"))
        assertTrue(host.contains("lineHeight = 20.sp,"))
        assertTrue(host.contains("vertical = 8.dp"))
        assertFalse(host.contains("heightIn(min = ActionMenuMetrics.rowHeight)"))
        assertTrue(host.contains("measuredRowHeightPx * (3f / 4f)"))
        assertTrue(host.contains("menuOptionIconSize(density)"))
        val iconSizing = source("MenuLineStyle.kt")
        assertTrue(iconSizing.contains("16.sp.toDp() + 4.dp"))
    }
}
