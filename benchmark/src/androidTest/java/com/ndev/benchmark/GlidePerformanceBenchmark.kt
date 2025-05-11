package com.ndev.benchmark

import android.content.Context
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.ndev.benchmark.helper.TestAssetsHelper
import com.ndev.benchmarkablelib.db.AppDatabase
import com.ndev.benchmarkablelib.glide.BlobDataModelLoaderFactory
import com.ndev.benchmarkablelib.glide.BlobDataModelLoaderFactoryOkio
import com.ndev.benchmarkablelib.glide.ByteArrayModelLoaderFactoryDirect
import com.ndev.benchmarkablelib.glide.SqlImageModelLoaderFactoryByteBuffer
import com.ndev.benchmarkablelib.glide.SqlImageModelLoaderFactoryInputStream
import com.ndev.benchmarkablelib.model.BlobData
import com.ndev.benchmarkablelib.model.SqlImageData
import com.ndev.benchmarkablelib.performance.GlidePerformanceTester
import com.ndev.benchmarkablelib.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Benchmark for Glide image loading performance
 *
 * To run this benchmark:
 * `:benchmark:connectedReleaseAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
class ImprovedGlidePerformanceBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()
    private lateinit var tester: GlidePerformanceTester

    companion object {
        private lateinit var context: Context
        private lateinit var repository: ImageRepository
        private lateinit var testImageFiles: List<File>
        private lateinit var testImageIds: List<Long>
        private lateinit var testImageNames: List<String>
        private lateinit var blobData: List<Triple<Long, ByteArray, File>>

        @JvmStatic
        @BeforeClass
        fun setupClass() {
            runBlocking {
                context = ApplicationProvider.getApplicationContext()

                blobData = TestAssetsHelper.createTestBlobDataAndFile(context, count = 10)

                testImageIds = blobData.map { it.first }
                testImageNames = blobData.map { it.third.name }
                testImageFiles = blobData.map { it.third }

            }
        }

        @JvmStatic
        @AfterClass
        fun tearDown() {
            testImageFiles.forEach { it.delete() }
        }
    }

    @Before
    fun setup() {
        runBlocking {
            Glide.tearDown()
            AppDatabase.reset()
            repository = ImageRepository(context)
            blobData.forEach { (id, data, file) ->
                repository.addTestBlob(id, data, file.name)
            }
            tester = GlidePerformanceTester(context, repository)
        }
    }

    @After
    fun tearDownCache() {
        runBlocking {
            Glide.tearDown()
            clearGlideCache()
        }
    }

    @Test
    fun load_sequentially_from_files_hardware_off() {
        benchmarkRule.measureRepeated {
            runBlocking {
                tester.testFileLoading(
                    imageFiles = testImageFiles,
                    isHardwareBitmap = false
                )
            }
        }
    }

    @Test
    fun load_sequentially_from_files_hardware_on() {
        benchmarkRule.measureRepeated {
            runBlocking {
                tester.testFileLoading(
                    imageFiles = testImageFiles,
                    isHardwareBitmap = true
                )
            }
        }
    }

    @Test
    fun load_parallel_from_files_hardware_off() {
        benchmarkRule.measureRepeated {
            runBlocking {
                tester.testParallelFileLoading(
                    imageFiles = testImageFiles,
                    isHardwareBitmap = false
                )
            }
        }
    }

    @Test
    fun load_parallel_from_files_hardware_on() {
        benchmarkRule.measureRepeated {
            runBlocking {
                tester.testParallelFileLoading(
                    imageFiles = testImageFiles,
                    isHardwareBitmap = true
                )
            }
        }
    }


    @Test
    fun load_sequentially_from_byte_array_hardware_off() {
        runLoadSequentiallyCustomDataImageTest(
            ByteArray::class.java,
            ByteArray::class.java,
            null,
            false
        )
    }

    @Test
    fun load_sequentially_from_byte_array_hardware_on() {
        runLoadSequentiallyCustomDataImageTest(
            ByteArray::class.java,
            ByteArray::class.java,
            null,
            true
        )
    }


    @Test
    fun load_parallel_from_byte_array_hardware_off() {
        runLoadParallelCustomDataImageTest(
            ByteArray::class.java,
            ByteArray::class.java,
            null,
            false
        )
    }


    @Test
    fun load_parallel_from_byte_array_hardware_on() {
        runLoadParallelCustomDataImageTest(
            ByteArray::class.java,
            ByteArray::class.java,
            null,
            true
        )
    }


    @Test
    fun load_sequentially_from_blob_data_using_default_loader() {
        runLoadSequentiallyCustomDataImageTest(
            BlobData::class.java,
            InputStream::class.java,
            BlobDataModelLoaderFactory()
        )
    }

    @Test
    fun load_parallel_from_blob_data_using_default_loader() {
        runLoadParallelCustomDataImageTest(
            BlobData::class.java,
            InputStream::class.java,
            BlobDataModelLoaderFactory()
        )
    }

    @Test
    fun load_sequentially_from_blob_data_using_okio_loader() {
        runLoadSequentiallyCustomDataImageTest(
            BlobData::class.java,
            InputStream::class.java,
            BlobDataModelLoaderFactoryOkio()
        )
    }

    @Test
    fun load_parallel_from_blob_data_using_okio_loader() {
        runLoadParallelCustomDataImageTest(
            BlobData::class.java,
            InputStream::class.java,
            BlobDataModelLoaderFactoryOkio()
        )
    }

    @Test
    fun load_sequentially_from_blob_data_using_direct_bytebuffer_loader() {
        runLoadSequentiallyCustomDataImageTest(
            BlobData::class.java,
            ByteBuffer::class.java,
            ByteArrayModelLoaderFactoryDirect()
        )
    }

    @Test
    fun load_parallel_from_blob_data_using_direct_bytebuffer_loader() {
        runLoadParallelCustomDataImageTest(
            BlobData::class.java,
            ByteBuffer::class.java,
            ByteArrayModelLoaderFactoryDirect()
        )
    }

    @Test
    fun load_sequentially_from_sql_image_data_to_byte_buffer() {
        runLoadSequentiallyCustomDataImageTest(
            SqlImageData::class.java,
            ByteBuffer::class.java,
            SqlImageModelLoaderFactoryByteBuffer(repository)
        )
    }

    @Test
    fun load_parallel_from_sql_image_data_to_byte_buffer() {
        runLoadParallelCustomDataImageTest(
            SqlImageData::class.java,
            ByteBuffer::class.java,
            SqlImageModelLoaderFactoryByteBuffer(repository)
        )
    }

    @Test
    fun load_sequentially_from_sql_image_data_to_input_stream() {
        runLoadSequentiallyCustomDataImageTest(
            SqlImageData::class.java,
            InputStream::class.java,
            SqlImageModelLoaderFactoryInputStream(repository)
        )
    }


    @Test
    fun load_parallel_from_sql_image_data_to_input_stream() {
        runLoadParallelCustomDataImageTest(
            SqlImageData::class.java,
            InputStream::class.java,
            SqlImageModelLoaderFactoryInputStream(repository)
        )
    }

    private fun <Model, Data> runLoadSequentiallyCustomDataImageTest(
        model: Class<Model>,
        dataClass: Class<Data>?,
        factory: ModelLoaderFactory<Model, Data>?,
        isHardwareBitmap: Boolean = false
    ) {
        benchmarkRule.measureRepeated {
            runWithTimingDisabled {
                if (dataClass != null && factory != null) {
                    Glide.get(context)
                        .registry.prepend(
                            model,
                            dataClass,
                            factory
                        )
                }
            }

            runBlocking {
                tester.testBlobLoading(
                    imageIds = testImageIds,
                    imageNames = testImageNames,
                    model = model,
                    isHardwareBitmap = isHardwareBitmap
                )
            }
        }
    }

    private fun <Model, Data> runLoadParallelCustomDataImageTest(
        model: Class<Model>,
        dataClass: Class<Data>?,
        factory: ModelLoaderFactory<Model, Data>?,
        isHardwareBitmap: Boolean = false
    ) {
        benchmarkRule.measureRepeated {
            runWithTimingDisabled {
                if (dataClass != null && factory != null) {
                    Glide.get(context)
                        .registry.prepend(
                            model,
                            dataClass,
                            factory
                        )
                }
            }

            runBlocking {
                tester.testParallelBlobLoading(
                    imageIds = testImageIds,
                    imageNames = testImageNames,
                    model = model,
                    isHardwareBitmap = isHardwareBitmap
                )
            }
        }
    }

    // Helper method to clear Glide cache
    private fun clearGlideCache() {
        runBlocking {
            withContext(Dispatchers.Main) {
                Glide.get(context).clearMemory()
            }
            withContext(Dispatchers.IO) {
                Glide.get(context).clearDiskCache()
            }
        }
    }
}
