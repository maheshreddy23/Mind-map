package com.maheshreddy.mym.di

import com.maheshreddy.mym.engine.Engines
import com.maheshreddy.mym.engine.SynthesizerProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {
    @Binds abstract fun synthesizerProvider(engines: Engines): SynthesizerProvider
}
