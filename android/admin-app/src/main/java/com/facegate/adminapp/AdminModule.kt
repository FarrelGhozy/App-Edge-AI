package com.facegate.adminapp

import com.facegate.core.di.ApiBaseUrl
import com.facegate.adminapp.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AdminModule {
    @Provides
    @Singleton
    @ApiBaseUrl
    fun provideApiBaseUrl(): String = BuildConfig.API_BASE_URL + "/"
}
