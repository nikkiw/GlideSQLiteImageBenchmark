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

// DataFetcher с поддержкой отмены и использованием Okio для buffer-операций
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
            // Используем Okio для эффективного буферного чтения
            stream = Buffer().write(model.data)
            val inputStream = stream!!.inputStream()

            if (isCanceled) {
                inputStream.close()
                callback.onLoadFailed(IOException("Load canceled after buffer allocation"))
                return
            }

            callback.onDataReady(inputStream)
        }
//        catch (oom: OutOfMemoryError) {
//            Log.e("BlobDataFetcher", "OOM при выделении буфера", oom)
//            callback.onLoadFailed(oom)
//        }
        catch (e: Exception) {
            Log.e("BlobDataFetcher", "Ошибка загрузки BlobData", e)
            callback.onLoadFailed(e)
        }
    }

    override fun cleanup() {
        try {
            stream?.close()
        } catch (ignored: IOException) {
            // Игнорируем ошибки при закрытии
        }
    }

    override fun cancel() {
        isCanceled = true
    }

    override fun getDataClass(): Class<InputStream> = InputStream::class.java

    override fun getDataSource(): DataSource = DataSource.LOCAL
}

// ModelLoader и фабрика для него
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

    override fun teardown() = Unit
}
