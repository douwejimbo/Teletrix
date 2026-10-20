package com.nextgenmatrix.data.media

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaBindingsModule {
    @Binds
    @Singleton
    abstract fun bindChatMusicPlayer(impl: Media3ChatMusicPlayer): ChatMusicPlayer
}
