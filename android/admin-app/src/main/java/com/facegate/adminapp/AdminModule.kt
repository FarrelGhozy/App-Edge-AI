package com.facegate.adminapp

import android.content.Context
import com.facegate.core.di.ApiBaseUrl
import com.facegate.adminapp.BuildConfig
import com.facegate.core.face.FaceEmbedderProvider
import com.facegate.core.face.OnnxFaceEmbedder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AdminModule {
    @Provides
    @Singleton
    @ApiBaseUrl
    fun provideApiBaseUrl(): String = BuildConfig.API_BASE_URL + "/"

    @Provides
    @Singleton
    fun provideOnnxFaceEmbedder(@ApplicationContext context: Context): OnnxFaceEmbedder {
        return OnnxFaceEmbedder(context)
    }

    @Provides
    @Singleton
    fun provideFaceEmbedderProvider(embedder: OnnxFaceEmbedder): FaceEmbedderProvider {
        return embedder
    }
}
