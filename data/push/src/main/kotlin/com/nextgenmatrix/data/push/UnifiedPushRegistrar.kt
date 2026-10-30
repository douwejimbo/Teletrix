package com.nextgenmatrix.data.push

import javax.inject.Inject
import javax.inject.Singleton

/** Kept so existing injection sites compile. Registration lives in [PushCoordinator]. */
@Singleton
class UnifiedPushRegistrar @Inject constructor(
    private val coordinator: PushCoordinator,
) {
    fun registerDefaultDistributor() {
        coordinator
    }
}
