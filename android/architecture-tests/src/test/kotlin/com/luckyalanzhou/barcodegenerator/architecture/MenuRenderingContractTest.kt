package com.luckyalanzhou.barcodegenerator.architecture

import com.lemonappdev.konsist.api.Konsist
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 菜单绘制边界的本地回归约束；不替代 Popup 跨窗口重放与真机视觉验收。 */
class MenuRenderingContractTest {
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
        assertTrue(item.contains("heightIn(min = 40.dp)"))
        assertFalse(item.contains("selectedContainer"))
    }

    @Test fun `value menu rows keep compact height without material minimum height`() {
        val item = source("MenuChoiceItem.kt")
        assertFalse(item.contains("DropdownMenuItem("))
        assertTrue(item.contains("fontSize = 14.sp, lineHeight = 20.sp"))
        assertTrue(item.contains("padding(horizontal = 12.dp, vertical = 8.dp)"))
        assertTrue(item.contains("verticalAlignment = Alignment.CenterVertically"))
        assertTrue(item.contains(".clickable("))
    }
}
