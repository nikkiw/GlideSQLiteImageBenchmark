package com.ndev.benchmarkablelib.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.io.FileOutputStream

@Entity(
    tableName = "images",
    indices = [Index(value = ["name"], unique = true)]
)
data class ImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val blobData: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImageEntity

        if (id != other.id) return false
        if (name != other.name) return false
        if (!blobData.contentEquals(other.blobData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + blobData.contentHashCode()
        return result
    }
}

@Dao
interface ImageDao {
    @Insert
    suspend fun insertImage(image: ImageEntity): Long

    @Query("SELECT * FROM images WHERE id = :id")
    suspend fun getImageById(id: Long): ImageEntity?

    @Query("SELECT * FROM images WHERE name = :name")
    suspend fun getImageByName(name: String): ImageEntity?

    @Query("SELECT * FROM images")
    suspend fun getAllImages(): List<ImageEntity>

    @Query("DELETE FROM images")
    suspend fun deleteAllImages()
}

@Database(entities = [ImageEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun imageDao(): ImageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun reset() {
            INSTANCE = null
        }

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbName = "app_database"
                context.deleteDatabase(dbName)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    dbName
                )
                    .setDriver(BundledSQLiteDriver())
                    .build()


                INSTANCE = instance
                instance
            }
        }
    }
}

// Utility class for working with image files
object FileUtils {
    fun saveImageToFile(
        context: android.content.Context,
        name: String,
        imageData: ByteArray
    ): File {
        val file = File(context.filesDir, name)
        FileOutputStream(file).use { output ->
            output.write(imageData)
        }
        return file
    }
}
