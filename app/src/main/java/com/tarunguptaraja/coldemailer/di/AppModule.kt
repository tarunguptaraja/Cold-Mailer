package com.tarunguptaraja.coldemailer.di

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tarunguptaraja.coldemailer.BuildConfig
import com.tarunguptaraja.coldemailer.GeminiManager
import com.tarunguptaraja.coldemailer.data.repository.EmailRepositoryImpl
import com.tarunguptaraja.coldemailer.data.repository.ProfileRepositoryImpl
import com.tarunguptaraja.coldemailer.data.repository.ResumeRepositoryImpl
import com.tarunguptaraja.coldemailer.domain.repository.EmailRepository
import com.tarunguptaraja.coldemailer.domain.repository.ProfileRepository
import com.tarunguptaraja.coldemailer.domain.repository.ResumeRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProfileRepository(
        profileRepositoryImpl: ProfileRepositoryImpl
    ): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindEmailRepository(
        emailRepositoryImpl: EmailRepositoryImpl
    ): EmailRepository

    @Binds
    @Singleton
    abstract fun bindResumeRepository(
        resumeRepositoryImpl: ResumeRepositoryImpl
    ): ResumeRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGeminiApiKey(): String {
        return BuildConfig.GEMINI_API_KEY
    }

    @Provides
    @Singleton
    fun provideGeminiManager(apiKey: String, crashlytics: FirebaseCrashlytics): GeminiManager {
        return GeminiManager(apiKey, crashlytics)
    }

    @Provides
    @Singleton
    fun provideFirebaseAnalytics(@ApplicationContext context: Context): FirebaseAnalytics {
        return FirebaseAnalytics.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideFirebaseCrashlytics(): FirebaseCrashlytics {
        return FirebaseCrashlytics.getInstance()
    }
}
