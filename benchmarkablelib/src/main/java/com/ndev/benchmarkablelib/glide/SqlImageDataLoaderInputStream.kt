package com.ndev.benchmarkablelib.glide

import android.util.Log
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import com.ndev.benchmarkablelib.model.SqlImageData
import com.ndev.benchmarkablelib.repository.ImageRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException

class SqlImageDataFetcherInputStream(
    private val model: SqlImageData,
    private val repository: ImageRepository
) : DataFetcher<InputStream> {

    companion object {
        private const val TAG = "SqlImageDataFetcher"
    }

    // Add Job for coroutine cancellation
    private var job: Job? = null

    private var inputStream: InputStream? = null

    @Throws(Exception::class)
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        Log.d(TAG, "loadData started for image: ${model.imageName}")
        // Create exception handler
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            // Ignore CancellationException as it's expected behavior when cancelling
            if (throwable !is CancellationException) {
                callback.onLoadFailed(throwable as java.lang.Exception)
            }
        }

        // Create job for managing coroutine lifecycle with exception handler
        job = CoroutineScope(Dispatchers.IO + exceptionHandler).launch {
            // Check if coroutine is not cancelled
            ensureActive()

            val blobData = repository.getImageBlobByName(model.imageName)
                ?: throw RuntimeException("SqlImageDataFetcher: get image blob failed (name=${model.imageName})")
            inputStream = ByteArrayInputStream(blobData)
            // Check cancellation before calling callback
            ensureActive()

            // Only call callback if task wasn't cancelled
            callback.onDataReady(inputStream)
        }
    }

    override fun cleanup() {
        Log.d(TAG, "cleanup called")
        try {
            inputStream?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close input stream", e)
        } finally {
            inputStream = null
        }
    }

    override fun cancel() {
        Log.d(TAG, "cancel called")
        job?.cancel()
        job = null
    }

    override fun getDataClass(): Class<InputStream> = InputStream::class.java

    override fun getDataSource(): DataSource = DataSource.LOCAL
}

class SqlImageModelLoaderInputStream(
    private val repository: ImageRepository
) : ModelLoader<SqlImageData, InputStream> {
    override fun buildLoadData(
        model: SqlImageData,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream> {
        // We use ObjectKey since our signature hasn't changed
        return ModelLoader.LoadData(
            ObjectKey(model.imageName),
            SqlImageDataFetcherInputStream(model, repository)
        )
    }

    override fun handles(model: SqlImageData): Boolean {
        Log.d("SqlImageModelLoaderByteArray", "handles")
        return true
    }
}

class SqlImageModelLoaderFactoryInputStream(
    private val repository: ImageRepository
) : ModelLoaderFactory<SqlImageData, InputStream> {
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<SqlImageData, InputStream> {
        return SqlImageModelLoaderInputStream(repository)
    }

    override fun teardown() {
        // Nothing to clean up
    }
}
