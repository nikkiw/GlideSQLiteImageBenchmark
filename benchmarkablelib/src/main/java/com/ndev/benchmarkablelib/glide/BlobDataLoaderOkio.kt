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
import com.ndev.benchmarkablelib.model.BlobData
import okio.Buffer
import okio.BufferedSource
import java.io.IOException
import java.io.InputStream

// DataFetcher with cancellation support and using Okio for buffer operations
class BlobDataFetcherOkio(
    private val model: BlobData
) : DataFetcher<InputStream> {

    @Volatile
    private var isCanceled = false
    private var stream: BufferedSource? = null

    override fun loadData(
        priority: Priority,
        callback: DataFetcher.DataCallback<in InputStream>
    ) {
        if (isCanceled) {
            callback.onLoadFailed(IOException("Load canceled"))
            return
        }

        try {
            // Use Okio for efficient buffered reading
            stream = Buffer().write(model.data)
            val inputStream = stream!!.inputStream()

            if (isCanceled) {
                inputStream.close()
                callback.onLoadFailed(IOException("Load canceled after buffer allocation"))
                return
            }

            callback.onDataReady(inputStream)
        } catch (e: Exception) {
            Log.e("BlobDataFetcher", "Error loading BlobData", e)
            callback.onLoadFailed(e)
        }
    }

    override fun cleanup() {
        try {
            stream?.close()
        } catch (ignored: IOException) {
            // Ignore errors during cleanup
        }
    }

    override fun cancel() {
        isCanceled = true
    }

    override fun getDataClass(): Class<InputStream> = InputStream::class.java

    override fun getDataSource(): DataSource = DataSource.LOCAL
}

// ModelLoader and its factory
class BlobDataModelLoaderOkio : ModelLoader<BlobData, InputStream> {
    override fun buildLoadData(
        model: BlobData,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream>? {
        return ModelLoader.LoadData(ObjectKey(model.id), BlobDataFetcherOkio(model))
    }

    override fun handles(model: BlobData): Boolean = model.data.isNotEmpty()
}

class BlobDataModelLoaderFactoryOkio : ModelLoaderFactory<BlobData, InputStream> {
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<BlobData, InputStream> {
        return BlobDataModelLoaderOkio()
    }

    override fun teardown() {
        // Nothing to clean up
    }
}
