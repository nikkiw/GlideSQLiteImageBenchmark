package com.ndev.benchmarkablelib.glide

import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import com.ndev.benchmarkablelib.model.BlobData
import java.io.ByteArrayInputStream
import java.io.InputStream


class BlobDataFetcherDirect(
    private val model: BlobData
) : DataFetcher<InputStream> {

    private var stream: InputStream? = null

    override fun loadData(
        priority: Priority,
        callback: DataFetcher.DataCallback<in InputStream>
    ) {
        stream = ByteArrayInputStream(model.data)
        callback.onDataReady(stream)
    }

    override fun cleanup() {
        // Закрываем поток (хоть он и слегка условный для direct-буфера)
        stream?.close()
    }

    override fun cancel() {
        // Нет асинхронной работы — можно оставить пустым
        cleanup()
    }

    override fun getDataClass(): Class<InputStream> = InputStream::class.java

    override fun getDataSource(): DataSource = DataSource.LOCAL
}


class BlobDataModelLoaderDirect : ModelLoader<BlobData, InputStream> {
    override fun buildLoadData(
        model: BlobData,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream> {
        return ModelLoader.LoadData(ObjectKey(model.id), BlobDataFetcherDirect(model))
    }

    override fun handles(model: BlobData): Boolean = true
}

class BlobDataModelLoaderFactory : ModelLoaderFactory<BlobData, InputStream> {
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<BlobData, InputStream> {
        return BlobDataModelLoaderDirect()
    }

    override fun teardown() = Unit
}

//// Сигнатура для кэширования BlobData
//class BlobDataSignature(private val data: BlobData) : com.bumptech.glide.load.Key {
//    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
//        messageDigest.update(data.data)
//    }
//
//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (javaClass != other?.javaClass) return false
//        other as BlobDataSignature
//        return data.data.contentEquals(other.data.data)
//    }
//
//    override fun hashCode(): Int = data.data.contentHashCode()
//}