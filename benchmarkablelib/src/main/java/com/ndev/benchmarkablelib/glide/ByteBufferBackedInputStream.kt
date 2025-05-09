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
import java.io.InputStream
import java.nio.ByteBuffer


class ByteArrayDataFetcherDirect(
    private val model: BlobData
) : DataFetcher<ByteBuffer> {

    override fun loadData(
        priority: Priority,
        callback: DataFetcher.DataCallback<in ByteBuffer>
    ) {

        // 1. Выделяем direct-буфер
        val buffer = ByteBuffer.allocateDirect(model.data.size)
        // 2. Копируем в него данные
        buffer.put(model.data)
        // 3. Подготовка к чтению
        buffer.flip()

        callback.onDataReady(buffer)
    }

    override fun cleanup() {
        // Закрываем поток (хоть он и слегка условный для direct-буфера)
    }

    override fun cancel() {
        // Нет асинхронной работы — можно оставить пустым
    }

    override fun getDataClass(): Class<ByteBuffer> = ByteBuffer::class.java

    override fun getDataSource(): DataSource = DataSource.LOCAL
}


class ByteArrayModelLoaderDirect : ModelLoader<BlobData, ByteBuffer> {
    override fun buildLoadData(
        model: BlobData,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<ByteBuffer> {
        // Мы используем ObjectKey, поскольку наша сигнатура не изменилась
        return ModelLoader.LoadData(ObjectKey(model.data), ByteArrayDataFetcherDirect(model))
    }

    override fun handles(model: BlobData): Boolean = true
}

class ByteArrayModelLoaderFactoryDirect : ModelLoaderFactory<BlobData, ByteBuffer> {
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<BlobData, ByteBuffer> {
        return ByteArrayModelLoaderDirect()
    }

    override fun teardown() = Unit
}