package com.ndev.benchmarkablelib.glide

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

class SqlImageDataFetcher(
    private val model: SqlImageData,
    private val repository: ImageRepository
) : DataFetcher<ByteBuffer> {

    // Добавляем Job для возможности отмены корутины
    private var job: Job? = null
    // Добавляем переменную для хранения буфера
    private var buffer: ByteBuffer? = null

    @Throws(Exception::class)
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in ByteBuffer>) {
        // Создаем обработчик исключений
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            // Игнорируем CancellationException, так как это ожидаемое поведение при отмене
            if (throwable !is CancellationException) {
                callback.onLoadFailed(throwable as java.lang.Exception)
            }
        }

        // Создаем job для управления жизненным циклом корутины с обработчиком исключений
        job = CoroutineScope(Dispatchers.IO + exceptionHandler).launch {
            // Проверяем, не отменена ли корутина
            ensureActive()

            val blobData = repository.getImageBlobByName(model.imageName)
                ?: throw RuntimeException("SqlImageDataFetcher: get image blob failed (name=${model.imageName})")

            // Проверка отмены перед выделением памяти
            ensureActive()

            // 1. Выделяем direct-буфер
            val newBuffer = ByteBuffer.allocateDirect(blobData.size)
            // 2. Копируем в него данные
            newBuffer.put(blobData)
            // 3. Подготовка к чтению
            newBuffer.flip()

            // Сохраняем ссылку на буфер для возможности очистки
            buffer = newBuffer

            // Проверка отмены перед вызовом callback
            ensureActive()

            // Вызываем callback только если задача не была отменена
            callback.onDataReady(newBuffer)
        }
    }

    override fun cleanup() {
        // Очищаем ресурсы ByteBuffer
        buffer?.let {
            // Для direct-буфера нет явных методов очистки в JVM,
            // но можно установить ссылку в null для сборщика мусора
            buffer = null
        }
    }

    override fun cancel() {
        // Отменяем корутину при вызове cancel
        job?.cancel()
        job = null
    }

    override fun getDataClass(): Class<ByteBuffer> = ByteBuffer::class.java

    override fun getDataSource(): DataSource = DataSource.LOCAL
}

class SqlImageModelLoader(
    private val repository: ImageRepository
) : ModelLoader<SqlImageData, ByteBuffer> {
    override fun buildLoadData(
        model: SqlImageData,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<ByteBuffer> {
        // Мы используем ObjectKey, поскольку наша сигнатура не изменилась
        return ModelLoader.LoadData(
            ObjectKey(model.imageName),
            SqlImageDataFetcher(model, repository)
        )
    }

    override fun handles(model: SqlImageData): Boolean = true
}

class SqlImageModelLoaderFactory(
    private val repository: ImageRepository
) : ModelLoaderFactory<SqlImageData, ByteBuffer> {
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<SqlImageData, ByteBuffer> {
        return SqlImageModelLoader(repository)
    }

    override fun teardown() = Unit
}
