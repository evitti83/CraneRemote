package com.craneremote.di

import android.content.Context
import androidx.room.Room
import com.craneremote.data.local.*
import com.craneremote.data.repository.*
import com.craneremote.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): CraneDatabase =
        Room.databaseBuilder(ctx, CraneDatabase::class.java, "crane_remote_v2.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideDeviceDao(db: CraneDatabase)      = db.deviceDao()
    @Provides fun provideSubsystemDao(db: CraneDatabase)   = db.subsystemDao()
    @Provides fun provideFixedButtonDao(db: CraneDatabase) = db.fixedButtonDao()
    @Provides fun provideLogDao(db: CraneDatabase)         = db.commandLogDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindDeviceRepo(impl: DeviceRepositoryImpl): DeviceRepository
    @Binds @Singleton abstract fun bindSubsystemRepo(impl: SubsystemRepositoryImpl): SubsystemRepository
    @Binds @Singleton abstract fun bindButtonRepo(impl: FixedButtonRepositoryImpl): FixedButtonRepository
    @Binds @Singleton abstract fun bindLogRepo(impl: LogRepositoryImpl): LogRepository
}
