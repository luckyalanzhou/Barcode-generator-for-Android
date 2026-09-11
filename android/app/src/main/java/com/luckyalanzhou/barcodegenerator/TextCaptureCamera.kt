package com.luckyalanzhou.barcodegenerator

import android.app.Dialog
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
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
import java.util.Locale
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
    val guide = TextCaptureGuideView(this)
    root.addView(guide, FrameLayout.LayoutParams(-1, -1))

    val title = TextView(this).apply {
        text = "拍照取字"
        textSize = 18f
        setTextColor(Color.WHITE)
        setShadowLayer(5f, 0f, 1f, Color.BLACK)
        setPadding(dp(20), dp(18), dp(20), dp(10))
    }
    root.addView(title, FrameLayout.LayoutParams(-1, dp(58), Gravity.TOP))

    val zoomLabel = TextView(this).apply {
        text = "1.0×"
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setBackgroundColor(0x66000000)
        setPadding(dp(10), 0, dp(10), 0)
    }
    val zoomOut = TextView(this).apply {
        text = "−"
        textSize = 22f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setBackgroundColor(0x66000000)
    }
    val zoomIn = TextView(this).apply {
        text = "+"
        textSize = 22f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setBackgroundColor(0x66000000)
    }
    val zoomControls = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(zoomOut, LinearLayout.LayoutParams(dp(42), dp(42)))
        addView(zoomLabel, LinearLayout.LayoutParams(dp(76), dp(42)).apply { setMargins(dp(2), 0, dp(2), 0) })
        addView(zoomIn, LinearLayout.LayoutParams(dp(42), dp(42)))
    }
    root.addView(zoomControls, FrameLayout.LayoutParams(dp(164), dp(42), Gravity.TOP or Gravity.END).apply {
        topMargin = dp(12)
        rightMargin = dp(14)
    })

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
            val zoomState = camera.cameraInfo.zoomState.value
            var zoomRatio = zoomState?.zoomRatio ?: 1f
            fun updateZoom(delta: Float) {
                val state = camera.cameraInfo.zoomState.value ?: return
                zoomRatio = (zoomRatio + delta).coerceIn(state.minZoomRatio, state.maxZoomRatio)
                camera.cameraControl.setZoomRatio(zoomRatio)
                zoomLabel.text = String.format(Locale.US, "%.1f×", zoomRatio)
            }
            zoomOut.setOnClickListener { updateZoom(-0.5f) }
            zoomIn.setOnClickListener { updateZoom(0.5f) }
            zoomLabel.setOnClickListener { camera.cameraControl.setZoomRatio(1f); zoomRatio = 1f; zoomLabel.text = "1.0×" }
            camera.cameraControl.startFocusAndMetering(
                FocusMeteringAction.Builder(previewView.meteringPointFactory.createPoint(0.5f, 0.5f))
                    .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
            )
            val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    updateZoom((detector.scaleFactor - 1f) * 2f)
                    return true
                }
            })
            previewView.setOnTouchListener { _, event ->
                scaleDetector.onTouchEvent(event)
                if (event.actionMasked == MotionEvent.ACTION_UP && !scaleDetector.isInProgress) {
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
                            else {
                                val prepared = prepareTextBitmap(bitmap, outputFile)
                                val cropped = cropTextBitmap(prepared, guide.frameForBitmap(prepared.width.toFloat() / prepared.height.coerceAtLeast(1)))
                                recognizeText(prepareScreenOcrBitmap(cropped))
                            }
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
    internal val frameNormalized = RectF(0.06f, 0.27f, 0.94f, 0.68f)
    private var dragCorner = 0
    private val handleRadius = 28f * resources.displayMetrics.density
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
        val frame = RectF(width * frameNormalized.left, height * frameNormalized.top, width * frameNormalized.right, height * frameNormalized.bottom)
        canvas.drawRect(0f, 0f, width, frame.top, dim)
        canvas.drawRect(0f, frame.bottom, width, height, dim)
        canvas.drawRect(0f, frame.top, frame.left, frame.bottom, dim)
        canvas.drawRect(frame.right, frame.top, width, frame.bottom, dim)
        canvas.drawRoundRect(frame, 18f, 18f, border)
        canvas.drawCircle(frame.left, frame.top, handleRadius / 2f, border)
        canvas.drawCircle(frame.right, frame.top, handleRadius / 2f, border)
        canvas.drawCircle(frame.left, frame.bottom, handleRadius / 2f, border)
        canvas.drawCircle(frame.right, frame.bottom, handleRadius / 2f, border)
        canvas.drawText("让屏幕文字完整进入取景框", width / 2f, frame.bottom + 34f * resources.displayMetrics.density, label)
    }

    /** 将铺满预览视图的取景框换算成实际 JPEG 的归一化坐标。 */
    internal fun frameForBitmap(bitmapAspect: Float): RectF {
        val viewAspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        return if (viewAspect < bitmapAspect) {
            val visibleWidth = viewAspect / bitmapAspect
            val visibleLeft = (1f - visibleWidth) / 2f
            RectF(
                visibleLeft + frameNormalized.left * visibleWidth,
                frameNormalized.top,
                visibleLeft + frameNormalized.right * visibleWidth,
                frameNormalized.bottom
            )
        } else {
            val visibleHeight = bitmapAspect / viewAspect
            val visibleTop = (1f - visibleHeight) / 2f
            RectF(
                frameNormalized.left,
                visibleTop + frameNormalized.top * visibleHeight,
                frameNormalized.right,
                visibleTop + frameNormalized.bottom * visibleHeight
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x / width.coerceAtLeast(1).toFloat()
        val y = event.y / height.coerceAtLeast(1).toFloat()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val corners = arrayOf(
                    floatArrayOf(frameNormalized.left, frameNormalized.top),
                    floatArrayOf(frameNormalized.right, frameNormalized.top),
                    floatArrayOf(frameNormalized.left, frameNormalized.bottom),
                    floatArrayOf(frameNormalized.right, frameNormalized.bottom)
                )
                dragCorner = corners.indexOfFirst { (x - it[0]) * width < handleRadius && (y - it[1]) * height < handleRadius && kotlin.math.abs(x - it[0]) * width < handleRadius && kotlin.math.abs(y - it[1]) * height < handleRadius } + 1
                return dragCorner > 0
            }
            MotionEvent.ACTION_MOVE -> if (dragCorner > 0) {
                val minSize = 0.22f
                when (dragCorner) {
                    1 -> { frameNormalized.left = x.coerceIn(0.02f, frameNormalized.right - minSize); frameNormalized.top = y.coerceIn(0.10f, frameNormalized.bottom - minSize) }
                    2 -> { frameNormalized.right = x.coerceIn(frameNormalized.left + minSize, 0.98f); frameNormalized.top = y.coerceIn(0.10f, frameNormalized.bottom - minSize) }
                    3 -> { frameNormalized.left = x.coerceIn(0.02f, frameNormalized.right - minSize); frameNormalized.bottom = y.coerceIn(frameNormalized.top + minSize, 0.90f) }
                    4 -> { frameNormalized.right = x.coerceIn(frameNormalized.left + minSize, 0.98f); frameNormalized.bottom = y.coerceIn(frameNormalized.top + minSize, 0.90f) }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragCorner > 0) { dragCorner = 0; return true }
        }
        return false
    }
}

private fun cropTextBitmap(bitmap: android.graphics.Bitmap, normalized: RectF): android.graphics.Bitmap {
    val left = (bitmap.width * normalized.left).toInt().coerceIn(0, bitmap.width - 1)
    val top = (bitmap.height * normalized.top).toInt().coerceIn(0, bitmap.height - 1)
    val right = (bitmap.width * normalized.right).toInt().coerceIn(left + 1, bitmap.width)
    val bottom = (bitmap.height * normalized.bottom).toInt().coerceIn(top + 1, bitmap.height)
    return runCatching { android.graphics.Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top) }.getOrDefault(bitmap)
}

