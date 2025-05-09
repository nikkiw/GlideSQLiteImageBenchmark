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
import com.ndev.benchmarkablelib.glide.SqlImageModelLoaderFactory
import com.ndev.benchmarkablelib.model.BlobData
import com.ndev.benchmarkablelib.model.SqlImageData
import com.ndev.benchmarkablelib.performance.GlidePerformanceTester
import com.ndev.benchmarkablelib.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
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
            clearGlideCache()
        }
    }

    @Test
    fun load_single_image_from_file() {
        benchmarkRule.measureRepeated {
            runTest {
                tester.testFileLoading(
                    imageFiles = listOf(testImageFiles.first())
                )
            }
        }
    }

    @Test
    fun load_single_blob_image_using_default_loader() {
        runBlobSingleImageTest(
            BlobData::class.java,
            InputStream::class.java,
            BlobDataModelLoaderFactory()
        )
    }

    @Test
    fun load_single_blob_image_using_okio_loader() {
        runBlobSingleImageTest(
            BlobData::class.java,
            InputStream::class.java,
            BlobDataModelLoaderFactoryOkio()
        )
    }

    @Test
    fun load_single_blob_image_using_direct_bytebuffer_loader() {
        runBlobSingleImageTest(
            BlobData::class.java,
            ByteBuffer::class.java,
            ByteArrayModelLoaderFactoryDirect()
        )
    }

    @Test
    fun load_single_sql_image_blob() {
        runBlobSingleImageTest(
            SqlImageData::class.java,
            ByteBuffer::class.java,
            SqlImageModelLoaderFactory(repository)
        )
    }

    private fun <Model, Data> runBlobSingleImageTest(
        model: Class<Model>,
        dataClass: Class<Data>,
        factory: ModelLoaderFactory<Model, Data>
    ) {
        benchmarkRule.measureRepeated {
            runWithTimingDisabled {
                Glide.get(context)
                    .registry.prepend(
                        model,
                        dataClass,
                        factory
                    )
            }

            runTest {
                tester.testBlobLoading(
                    imageIds = listOf(testImageIds.first()),
                    imageNames = listOf(testImageNames.first()),
                    model = model
                )
            }
        }
    }

    @Test
    fun load_multiple_images_from_files_in_parallel() {
        benchmarkRule.measureRepeated {
            runTest {
                tester.testParallelFileLoading(
                    imageFiles = testImageFiles
                )
            }
        }
    }

    @Test
    fun load_multiple_blob_images_in_parallel_using_default_loader() {
        runBlobParallelTest(
            BlobData::class.java,
            InputStream::class.java,
            BlobDataModelLoaderFactory()
        )
    }

    @Test
    fun load_multiple_blob_images_in_parallel_using_okio_loader() {
        runBlobParallelTest(
            BlobData::class.java,
            InputStream::class.java,
            BlobDataModelLoaderFactoryOkio()
        )
    }

    @Test
    fun load_multiple_blob_images_in_parallel_using_direct_bytebuffer_loader() {
        runBlobParallelTest(
            BlobData::class.java,
            ByteBuffer::class.java,
            ByteArrayModelLoaderFactoryDirect()
        )
    }

    @Test
    fun load_multiple_sql_image_blobs_in_parallel() {
        runBlobParallelTest(
            SqlImageData::class.java,
            ByteBuffer::class.java,
            SqlImageModelLoaderFactory(repository)
        )
    }

    private fun <Model, Data> runBlobParallelTest(
        model: Class<Model>,
        dataClass: Class<Data>,
        factory: ModelLoaderFactory<Model, Data>
    ) {
        benchmarkRule.measureRepeated {
            runWithTimingDisabled {
                Glide.get(context)
                    .registry.prepend(
                        model,
                        dataClass,
                        factory
                    )
            }

            runTest {
                tester.testParallelBlobLoading(
                    imageIds = testImageIds,
                    imageNames = testImageNames,
                    model = model
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
