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
import java.nio.ByteBuffer
import kotlin.coroutines.cancellation.CancellationException

class SqlImageDataFetcherByteBuffer(
    private val model: SqlImageData,
    private val repository: ImageRepository
) : DataFetcher<ByteBuffer> {

    companion object {
        private const val TAG = "SqlImageDataFetcher"
    }

    // Add Job for coroutine cancellation
    private var job: Job? = null

    // Add variable to store buffer
    private var buffer: ByteBuffer? = null

    @Throws(Exception::class)
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in ByteBuffer>) {
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

            // Check cancellation before allocating memory
            ensureActive()

//            // 1. Allocate direct buffer
//            val newBuffer = ByteBuffer.allocateDirect(blobData.size)
//            // 2. Copy data into it
//            newBuffer.put(blobData)
//            // 3. Prepare for reading
//            newBuffer.flip()


            // Store buffer reference for cleanup
//            buffer = newBuffer
            buffer = ByteBuffer.wrap(blobData)
            // Check cancellation before calling callback
            ensureActive()

            // Only call callback if task wasn't cancelled
            callback.onDataReady(buffer)
        }
    }

    override fun cleanup() {
        // Clean up ByteBuffer resources
        buffer = null
    }

    override fun cancel() {
        Log.d(TAG, "cancel")
        // Cancel coroutine when cancel is called
        job?.cancel()
        job = null
    }

    override fun getDataClass(): Class<ByteBuffer> = ByteBuffer::class.java

    override fun getDataSource(): DataSource = DataSource.LOCAL
}

class SqlImageModelLoaderByteBuffer(
    private val repository: ImageRepository
) : ModelLoader<SqlImageData, ByteBuffer> {
    override fun buildLoadData(
        model: SqlImageData,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<ByteBuffer> {
        // We use ObjectKey since our signature hasn't changed
        return ModelLoader.LoadData(
            ObjectKey(model.imageName),
            SqlImageDataFetcherByteBuffer(model, repository)
        )
    }

    override fun handles(model: SqlImageData): Boolean = true
}

class SqlImageModelLoaderFactoryByteBuffer(
    private val repository: ImageRepository
) : ModelLoaderFactory<SqlImageData, ByteBuffer> {
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<SqlImageData, ByteBuffer> {
        return SqlImageModelLoaderByteBuffer(repository)
    }

    override fun teardown() {
        // Nothing to clean up
    }
}
