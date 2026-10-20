package com.nextgenmatrix.data.push

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedPushRegistrar @Inject constructor() {
    fun registerDefaultDistributor() {
        // UnifiedPush distributor selection is user-driven; hook distributor registration here.
    }
}
