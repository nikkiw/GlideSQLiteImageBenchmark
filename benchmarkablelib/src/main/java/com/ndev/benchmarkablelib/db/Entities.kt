package com.ndev.benchmarkablelib.db

import android.util.Log
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
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

class CustomOpenHelperFactory : SupportSQLiteOpenHelper.Factory {
    private val delegate = FrameworkSQLiteOpenHelperFactory()

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val wrappedCallback =
            object : SupportSQLiteOpenHelper.Callback(configuration.callback.version) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    Log.d("AppDatabase", "onConfigure")

                    // Установим page_size до создания таблиц
                    db.query("PRAGMA page_size = 8192;").moveToFirst()
                    db.query("PRAGMA cache_size = -2000;")
                        .moveToFirst()   // “-2000” — 2000 страниц × 32768 байт = ~64 МБ кэша
                    db.query("PRAGMA temp_store = MEMORY;")
                        .moveToFirst() // временные таблицы/индексы в RAM
                    db.query("PRAGMA mmap_size = 268435456;").moveToFirst() // например, 256 МБ
                    db.query("VACUUM;")
                    configuration.callback.onConfigure(db)
                }

                override fun onCreate(db: SupportSQLiteDatabase) {
                    // VACUUM для применения page_size
                    Log.d("AppDatabase", "onCreate")

                    configuration.callback.onCreate(db)
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) {
                    configuration.callback.onUpgrade(db, oldVersion, newVersion)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    configuration.callback.onOpen(db)
                }
            }

        val newConfig = SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
            .name(configuration.name)
            .callback(wrappedCallback)
            .build()

        return delegate.create(newConfig)
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

        fun reset(){
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
                    //.openHelperFactory(CustomOpenHelperFactory())
                    .setDriver(BundledSQLiteDriver())
//                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()

//                // Verify the setting
//                val db = (instance as RoomDatabase).openHelper.writableDatabase
//                var cursor = db.query("SELECT sqlite_version()")
//                if (cursor.moveToFirst()) {
//                    val pageSize = cursor.getInt(0)
//                    Log.d("AppDatabase", "sqlite_version is: $pageSize")
//                }
//
//                cursor = db.query("PRAGMA page_size")
//                if (cursor.moveToFirst()) {
//                    val pageSize = cursor.getInt(0)
//                    Log.d("AppDatabase", "Page size set to: $pageSize")
//                }
//
//                cursor = db.query("PRAGMA cache_size")
//                if (cursor.moveToFirst()) {
//                    val pageSize = cursor.getInt(0)
//                    Log.d("AppDatabase", "cache_size set to: $pageSize")
//                }
//
//                cursor = db.query("PRAGMA temp_store")
//                if (cursor.moveToFirst()) {
//                    val pageSize = cursor.getInt(0)
//                    Log.d("AppDatabase", "temp_store set to: $pageSize")
//                }
//
//                cursor = db.query("PRAGMA mmap_size")
//                if (cursor.moveToFirst()) {
//                    val pageSize = cursor.getInt(0)
//                    Log.d("AppDatabase", "mmap_size set to: $pageSize")
//                }
                INSTANCE = instance
                instance
            }
        }
    }
}

// Утилитарный класс для работы с файлами изображений
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