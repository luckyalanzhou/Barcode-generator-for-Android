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
    @Test fun `static result rims use one gradient outline separate from reflection`() {
        val rim = source("ResultActionRim.kt")
        assertTrue(rim.contains("edgeHighlight -> Brush.verticalGradient("))
        assertTrue(rim.contains("drawOutline(outerOutline, rim, style = Stroke(stroke))"))
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
    @Test fun `filled primary actions use their contrast paired semantic color`() {
        val generate = source("GenerateContent.kt")
        assertTrue(generate.contains("if (generateEnabled) themeColors.controls.primaryAction"))
        assertTrue(generate.contains("if (generateEnabled) themeColors.text.onAccent"))
        val dialogs = source("ComposeCommonDialogs.kt")
        assertTrue(dialogs.contains("if (primary) colors.controls.primaryAction"))
        assertTrue(dialogs.contains("primary -> colors.text.onAccent"))
        assertTrue(source("LightColorScheme.kt").contains("primary = colors.controls.primaryAction"))
        assertTrue(source("DarkColorScheme.kt").contains("primary = colors.controls.primaryAction"))
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
            "ComposeCommonDialogs.kt", "EditorChoiceField.kt", "FavoriteSaveForm.kt",
            "LanShareInputBar.kt",
            "LanShareContent.kt", "LanShareMessageBubble.kt", "LanShareUploadingBubble.kt",
            "ComposeLanShareQrDialog.kt", "ComposeSettingsSlider.kt",
            "LanShareImagePreviewDialog.kt")) {
            val buttonSource = source(name)
            assertTrue("$name 必须使用统一静态按钮框", buttonSource.contains(".globalButtonChrome(") || buttonSource.contains(".resultActionEdgeChrome("))
        }
    }

    @Test fun `LAN download button keeps one outline in light and dark themes`() {
        val bubble = source("LanShareMessageBubble.kt")
        val downloadButton = bubble.substringAfter("val downloadShape = RoundedCornerShape(12.dp)")
            .substringBefore("Text(\n                            \"下载\"")

        // 下载入口只保留统一自绘单层轮廓，不再叠加 Material Button 的表面实现。
        assertFalse(downloadButton.contains("Button("))
        assertTrue(downloadButton.contains("clickable("))
        assertTrue(downloadButton.contains("role = Role.Button"))
        assertTrue(downloadButton.contains("interactionSource = downloadInteraction"))
        assertTrue(downloadButton.contains("widthIn(min = ButtonDefaults.MinWidth)"))
        assertTrue(downloadButton.contains("shape = downloadShape"))
        assertTrue(downloadButton.contains("rimBackground = bubbleColor"))
        assertTrue(downloadButton.contains("edgeHighlight = false"))
        val rim = source("ResultActionRim.kt")
        assertTrue(rim.contains("edgeHighlight: Boolean = true"))
        assertTrue(rim.contains("drawOutline(outerOutline, rim, style = Stroke(stroke))"))
        assertFalse("共享按钮边缘不能再画第二条内侧闭合描边", rim.contains("bandOutline"))
    }

    @Test fun `requested controls reuse result action rim while unframed pickers stay unframed`() {
        for (name in listOf("ComposeSettingsComponents.kt", "ComposeCommonDialogs.kt", "EditorChoiceField.kt",
            "FavoriteSaveForm.kt", "ComposeLanShareQrDialog.kt", "ComposeSettingsSlider.kt",
            "LanShareImagePreviewDialog.kt", "LanShareContent.kt", "LanShareInputBar.kt",
            "LanShareMessageBubble.kt", "LanShareUploadingBubble.kt")) {
            assertTrue("$name 的既有外框必须复用结果页边缘高光", source(name).contains(".resultActionEdgeChrome("))
        }
        val settings = source("ComposeSettingsComponents.kt")
        assertTrue(settings.contains("showOutline = false"))
        assertTrue(settings.contains("if (showOutline) Modifier.resultActionEdgeChrome"))
        val editor = source("EditorChoiceField.kt")
        assertTrue(editor.contains("if (compact) Modifier else Modifier.resultActionEdgeChrome"))
        val edgeChrome = source("ResultActionEdgeChrome.kt")
        assertTrue(edgeChrome.contains("drawResultActionRim("))
        for (forbidden in listOf(".background(", ".padding(", ".size(", "graphicsLayer", "shadow(")) {
            assertFalse("边缘组件不能改变按钮结构或外观填充: $forbidden", edgeChrome.contains(forbidden))
        }
        val tabSurface = source("TabGlassSurface.kt")
        assertTrue(tabSurface.contains("drawResultActionRim("))
        assertTrue(tabSurface.contains("drawMotionHighlight && frame.motion > .001f"))
        val shader = source("GlassBackdropShader.kt")
        assertTrue(shader.contains("capsuleOptics.x > 0.001"))
        assertTrue(shader.contains("* contact.z"))
        assertFalse(shader.contains("float bevel ="))
    }

    @Test fun `sorting and row deletion remain unframed with explicit press feedback`() {
        for (name in listOf("ComposeGenerateInputPanel.kt", "ComposeHistoryUi.kt")) {
            val button = source(name)
            assertFalse("$name 不添加按钮框", button.contains("globalButtonChrome"))
            assertTrue(button.contains(".unframedActionPressFeedback("))
            assertTrue(button.contains("interactionSource ="))
        }
    }

    @Test fun `value picker triggers are unframed and removed display effects controls stay absent`() {
        val settings = source("ComposeSettingsComponents.kt")
        val dropdown = settings.substringAfter("internal fun SettingsDropdownButton(").substringBefore("internal fun SettingsButton(")
        assertTrue(dropdown.contains("SettingsButton(text, Color.Transparent"))
        assertTrue(dropdown.contains("showOutline = false"))
        assertTrue(settings.contains("if (showOutline) Modifier.resultActionEdgeChrome"))
        assertTrue(settings.contains("Text(text, color = contentColor, fontSize = 16.sp"))
        val settingsContent = source("SettingsContent.kt")
        assertFalse("已移除显示与动效入口", settingsContent.contains("显示与动效"))
        assertFalse("已移除显示与动效偏好绑定", settingsContent.contains("effectsSettings"))
        assertFalse(source("ComposeSettingsSlider.kt").contains("fontSize = 15.sp"))
        val format = source("GenerateContent.kt").substringAfter("onClick = { formatExpanded = true }").substringBefore("AnchoredDropdownMenu(")
        assertFalse(format.contains("globalButtonChrome"))
        assertTrue(format.contains("containerColor = Color.Transparent"))
        assertTrue(format.contains("Text(formatName, color = themeColors.text.secondary"))
        val editor = source("EditorChoiceField.kt")
        assertTrue(editor.contains("if (compact) Modifier else Modifier.resultActionEdgeChrome"))
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
            "FavoriteSaveForm.kt", "FavoritesContent.kt", "ComposeLanShareQrDialog.kt",
            "ComposeSettingsSlider.kt", "LanShareImagePreviewDialog.kt", "ComposeSettingsComponents.kt",
            "LanShareInputBar.kt", "LanShareMessageBubble.kt", "LanShareUploadingBubble.kt",
            "ComposeCommonDialogs.kt")) {
            val button = source(name)
            assertTrue("$name 缺少按压反馈", button.contains(".iosPressFeedback("))
            assertTrue("$name 缺少手势源", button.contains("interactionSource ="))
        }
    }

    @Test fun `ordinary press feedback honors reduced motion and keeps immediate visual confirmation`() {
        val feedback = source("IosPressFeedback.kt")
        assertTrue(feedback.contains("LocalVisualEffectsPolicy.current.reduceMotion"))
        assertTrue(feedback.contains("pressed && !reduceMotion"))
        assertTrue(feedback.contains("if (reduceMotion) snap<Float>()"))
        assertTrue(feedback.contains("targetValue = if (pressed) pressedAlpha else 1f"))
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
