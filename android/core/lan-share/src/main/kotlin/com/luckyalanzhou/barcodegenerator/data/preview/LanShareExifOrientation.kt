package com.luckyalanzhou.barcodegenerator.data.preview

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** 表示 JPEG EXIF 方向值中的旋转与镜像变换。 */
internal data class ExifOrientationTransform(
    val rotationDegrees: Float,
    val scaleX: Float,
    val scaleY: Float,
)

/** 映射完整的八种 EXIF 方向；镜像与转置方向不能只按旋转处理。 */
internal fun exifOrientationTransform(orientation: Int): ExifOrientationTransform? = when (orientation) {
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ExifOrientationTransform(0f, -1f, 1f)
    ExifInterface.ORIENTATION_ROTATE_180 -> ExifOrientationTransform(180f, 1f, 1f)
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> ExifOrientationTransform(0f, 1f, -1f)
    ExifInterface.ORIENTATION_TRANSPOSE -> ExifOrientationTransform(90f, -1f, 1f)
    ExifInterface.ORIENTATION_ROTATE_90 -> ExifOrientationTransform(90f, 1f, 1f)
    ExifInterface.ORIENTATION_TRANSVERSE -> ExifOrientationTransform(270f, -1f, 1f)
    ExifInterface.ORIENTATION_ROTATE_270 -> ExifOrientationTransform(270f, 1f, 1f)
    else -> null
}

/** 在旧版 Android 上应用 EXIF 方向，并释放被替换的原始位图。 */
internal fun applyExifOrientation(source: File, bitmap: Bitmap): Bitmap {
    val orientation = runCatching {
        ExifInterface(source).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val transform = exifOrientationTransform(orientation) ?: return bitmap
    val matrix = Matrix().apply {
        setRotate(transform.rotationDegrees)
        postScale(transform.scaleX, transform.scaleY)
    }
    val oriented = runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }.getOrNull() ?: return bitmap
    if (oriented !== bitmap) bitmap.recycle()
    return oriented
}
