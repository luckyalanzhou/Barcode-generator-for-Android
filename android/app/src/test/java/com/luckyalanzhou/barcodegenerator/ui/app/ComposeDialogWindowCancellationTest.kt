package com.luckyalanzhou.barcodegenerator.ui.app

import android.app.Activity
import android.app.Application
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ComposeDialogWindowCancellationTest {
    @Suppress("DEPRECATION")
    @Test
    fun userCancellationIsIgnoredWhileSaveIsInProgressAndRestoredAfterward() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var saving = false
        val dialog = createComposeDialog(activity) { !saving }.apply {
            setContentView(FrameLayout(activity).apply { addView(View(activity)) })
            setCanceledOnTouchOutside(true)
            show()
        }

        saving = true
        dialog.onBackPressed()

        assertTrue("保存期间系统返回不能关闭对话框", dialog.isShowing)

        dialog.cancel()

        assertTrue("保存期间用户取消不能关闭对话框", dialog.isShowing)

        saving = false
        dialog.cancel()

        assertFalse("保存结束后应恢复正常取消行为", dialog.isShowing)
    }
}