/**
 * 屏幕像素网格会制造高频摩尔纹。先适度降采样，再做一次保边低通，
 * 让网格纹理变弱而不把表格文字锐化成更明显的伪影。
 */
private fun prepareScreenOcrBitmap(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
    var source = bitmap
    val longest = maxOf(source.width, source.height)
    if (longest > 1800) {
        val scale = 1800f / longest.toFloat()
        source = android.graphics.Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true)
    }
    if (source.width < 900 || source.height < 600) return source
    val width = source.width
    val height = source.height
    val input = IntArray(width * height)
    val output = IntArray(width * height)
    source.getPixels(input, 0, width, 0, 0, width, height)
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            var red = 0
            var green = 0
            var blue = 0
            for (dy in -1..1) for (dx in -1..1) {
                val weight = if (dx == 0 && dy == 0) 4 else if (dx == 0 || dy == 0) 2 else 1
                val color = input[(y + dy) * width + x + dx]
                red += android.graphics.Color.red(color) * weight
                green += android.graphics.Color.green(color) * weight
                blue += android.graphics.Color.blue(color) * weight
            }
            output[y * width + x] = android.graphics.Color.rgb(red / 16, green / 16, blue / 16)
        }
    }
    for (x in 0 until width) { output[x] = input[x]; output[(height - 1) * width + x] = input[(height - 1) * width + x] }
    for (y in 0 until height) { output[y * width] = input[y * width]; output[y * width + width - 1] = input[y * width + width - 1] }
    return android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888).also { it.setPixels(output, 0, width, 0, 0, width, height) }
}
