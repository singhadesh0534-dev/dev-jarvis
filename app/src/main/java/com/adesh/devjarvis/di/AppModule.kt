package com.adesh.devjarvis.di

import com.adesh.devjarvis.voice.AndroidTts
import com.adesh.devjarvis.voice.SpeechOutput
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS) // streaming: max gap between chunks
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        // Deliberately no logging interceptor: it would print API keys.
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {
    /** TTS pluggable hai: naya engine banao, yahan bind badlo. */
    @Binds
    @Singleton
    abstract fun speechOutput(impl: AndroidTts): SpeechOutput
}
