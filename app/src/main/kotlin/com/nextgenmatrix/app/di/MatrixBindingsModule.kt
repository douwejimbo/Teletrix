package com.nextgenmatrix.app.di

import com.nextgenmatrix.matrix.DefaultMatrixRepository
import com.nextgenmatrix.matrix.MatrixRepository
import com.nextgenmatrix.matrix.session.EncryptedMatrixSessionStore
import com.nextgenmatrix.matrix.session.MatrixSessionStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MatrixBindingsModule {
    @Binds
    @Singleton
    abstract fun bindMatrixRepository(impl: DefaultMatrixRepository): MatrixRepository

    @Binds
    @Singleton
    abstract fun bindSessionStore(impl: EncryptedMatrixSessionStore): MatrixSessionStore
}
