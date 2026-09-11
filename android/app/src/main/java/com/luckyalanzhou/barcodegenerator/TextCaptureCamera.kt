package com.luckyalanzhou.barcodegenerator

import android.app.Dialog
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.TimeUnit

/** App 内置的拍照取字相机，避免系统相机无法控制对焦和取景的问题。 */
internal fun MainActivity.showTextCaptureCamera() {
    val dialog = Dialog(this).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCancelable(true)
    }
    val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
    val previewView = PreviewView(this).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }
    root.addView(previewView, FrameLayout.LayoutParams(-1, -1))
    root.addView(TextCaptureGuideView(this), FrameLayout.LayoutParams(-1, -1))

    val title = TextView(this).apply {
        text = "拍照取字"
        textSize = 18f
        setTextColor(Color.WHITE)
        setShadowLayer(5f, 0f, 1f, Color.BLACK)
        setPadding(dp(20), dp(18), dp(20), dp(10))
    }
    root.addView(title, FrameLayout.LayoutParams(-1, dp(58), Gravity.TOP))

    val controls = FrameLayout(this).apply {
        setPadding(dp(20), dp(12), dp(20), dp(20))
        setBackgroundColor(0x99000000.toInt())
    }
    val cancel = TextView(this).apply {
        text = "取消"
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        isClickable = true
        setOnClickListener { dialog.dismiss() }
    }
    controls.addView(cancel, FrameLayout.LayoutParams(dp(72), dp(52), Gravity.CENTER_VERTICAL or Gravity.START))
    val shutter = ImageButton(this).apply {
        contentDescription = "拍摄并识别"
        setImageResource(android.R.drawable.ic_menu_camera)
        setColorFilter(Color.WHITE)
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(0x33FFFFFF)
            setStroke(dp(3), Color.WHITE)
        }
        setPadding(dp(16), dp(16), dp(16), dp(16))
        isClickable = true
    }
    controls.addView(shutter, FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER))
    root.addView(controls, FrameLayout.LayoutParams(-1, dp(104), Gravity.BOTTOM))
    dialog.setContentView(root)
    dialog.setOnDismissListener { previewView.controller = null }
    dialog.show()
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.black)
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        statusBarColor = Color.BLACK
        navigationBarColor = Color.BLACK
    }

    val outputFile = File.createTempFile("barcode_text_capture_", ".jpg", cacheDir)
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    cameraProviderFuture.addListener({
        runCatching {
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3).build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()
            provider.unbindAll()
            val camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            camera.cameraControl.startFocusAndMetering(
                FocusMeteringAction.Builder(previewView.meteringPointFactory.createPoint(0.5f, 0.5f))
                    .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
            )
            previewView.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    camera.cameraControl.startFocusAndMetering(
                        FocusMeteringAction.Builder(previewView.meteringPointFactory.createPoint(event.x, event.y))
                            .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                    )
                }
                true
            }
            shutter.setOnClickListener {
                shutter.isEnabled = false
                capture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(outputFile).build(),
                    ContextCompat.getMainExecutor(this),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            dialog.dismiss()
                            val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                            if (bitmap == null) toast("拍摄失败，请重试")
                            else recognizeText(prepareTextBitmap(bitmap, outputFile))
                            outputFile.delete()
                        }

                        override fun onError(exception: ImageCaptureException) {
                            shutter.isEnabled = true
                            outputFile.delete()
                            toast("拍摄失败，请重试")
                        }
                    }
                )
            }
        }.onFailure {
            outputFile.delete()
            dialog.dismiss()
            toast("无法启动相机，请检查相机权限")
        }
    }, ContextCompat.getMainExecutor(this))
}

private class TextCaptureGuideView(context: android.content.Context) : View(context) {
    private val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * resources.displayMetrics.scaledDensity
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        val frame = RectF(width * 0.06f, height * 0.27f, width * 0.94f, height * 0.68f)
        canvas.drawRect(0f, 0f, width, frame.top, dim)
        canvas.drawRect(0f, frame.bottom, width, height, dim)
        canvas.drawRect(0f, frame.top, frame.left, frame.bottom, dim)
        canvas.drawRect(frame.right, frame.top, width, frame.bottom, dim)
        canvas.drawRoundRect(frame, 18f, 18f, border)
        canvas.drawText("让屏幕文字完整进入取景框", width / 2f, frame.bottom + 34f * resources.displayMetrics.density, label)
    }
}
