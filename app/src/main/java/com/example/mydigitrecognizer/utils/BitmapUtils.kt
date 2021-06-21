package com.example.mydigitrecognizer.utils

import android.graphics.Bitmap
import android.graphics.Matrix

fun Bitmap.toSquare(): Bitmap {
    val side = kotlin.math.min(width, height)
    val xOffset = (width - side)/2
    val yOffset = (height - side)/2

    return Bitmap.createBitmap(
        this,
        xOffset,
        yOffset,
        side,
        side
    )
}


fun rotateBitmap(original: Bitmap): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(90f)
    return Bitmap.createBitmap(
        original,
        0,
        0,
        original.width,
        original.height,
        matrix,
        true
    )
}
