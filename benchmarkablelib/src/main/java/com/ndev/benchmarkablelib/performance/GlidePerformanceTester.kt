package com.ndev.benchmarkablelib.performance

import android.content.Context
import android.graphics.drawable.Drawable
//import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.ndev.benchmarkablelib.model.BlobData
import com.ndev.benchmarkablelib.model.PerformanceResult
import com.ndev.benchmarkablelib.model.SqlImageData
import com.ndev.benchmarkablelib.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class GlidePerformanceTester(
    private val context: Context,
    private val repository: ImageRepository
) {
    // Тестирование загрузки из файла
    suspend fun testFileLoading(
        imageFiles: List<File>
    ) = withContext(Dispatchers.Default) {
        for (file in imageFiles) {
//            Log.d(TAG, "Starting tests for file: ${file.name}, size=${file.length()} bytes")

            // Glide: без ImageView, сразу в Bitmap
            Glide.with(context)
                .asBitmap()
                .load(file.absolutePath)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .submit()
                .get(1, TimeUnit.SECONDS)
        }
    }


    // Тестирование загрузки из BLOB
    suspend fun <Model> testBlobLoading(
        imageIds: List<Long>,
        imageNames: List<String>,
        model: Class<Model>
    ) = withContext(Dispatchers.Default) {

        // Тестируем каждое изображение
        for (i in imageIds.indices) {
            val id = imageIds[i]
            val name = imageNames[i]
//            Log.d(TAG, "testBlobLoading name=$name")
            val data = if (model == BlobData::class.java) {
                val imageBlob =
                    repository.getImageBlobByName(name)
                        ?: throw RuntimeException("get image blob failed (id=$id, name=$name)")
                BlobData(id, imageBlob)
            } else {
                SqlImageData(name)
            }
            Glide.with(context)
                .asBitmap()
                .load(data)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .submit()
                .get(1, TimeUnit.SECONDS)
        }
    }

    // Тестирование загрузки из файла
    suspend fun testFileLoadingInUI(
        imageFiles: List<File>,
        imageView: ImageView
    ): List<PerformanceResult> = withContext(Dispatchers.Default) {
        val results = mutableListOf<PerformanceResult>()

        withContext(Dispatchers.Main) {
            Glide.get(context).clearMemory()
        }
        withContext(Dispatchers.IO) {
            Glide.get(context).clearDiskCache()
        }

        for (file in imageFiles) {
            val startTime = System.nanoTime()
            var success = false

            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<Unit> { cont ->
                    val target = Glide.with(context)
                        .load(file.absolutePath)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .listener(object : RequestListener<Drawable> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<Drawable>?,
                                isFirstResource: Boolean
                            ): Boolean {
                                success = false
                                cont.resume(Unit)
                                return false
                            }

                            override fun onResourceReady(
                                resource: Drawable?,
                                model: Any?,
                                target: Target<Drawable>?,
                                dataSource: DataSource?,
                                isFirstResource: Boolean
                            ): Boolean {
                                success = true
                                cont.resume(Unit)
                                return false
                            }
                        })
                        .into(imageView)

                    cont.invokeOnCancellation {
                        target.request?.clear()
                    }
                }
            }

            val endTime = System.nanoTime()
            val elapsedTime = (endTime - startTime) / 1_000_000.0

            results.add(
                PerformanceResult(
                    sourceType = "FILE",
                    fileName = file.name,
                    fileSize = file.length(),
                    loadTimeMs = elapsedTime,
                    iteration = 1,
                    success = success
                )
            )

            // Небольшая пауза между итерациями
            delay(100)
        }

//        Log.d(TAG, "All iterations complete, returning results (${results.size} entries)")
        results
    }


    // Тестирование параллельной загрузки из файла
    suspend fun testParallelFileLoading(
        imageFiles: List<File>
    ) = withContext(Dispatchers.Default) {

        imageFiles.map { file ->
            async {
                // Glide: без ImageView, сразу в Bitmap
                val future = Glide.with(context)
                    .asBitmap()
                    .load(file.absolutePath)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .submit() // запускает загрузку в фоне

                // Блокируем текущую корутину до результата
                future.get() // тут реально грузит

                Glide.with(context).clear(future) // освобождаем таргет
            }
        }.awaitAll() // дождаться всех загрузок

    }


    // Тестирование загрузки из BLOB
    suspend fun testBlobLoadingInUI(
        imageIds: List<Long>,
        imageNames: List<String>,
        imageView: ImageView
    ): List<PerformanceResult> = withContext(Dispatchers.Default) {
        val results = mutableListOf<PerformanceResult>()

        withContext(Dispatchers.Main) {
            Glide.get(context).clearMemory()
        }

        withContext(Dispatchers.IO) {
            Glide.get(context).clearDiskCache()
        }

        // Тестируем каждое изображение
        for (i in imageIds.indices) {
//            val id = imageIds[i]
            val name = imageNames[i]

            // Получаем BLOB данные из БД
            val startTime = System.nanoTime()

//                val imageBlob = repository.getImageBlobById(id) ?: continue
//                val imageBlob = repository.getImageBlobByName(name) ?: continue
//                val byteBuffer = ByteBuffer.wrap(imageBlob)

            var success = false

            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<Unit> { cont ->
                    val target = Glide.with(context)
//                            .load(BlobData(id, imageBlob))
//                            .load(imageBlob)
                        .load(SqlImageData(name))
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true) // Отключаем кэширование в памяти для честного теста
                        .listener(object : RequestListener<Drawable> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<Drawable>?,
                                isFirstResource: Boolean
                            ): Boolean {
                                cont.resume(Unit)
                                return false
                            }

                            override fun onResourceReady(
                                resource: Drawable?,
                                model: Any?,
                                target: Target<Drawable>?,
                                dataSource: DataSource?,
                                isFirstResource: Boolean
                            ): Boolean {
                                success = true
                                cont.resume(Unit)
                                return false
                            }
                        })
                        .into(imageView)

                    // если корутину отменили — очистим запрос
                    cont.invokeOnCancellation {
                        target.request?.clear()
                    }
                }
            }

            val endTime = System.nanoTime()
            val elapsedTime = (endTime - startTime) / 1_000_000.0 // Время в миллисекундах

            results.add(
                PerformanceResult(
                    sourceType = "BLOB",
                    fileName = name,
                    fileSize = 1, //imageBlob.size.toLong(),
                    loadTimeMs = elapsedTime,
                    iteration = 1,
                    success = success
                )
            )

            // Небольшая пауза между итерациями
            delay(100)
        }

        results
    }

    // Тестирование параллельной загрузки из BLOB
    suspend fun <Model> testParallelBlobLoading(
        imageIds: List<Long>,
        imageNames: List<String>,
        model: Class<Model>
    ) = withContext(Dispatchers.Default) {
        imageIds.mapIndexed { idx, id ->
            async {
                val name = imageNames[idx]

                val data = if (model == BlobData::class.java) {
                    val imageBlob =
                        repository.getImageBlobByName(name)
                            ?: throw RuntimeException("get image blob failed (id=$id, name=$name)")
                    BlobData(id, imageBlob)
                } else {
                    SqlImageData(name)
                }

                // Glide: без ImageView, сразу в Bitmap
                val future = Glide.with(context)
                    .asBitmap()
                    .load(data)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .submit() // запускает загрузку в фоне

                Glide.with(context).clear(future) // освобождаем таргет
            }
        }.awaitAll()
    }

    companion object {
        private const val TAG = "GlidePerformanceTester"
    }
}