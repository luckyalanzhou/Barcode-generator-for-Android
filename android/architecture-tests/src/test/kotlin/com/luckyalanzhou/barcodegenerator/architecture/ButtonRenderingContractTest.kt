package com.luckyalanzhou.barcodegenerator.architecture

import com.lemonappdev.konsist.api.Konsist
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 本地渲染边界约束不是截图测试：防止已知危险组合再次引入，真机观感仍需独立验收。 */
class ButtonRenderingContractTest {
    @Test fun `round result actions enhance glass optics while pressed`() {
        val button = source("GlassRoundActionButton.kt")
        assertTrue(button.contains("interaction.collectIsPressedAsState()"))
        assertTrue(button.contains("animateFloatAsState("))
        assertTrue(button.contains("if (effects.reduceMotion) snap() else spring("))
        assertTrue(button.contains("(11f + 4f * strength) * density"))
        assertTrue(button.contains("(18f + 6f * strength) * density"))
        assertTrue(button.contains("Highlight.Default.copy(alpha = .48f + .40f * progress)"))
        assertTrue(button.contains("Shadow(alpha = .18f + .24f * progress)"))
        assertFalse(button.contains("RuntimeShader("))
        assertFalse(button.contains("rememberGlassBackdropRenderer"))
        assertFalse(button.contains("GlassBackdropSurface("))
        val results = source("ResultsContent.kt")
        assertTrue(results.contains("val recordGlassBackdrop = android.os.Build.VERSION.SDK_INT >= 33"))
        assertTrue(results.contains(".then(if (recordGlassBackdrop) Modifier.layerBackdrop(resultBackdrop) else Modifier)"))
        assertTrue(results.indexOf("LazyColumn(") < results.indexOf("CompositionLocalProvider(LocalLiquidGlassBackdrop provides resultBackdrop)"))
        assertFalse(results.contains("rememberGlassBackdropRenderer"))
        assertFalse(results.contains("rememberGlassBackdrop("))
    }
    @Test fun `round action press updates material and keeps foreground readable`() {
        val button = source("GlassRoundActionButton.kt")
        assertTrue(button.contains("targetValue = if (pressed && enabled && !busy) 1f else 0f"))
        assertTrue(button.contains("InnerShadow(radius = (2f + 2f * progress).dp"))
        assertTrue(button.contains("content(contentTint)"))
        assertFalse(button.contains("GlassBackdropSurface("))
        assertFalse(button.contains("RuntimeShader("))
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
        val appearance = source("AppearanceThemeContent.kt")
        val settings = source("SettingsContent.kt")
        for ((sourceText, open) in listOf(appearance to "schemeMenu", settings to "ocrMenu")) {
            val beforeOpen = sourceText.substringBefore("onClick = { $open = true }").takeLast(180)
            assertTrue(beforeOpen.contains("contentColor = colors.settingsText.secondary"))
        }
        val about = source("AboutContent.kt")
        val update = about.substringAfter("SettingsSection(stringResource(R.string.about_app_info)")
            .substringBefore("SettingsSection(stringResource(R.string.title_licenses)")
        assertTrue(update.contains("busy = checkingForUpdates"))
        assertTrue(update.contains("contentColor = colors.controls.accent"))
        assertTrue(update.contains("onClick = onCheckForUpdates"))
        val action = source("ComposeSettingsComponents.kt").substringAfter("internal fun SettingsActionRow(").substringBefore("internal fun SettingsDropdownButton(")
        assertTrue(action.contains("SettingsRow(title, color, leading = leading)"))
        assertTrue(action.contains("SettingsButton(action, buttonColor, LocalAppColorScheme.current.controls.accent"))
    }
    @Test fun `photo fill uses the glass action bar and LAN entry is an accessible navigation row`() {
        val actions = source("GlassGenerateActionBar.kt")
        assertTrue(actions.contains("stringResource(R.string.add_row)"))
        assertTrue(actions.contains("stringResource(R.string.batch_paste)"))
        assertTrue(actions.contains("stringResource(R.string.ocr_take_photo)"))
        assertTrue(actions.contains("GenerateActionButton(labels[0], labelStyle, AddIcon"))
        assertTrue(actions.contains("GenerateActionButton(labels[1], labelStyle, ContentPasteIcon"))
        assertTrue(actions.contains("GenerateActionButton(labels[2], labelStyle, PhotoCameraIcon"))
        assertTrue(actions.contains("onClick = onClick"))
        assertTrue(actions.contains("role = Role.Button"))
        assertTrue(actions.contains("drawBackdrop("))
        val settings = source("SettingsContent.kt")
        val lanEntry = settings.substringAfter("SettingsSection(stringResource(R.string.settings_file_transfer)")
            .substringBefore("if (BuildConfig.DEBUG_LOG_EXPORT)")
        assertTrue(lanEntry.contains("SettingsNavigationRow("))
        assertTrue(lanEntry.contains("stringResource(R.string.settings_lan_file_share)"))
        assertTrue(lanEntry.contains("onEnterLanShare"))
        val navigation = source("ComposeSettingsComponents.kt")
            .substringAfter("internal fun SettingsNavigationRow(")
            .substringBefore("internal fun SettingsRow(")
        assertTrue(navigation.contains("clickable("))
        assertTrue(navigation.contains("role = Role.Button"))
        assertTrue(navigation.contains("onClick = onClick"))
    }
    @Test fun `filled primary actions use their contrast paired semantic color`() {
        val generate = source("GenerateContent.kt")
        assertTrue(generate.contains("if (generateEnabled) themeColors.controls.primaryAction"))
        assertTrue(generate.contains("if (generateEnabled) themeColors.text.onAccent"))
        val dialogs = source("ComposeCommonDialogs.kt")
        assertTrue(dialogs.contains("if (filled) colors.controls.primaryAction"))
        assertTrue(dialogs.contains("filled -> colors.text.onAccent"))
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
        for (name in listOf("ComposeSettingsComponents.kt",
            "ComposeCommonDialogs.kt", "EditorChoiceField.kt",
            "LanShareInputBar.kt",
            "LanShareContent.kt", "LanShareMessageBubble.kt", "LanShareUploadingBubble.kt",
            "ComposeLanShareQrDialog.kt",
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
        assertTrue(source("FavoriteSaveForm.kt").contains("ComposeChoiceField("))
        for (name in listOf("ComposeSettingsComponents.kt", "ComposeCommonDialogs.kt", "EditorChoiceField.kt",
            "ComposeLanShareQrDialog.kt",
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
        val format = source("GenerateContent.kt").substringAfter("interactionSource = formatInteractions,").substringBefore("AnchoredDropdownMenu(")
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
        assertTrue(source("FavoritesContent.kt").contains("LibrarySearchField("))
        for (name in listOf("GenerateContent.kt", "ComposeGenerateActionButton.kt", "EditorChoiceField.kt",
            "FavoriteSaveForm.kt", "LibrarySearchField.kt", "ComposeLanShareQrDialog.kt",
            "LanShareImagePreviewDialog.kt", "ComposeSettingsComponents.kt",
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
