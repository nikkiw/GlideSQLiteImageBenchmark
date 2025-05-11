package com.ndev.benchmarkablelib.performance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.Downsampler
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
import kotlin.coroutines.resume

fun Bitmap?.requireValid(): Bitmap {
    if (this == null || isRecycled || width <= 0 || height <= 0) {
        throw IllegalStateException("Invalid Bitmap: null, recycled or has zero dimensions")
    }
    return this
}

class GlidePerformanceTester(
    private val context: Context,
    private val repository: ImageRepository
) {
    /**
     * Load each file synchronously into a Bitmap without caching.
     */
    suspend fun testFileLoading(
        imageFiles: List<File>,
        isHardwareBitmap: Boolean
    ) = withContext(Dispatchers.Default) {
        for (file in imageFiles) {
            Glide.with(context)
                .asBitmap()
                .set(Downsampler.ALLOW_HARDWARE_CONFIG, isHardwareBitmap)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .load(file.absolutePath)
                .submit()
                .get()
                .requireValid()
        }
    }

    /**
     * Load images from various data models (BlobData, ByteArray, or SqlImageData).
     */
    suspend fun <Model> testBlobLoading(
        imageIds: List<Long>,
        imageNames: List<String>,
        model: Class<Model>,
        isHardwareBitmap: Boolean
    ) = withContext(Dispatchers.Default) {
        for (i in imageIds.indices) {
            val id = imageIds[i]
            val name = imageNames[i]
            val data = when (model) {
                BlobData::class.java -> {
                    val imageBlob = repository.getImageBlobByName(name)
                        ?: throw RuntimeException("Failed to retrieve blob (id=$id, name=$name)")
                    BlobData(id, imageBlob)
                }
                ByteArray::class.java -> {
                    repository.getImageBlobByName(name)
                        ?: throw RuntimeException("Failed to retrieve blob (id=$id, name=$name)")
                }
                else -> SqlImageData(name)
            }

            Glide.with(context)
                .asBitmap()
                .set(Downsampler.ALLOW_HARDWARE_CONFIG, isHardwareBitmap)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .load(data)
                .submit()
                .get()
                .requireValid()
        }
    }

    /**
     * Measure UI load performance for file-based images and record results.
     */
    suspend fun testFileLoadingInUI(
        imageFiles: List<File>,
        imageView: ImageView
    ): List<PerformanceResult> = withContext(Dispatchers.Default) {
        val results = mutableListOf<PerformanceResult>()

        // Clear caches before testing
        withContext(Dispatchers.Main) { Glide.get(context).clearMemory() }
        withContext(Dispatchers.IO) { Glide.get(context).clearDiskCache() }

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

                    cont.invokeOnCancellation { target.request?.clear() }
                }
            }

            val endTime = System.nanoTime()
            val elapsedMs = (endTime - startTime) / 1_000_000.0

            results.add(
                PerformanceResult(
                    sourceType = "FILE",
                    fileName = file.name,
                    fileSize = file.length(),
                    loadTimeMs = elapsedMs,
                    iteration = 1,
                    success = success
                )
            )

            // Pause briefly to avoid request overlap
            delay(100)
        }

        results
    }

    /**
     * Load all files in parallel into Bitmaps without interacting with UI.
     */
    suspend fun testParallelFileLoading(
        imageFiles: List<File>,
        isHardwareBitmap: Boolean
    ) = withContext(Dispatchers.Default) {
        imageFiles.map { file ->
            async {
                Glide.with(context)
                    .asBitmap()
                    .load(file.absolutePath)
                    .set(Downsampler.ALLOW_HARDWARE_CONFIG, isHardwareBitmap)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .submit()
                    .get()
                    .requireValid()
            }
        }.awaitAll()
    }

    /**
     * Measure UI performance for blob-based images and record results.
     */
    suspend fun testBlobLoadingInUI(
        imageIds: List<Long>,
        imageNames: List<String>,
        imageView: ImageView
    ): List<PerformanceResult> = withContext(Dispatchers.Default) {
        val results = mutableListOf<PerformanceResult>()

        withContext(Dispatchers.Main) { Glide.get(context).clearMemory() }
        withContext(Dispatchers.IO) { Glide.get(context).clearDiskCache() }

        for (i in imageIds.indices) {
            val name = imageNames[i]
            val startTime = System.nanoTime()

            val imageBlob = repository.getImageBlobByName(name)
            var success = false

            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<Unit> { cont ->
                    val target = Glide.with(context)
                        .load(imageBlob)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
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

                    cont.invokeOnCancellation { target.request?.clear() }
                }
            }

            val endTime = System.nanoTime()
            val elapsedMs = (endTime - startTime) / 1_000_000.0

            results.add(
                PerformanceResult(
                    sourceType = "BLOB",
                    fileName = name,
                    fileSize = 1,
                    loadTimeMs = elapsedMs,
                    iteration = 1,
                    success = success
                )
            )

            delay(100)
        }

        results
    }

    /**
     * Load all blob-based data models in parallel and validate results.
     */
    suspend fun <Model> testParallelBlobLoading(
        imageIds: List<Long>,
        imageNames: List<String>,
        model: Class<Model>,
        isHardwareBitmap: Boolean
    ) = withContext(Dispatchers.Default) {
        imageIds.mapIndexed { idx, id ->
            async {
                val name = imageNames[idx]
                val data = when (model) {
                    BlobData::class.java -> {
                        val imageBlob = repository.getImageBlobByName(name)
                            ?: throw RuntimeException("Failed to retrieve blob (id=$id, name=$name)")
                        BlobData(id, imageBlob)
                    }
                    ByteArray::class.java -> {
                        repository.getImageBlobByName(name)
                            ?: throw RuntimeException("Failed to retrieve blob (id=$id, name=$name)")
                    }
                    else -> SqlImageData(name)
                }

                Glide.with(context)
                    .asBitmap()
                    .load(data)
                    .set(Downsampler.ALLOW_HARDWARE_CONFIG, isHardwareBitmap)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .submit()
                    .get()
                    .requireValid()
            }
        }.awaitAll()
    }

    companion object {
        private const val TAG = "GlidePerformanceTester"
    }
}
