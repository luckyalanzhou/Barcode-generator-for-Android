package com.luckyalanzhou.barcodegenerator.architecture

import com.lemonappdev.konsist.api.Konsist
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 本地渲染边界约束不是截图测试：防止已知危险组合再次引入，真机观感仍需独立验收。 */
class ButtonRenderingContractTest {
    @Test fun `round result actions keep static optics without press animations`() {
        val button = source("GlassRoundActionButton.kt")
        for (forbidden in listOf("animateFloatAsState", "PressInteraction",
            "pressPosition", "scaleX =", "scaleY =", "translationY =", "radialGradient")) {
            assertFalse("静态圆按钮不可引入 $forbidden", button.contains(forbidden))
        }
        assertFalse(button.contains("rememberGlassBackdropRenderer"))
        assertFalse(button.contains("GlassBackdropSurface("))
        val results = source("ResultsContent.kt")
        assertFalse(results.contains("recordGlassBackdrop"))
        assertFalse(results.contains("rememberGlassBackdrop"))
    }
    @Test fun `press feedback affects only the round action foreground`() {
        val button = source("GlassRoundActionButton.kt")
        val feedback = button.substringAfter("// 反馈只作用于前景图标")
        assertTrue(feedback.contains("alpha = if (pressed && enabled && !busy)"))
        assertTrue(feedback.contains("content(contentTint)"))
        assertFalse(feedback.contains("GlassBackdropSurface("))
        assertFalse(feedback.contains("resultActionShadow("))
        assertFalse(feedback.contains("roundActionGlassFrame("))
    }
    @Test fun `round action appearance stays separate from functionality and GPU samplers`() {
        val button = source("GlassRoundActionButton.kt")
        assertTrue(button.contains("drawResultActionRim("))
        assertTrue(button.indexOf("content(contentTint)") > button.indexOf("drawResultActionRim("))
        assertTrue(button.contains("onClick = onClick"))
        assertFalse(button.contains("RuntimeShader("))
        assertFalse(source("GlassBackdropShader.kt").contains("roundAction"))
    }
    @Test fun `static result rims keep a complete outline separate from reflection`() {
        val rim = source("ResultActionRim.kt")
        assertTrue(rim.contains("palette.outline, radius, style = Stroke(stroke)"))
        assertTrue(rim.contains("0f to palette.top"))
        assertTrue(rim.contains(".5f to palette.side"))
        assertTrue(rim.contains("1f to palette.bottom"))
        assertFalse(rim.contains("Color.Transparent"))
        assertFalse(rim.contains("RuntimeShader"))
    }
    @Test fun `settings distinguish current values from executable actions`() {
        val settings = source("SettingsContent.kt")
        for (open in listOf("schemeMenu", "ocrMenu")) {
            val beforeOpen = settings.substringBefore("onClick = { $open = true }").takeLast(180)
            assertTrue(beforeOpen.contains("contentColor = colors.settingsText.secondary"))
        }
        val update = settings.substringAfter("busy = checkingForUpdates").substringBefore("onClick = onCheckForUpdates")
        assertTrue(update.contains("contentColor = colors.controls.accent"))
        val action = source("ComposeSettingsComponents.kt").substringAfter("internal fun SettingsActionRow(").substringBefore("internal fun SettingsDropdownButton(")
        assertTrue(action.contains("SettingsRow(title, color)"))
        assertTrue(action.contains("SettingsButton(action, buttonColor, LocalAppColorScheme.current.controls.accent"))
    }
    @Test fun `capture and LAN launch emphasize content without tinted containers`() {
        val camera = source("GenerateContent.kt").substringAfter("label = \"拍照填充\"").substringBefore("onClick = onCaptureText")
        assertTrue(camera.contains("containerColor = Color.Transparent"))
        assertTrue(camera.contains("contentColor = themeColors.controls.accent"))
        val launch = source("SettingsContent.kt").substringAfter("text = \"启动\"").substringBefore("onClick = onEnterLanShare")
        assertTrue(launch.contains("color = Color.Transparent"))
        assertTrue(launch.contains("contentColor = colors.controls.accent"))
    }
    private fun source(name: String): String {
        val file = Konsist.scopeFromProduction().files.single { File(it.path).name == name }
        return File(file.path).readText()
    }

    @Test fun `ordinary button chrome cannot own fill shadow or GPU layer`() {
        val chrome = source("ButtonOutlineChrome.kt")
        for (forbidden in listOf(".shadow(", "shadowElevation", "graphicsLayer", "RenderEffect",
            ".background(", "drawRect(", "drawRoundRect(", "elevation:")) {
            assertFalse("外框不能包含 $forbidden", chrome.contains(forbidden))
        }
        assertTrue(chrome.contains("drawOutline"))
        assertTrue(chrome.contains("style = stroke"))
    }

    @Test fun `transparent button labels explicitly clear inherited backgrounds`() {
        for (name in listOf("ComposeGenerateActionButton.kt", "GenerateContent.kt", "ComposeSettingsComponents.kt")) {
            assertTrue("$name 需要清除文字背景", source(name).contains("copy(background = Color.Transparent)"))
        }
    }

    @Test fun `favorite row pressure and visibility do not stack whole row render nodes`() {
        assertFalse(source("FavoritesContent.kt").contains("Modifier.graphicsLayer()"))
        for (name in listOf("ComposeFavoriteFolderRow.kt", "ComposeFavoriteGroupRow.kt")) {
            val row = source(name)
            assertFalse(row.contains("scaleX = scale.value"))
            assertTrue(row.contains("this@drawWithContent.drawContent()"))
        }
    }
}
