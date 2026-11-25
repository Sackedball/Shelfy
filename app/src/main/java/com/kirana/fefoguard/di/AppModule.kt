package com.kirana.fefoguard.di

import android.content.Context
import androidx.room.Room
import com.kirana.fefoguard.data.local.DatabaseMigrations
import com.kirana.fefoguard.data.local.FefoGuardDatabase
import com.kirana.fefoguard.data.repository.AdjustmentRepositoryImpl
import com.kirana.fefoguard.data.repository.AnalyticsRepositoryImpl
import com.kirana.fefoguard.data.repository.ProductRepositoryImpl
import com.kirana.fefoguard.data.repository.SalesRepositoryImpl
import com.kirana.fefoguard.data.repository.StoreProfileRepositoryImpl
import com.kirana.fefoguard.domain.repository.AdjustmentRepository
import com.kirana.fefoguard.domain.repository.AnalyticsRepository
import com.kirana.fefoguard.domain.repository.ProductRepository
import com.kirana.fefoguard.domain.repository.SalesRepository
import com.kirana.fefoguard.domain.repository.StoreProfileRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): FefoGuardDatabase =
        Room
            .databaseBuilder(
                context,
                FefoGuardDatabase::class.java,
                "fefoguard.db",
            ).addMigrations(*DatabaseMigrations.all)
            .build()

    @Provides
    @Singleton
    fun provideProductRepository(db: FefoGuardDatabase): ProductRepository = ProductRepositoryImpl(db)

    @Provides
    @Singleton
    fun provideSalesRepository(db: FefoGuardDatabase): SalesRepository = SalesRepositoryImpl(db)

    @Provides
    @Singleton
    fun provideAdjustmentRepository(db: FefoGuardDatabase): AdjustmentRepository = AdjustmentRepositoryImpl(db)

    @Provides
    @Singleton
    fun provideAnalyticsRepository(db: FefoGuardDatabase): AnalyticsRepository = AnalyticsRepositoryImpl(db)

    @Provides
    @Singleton
    fun provideStoreProfileRepository(db: FefoGuardDatabase): StoreProfileRepository = StoreProfileRepositoryImpl(db)
}
