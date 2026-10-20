package com.nextgenmatrix.data.media

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface DownloadManager {
    fun enqueue(url: String, destinationFileName: String): String
}

@Singleton
class DefaultDownloadManager @Inject constructor() : DownloadManager {
    override fun enqueue(url: String, destinationFileName: String): String =
        UUID.randomUUID().toString()
}
