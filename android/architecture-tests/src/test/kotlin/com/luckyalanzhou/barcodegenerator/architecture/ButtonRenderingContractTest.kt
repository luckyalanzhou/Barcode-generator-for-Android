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
        val frame = source("RoundActionGlassFrame.kt")
        assertTrue(frame.contains("motion = 0f"))
        assertTrue(frame.contains("travelStrength = 0f"))
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
    @Test fun `round glass optics are isolated from callbacks tabs and menu material`() {
        val button = source("GlassRoundActionButton.kt")
        assertTrue(button.contains("rememberGlassBackdropRenderer(roundAction = true)"))
        assertTrue(button.indexOf("content(contentTint)") > button.indexOf("GlassBackdropSurface("))
        assertTrue(button.contains("onClick = onClick"))
        val shader = source("RoundActionGlassShader.kt")
        for (forbidden in listOf("onClick", "ViewModel", "Bitmap", "capsuleOptics", "menuMaterial")) {
            assertFalse("圆按钮材质不能包含 $forbidden", shader.contains(forbidden))
        }
        val renderer = source("GlassBackdrop.kt")
        assertTrue(renderer.contains("blur > 0f && !roundAction"))
        assertTrue(renderer.contains("if (roundAction) null else capsule?.let(::tabDynamicOptics)"))
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
