package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsButtonTextStyleTest {
    @Test fun inheritedLightAndDarkBackgroundsAreCleared() {
        listOf(Color.White, Color.Black, Color.Gray).forEach { inheritedBackground ->
            assertEquals(Color.Transparent,
                settingsButtonTextStyle(TextStyle(background = inheritedBackground)).background)
        }
    }

    @Test fun clearingBackgroundPreservesTypography() {
        val inherited = TextStyle(color = Color.Blue, fontSize = 14.sp,
            fontWeight = FontWeight.Medium, lineHeight = 24.sp, background = Color.White)
        assertEquals(inherited.copy(background = Color.Transparent), settingsButtonTextStyle(inherited))
    }
}
