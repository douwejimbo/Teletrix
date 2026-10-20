package com.nextgenmatrix.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

data class AppDispatchers(
    val io: CoroutineDispatcher = Dispatchers.IO,
    val main: CoroutineDispatcher = Dispatchers.Main,
    val default: CoroutineDispatcher = Dispatchers.Default,
)

sealed class NgResult<out T> {
    data class Ok<T>(val value: T) : NgResult<T>()
    data class Err(val throwable: Throwable) : NgResult<Nothing>()
}

inline fun <T> runNgCatching(block: () -> T): NgResult<T> =
    try {
        NgResult.Ok(block())
    } catch (t: Throwable) {
        NgResult.Err(t)
    }
