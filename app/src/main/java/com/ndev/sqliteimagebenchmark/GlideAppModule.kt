package com.ndev.sqliteimagebenchmark


import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.ndev.benchmarkablelib.glide.BlobDataModelLoaderFactory
import com.ndev.benchmarkablelib.glide.SqlImageModelLoaderFactoryInputStream
import com.ndev.benchmarkablelib.model.BlobData
import com.ndev.benchmarkablelib.model.SqlImageData
import com.ndev.benchmarkablelib.repository.ImageRepository
import java.io.InputStream

@GlideModule
class BlobGlideModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {

    }
}