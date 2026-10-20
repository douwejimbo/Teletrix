package com.nextgenmatrix.data.db

import android.content.Context
import androidx.room.Room
import com.nextgenmatrix.data.db.dao.ChatDraftDao
import com.nextgenmatrix.data.db.dao.ChatScrollStateDao
import com.nextgenmatrix.data.db.dao.OutboxDao
import com.nextgenmatrix.data.db.dao.RoomPreviewDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.SecureRandom
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        System.loadLibrary("sqlcipher")
        val keyFile = File(context.filesDir, "nextgen_matrix_db.key")
        if (!keyFile.exists()) {
            keyFile.parentFile?.mkdirs()
            keyFile.writeBytes(SecureRandom().generateSeed(32))
        }
        val passphrase = keyFile.readBytes().copyOf(32)
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(context, AppDatabase::class.java, "nextgen_matrix.db")
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideChatDraftDao(db: AppDatabase): ChatDraftDao = db.chatDraftDao()

    @Provides
    fun provideChatScrollStateDao(db: AppDatabase): ChatScrollStateDao = db.chatScrollStateDao()

    @Provides
    fun provideOutboxDao(db: AppDatabase): OutboxDao = db.outboxDao()

    @Provides
    fun provideRoomPreviewDao(db: AppDatabase): RoomPreviewDao = db.roomPreviewDao()
}
