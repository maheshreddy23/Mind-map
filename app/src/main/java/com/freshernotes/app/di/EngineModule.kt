package com.freshernotes.app.di

import com.freshernotes.app.engine.Engines
import com.freshernotes.app.engine.SynthesizerProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {
    @Binds abstract fun synthesizerProvider(engines: Engines): SynthesizerProvider
}
