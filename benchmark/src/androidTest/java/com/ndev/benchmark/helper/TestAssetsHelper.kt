package com.ndev.benchmark.helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Helper class to create test assets for benchmarks
 */
object TestAssetsHelper {

    /**
     * Creates a bitmap with random content for testing
     */
    private fun createTestBitmap(width: Int, height: Int, seed: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        // Fill background
        canvas.drawColor(Color.rgb(200, 200, 200))

        // Draw some shapes with different colors based on seed
        val random = java.util.Random(seed.toLong())
        for (i in 0..20) {
            paint.color = Color.rgb(
                random.nextInt(256),
                random.nextInt(256),
                random.nextInt(256)
            )

            val x = random.nextInt(width)
            val y = random.nextInt(height)
            val radius = 50 + random.nextInt(150)

            canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), paint)
        }

        // Draw some text
        paint.color = Color.BLACK
        paint.textSize = 50f
        canvas.drawText("Test Image #$seed", 50f, 100f, paint)

        return bitmap
    }


    fun createTestBlobDataAndFile(
        context: Context,
        count: Int = 5
    ): List<Triple<Long, ByteArray, File>> {
        val blobs = mutableListOf<Triple<Long, ByteArray, File>>()

        for (i in 1..count) {
            val multiplier = 0.5 + (i * 0.5) // 1x, 1.5x, 2x, 2.5x, 3x
            val size = (500 * multiplier).toInt()

            val bitmap = createTestBitmap(size, size, i)

            // Сжимаем bitmap ОДИН раз в байтовый поток
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            val data: ByteArray = outputStream.toByteArray()

            // Записываем тот же байтовый массив в файл
            val file = File(context.filesDir, "test_image_$i.jpg")
            file.writeBytes(data)

            blobs += Triple(i.toLong(), data, file)

            bitmap.recycle()
        }

        return blobs
    }



}