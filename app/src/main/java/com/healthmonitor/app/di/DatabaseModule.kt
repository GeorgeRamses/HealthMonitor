package com.healthmonitor.app.di

import android.content.Context
import com.healthmonitor.app.data.local.HealthMonitorDatabase
import com.healthmonitor.app.data.repository.HealthMonitorRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideDatabase(
        @ApplicationContext context: Context
    ): HealthMonitorDatabase {
        return HealthMonitorDatabase.getDatabase(context)
    }

    @Singleton
    @Provides
    fun provideRepository(database: HealthMonitorDatabase): HealthMonitorRepository {
        return HealthMonitorRepository(database)
    }
}
