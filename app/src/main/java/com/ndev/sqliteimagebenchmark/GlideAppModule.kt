package com.ndev.sqliteimagebenchmark


import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.ndev.benchmarkablelib.glide.BlobDataModelLoaderFactory
import com.ndev.benchmarkablelib.glide.SqlImageModelLoaderFactory
import com.ndev.benchmarkablelib.model.BlobData
import com.ndev.benchmarkablelib.model.SqlImageData
import com.ndev.benchmarkablelib.repository.ImageRepository
import java.io.InputStream
import java.nio.ByteBuffer

@GlideModule
class BlobGlideModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {

        registry.prepend(
            BlobData::class.java,
            InputStream::class.java,
            BlobDataModelLoaderFactory()
        )

        registry.prepend(
            SqlImageData::class.java,
            ByteBuffer::class.java,
            SqlImageModelLoaderFactory(ImageRepository(context))
        )
    }
}