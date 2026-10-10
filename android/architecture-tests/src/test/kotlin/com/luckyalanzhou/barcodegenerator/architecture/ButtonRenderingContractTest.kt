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
            ".background(", "drawRect(", "drawRoundRect(", "elevation:", "Brush.", "Color.Transparent")) {
            assertFalse("外框不能包含 $forbidden", chrome.contains(forbidden))
        }
        assertTrue(chrome.contains("drawOutline"))
        assertTrue(chrome.contains("style = stroke"))
        assertTrue(chrome.contains("translate(inset, inset)"))
    }

    @Test fun `ordinary action entry points share the same static outline`() {
        for (name in listOf("ComposeGenerateActionButton.kt", "ComposeSettingsComponents.kt",
            "ComposeCommonDialogs.kt", "EditorChoiceField.kt", "ComposeFavoriteSaveDialog.kt",
            "FavoritesContent.kt", "LanShareInputBar.kt",
            "LanShareContent.kt", "LanShareMessageBubble.kt", "LanShareUploadingBubble.kt",
            "ComposeLanShareQrDialog.kt", "ComposeSettingsSlider.kt",
            "LanShareImagePreviewDialog.kt")) {
            assertTrue("$name 必须使用统一静态按钮框", source(name).contains(".globalButtonChrome("))
        }
    }

    @Test fun `sorting and row deletion remain unframed with explicit press feedback`() {
        for (name in listOf("ComposeGenerateInputPanel.kt", "ComposeHistoryUi.kt")) {
            val button = source(name)
            assertFalse("$name 不添加按钮框", button.contains("globalButtonChrome"))
            assertTrue(button.contains(".unframedActionPressFeedback("))
            assertTrue(button.contains("interactionSource ="))
        }
    }

    @Test fun `value picker triggers are unframed and settings trailing text matches titles`() {
        val settings = source("ComposeSettingsComponents.kt")
        val dropdown = settings.substringAfter("internal fun SettingsDropdownButton(").substringBefore("internal fun SettingsButton(")
        assertTrue(dropdown.contains("SettingsButton(text, Color.Transparent"))
        assertTrue(dropdown.contains("showOutline = false"))
        assertTrue(settings.contains("if (showOutline) Modifier.globalButtonChrome"))
        assertTrue(settings.contains("Text(text, color = contentColor, fontSize = 16.sp"))
        assertTrue(source("SettingsContent.kt").contains("Text(effectsSettings.summary, color = colors.settingsText.secondary, fontSize = 16.sp)"))
        assertFalse(source("ComposeSettingsSlider.kt").contains("fontSize = 15.sp"))
        val format = source("GenerateContent.kt").substringAfter("onClick = { formatExpanded = true }").substringBefore("AnchoredDropdownMenu(")
        assertFalse(format.contains("globalButtonChrome"))
        assertTrue(format.contains("containerColor = Color.Transparent"))
        assertTrue(format.contains("Text(formatName, color = themeColors.text.placeholder"))
        val editor = source("EditorChoiceField.kt")
        assertTrue(editor.contains("if (compact) Modifier else Modifier.globalButtonChrome"))
        assertTrue(editor.contains("if (compact) LocalAppColorScheme.current.text.secondary"))
    }

    @Test fun `unframed press feedback never owns actions or draws a background`() {
        val feedback = source("UnframedActionPressFeedback.kt")
        for (forbidden in listOf("onClick", "onDelete", "onMove", "drawRect", ".background(", "globalButtonChrome")) {
            assertFalse(feedback.contains(forbidden))
        }
        assertTrue(feedback.contains("is PressInteraction.Cancel -> pressed = false"))
        assertTrue(feedback.contains("reduceMotion"))
    }

    @Test fun `ordinary buttons share explicit press feedback without taking over callbacks`() {
        for (name in listOf("GenerateContent.kt", "ComposeGenerateActionButton.kt", "EditorChoiceField.kt",
            "ComposeFavoriteSaveDialog.kt", "FavoritesContent.kt", "ComposeLanShareQrDialog.kt",
            "ComposeSettingsSlider.kt", "LanShareImagePreviewDialog.kt", "ComposeSettingsComponents.kt",
            "LanShareInputBar.kt", "LanShareMessageBubble.kt", "LanShareUploadingBubble.kt",
            "ComposeCommonDialogs.kt")) {
            val button = source(name)
            assertTrue("$name 缺少按压反馈", button.contains(".iosPressFeedback("))
            assertTrue("$name 缺少手势源", button.contains("interactionSource ="))
        }
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
