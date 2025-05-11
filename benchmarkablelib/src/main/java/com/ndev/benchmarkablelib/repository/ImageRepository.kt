package com.ndev.benchmarkablelib.repository


import android.content.Context
import com.ndev.benchmarkablelib.db.AppDatabase
import com.ndev.benchmarkablelib.db.FileUtils
import com.ndev.benchmarkablelib.db.ImageDao
import com.ndev.benchmarkablelib.db.ImageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class ImageRepository(
    private val context: Context,
    private val appDatabase: AppDatabase = AppDatabase.getDatabase(context),
    private val imageDao: ImageDao = appDatabase.imageDao()
) {
    // Save image to database and disk
    suspend fun saveImage(name: String, imageData: ByteArray): Pair<Long, File> =
        withContext(Dispatchers.IO) {
            // Save to database
            val imageEntity = ImageEntity(name = name, blobData = imageData)
            val id = imageDao.insertImage(imageEntity)

            // Save to disk
            val file = FileUtils.saveImageToFile(context, name, imageData)

            Pair(id, file)
        }

    // Save image to database and disk with specified ID
    suspend fun saveImage(id: Long, name: String, imageData: ByteArray): Pair<Long, File> =
        withContext(Dispatchers.IO) {
            // Save to database
            val imageEntity = ImageEntity(id = id, name = name, blobData = imageData)
            val id = imageDao.insertImage(imageEntity)

            // Save to disk
            val file = FileUtils.saveImageToFile(context, name, imageData)

            Pair(id, file)
        }

    // Get image blob from database by ID
    suspend fun getImageBlobById(id: Long): ByteArray? = withContext(Dispatchers.IO) {
        imageDao.getImageById(id)?.blobData
    }

    // Get image blob from database by name
    suspend fun getImageBlobByName(name: String): ByteArray? = withContext(Dispatchers.IO) {
        imageDao.getImageByName(name)?.blobData
    }

    suspend fun getAllImages(): List<ImageEntity> = withContext(Dispatchers.IO) {
        imageDao.getAllImages()
    }


    // Get image file by name
    fun getImageFile(name: String): File {
        return File(context.filesDir, name)
    }

    // Load test images from app resources
    suspend fun loadTestImages(imageResIds: List<Int>): List<Triple<Long, String, File>> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<Triple<Long, String, File>>()

            for ((index, resId) in imageResIds.withIndex()) {
                try {
                    val name = "test_image_$index.jpg"
                    val bytes = context.resources.openRawResource(resId).use { inputStream ->
                        inputStream.readBytes()
                    }

                    val (id, file) = saveImage(name, bytes)
                    results.add(Triple(id, name, file))
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            results
        }

    suspend fun addTestBlob(id: Long, imageBlob: ByteArray, fileName: String) {
        saveImage(id, fileName, imageBlob)
    }


    // Clear all images
    suspend fun clearAllImages() = withContext(Dispatchers.IO) {
        imageDao.deleteAllImages()
        context.filesDir.listFiles()?.filter {
            it.name.startsWith("test_image_")
        }?.forEach {
            it.delete()
        }
    }

}
