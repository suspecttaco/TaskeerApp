package com.simposio.taskeer.ui.permission

object NotificationPermissionPolicy {
    const val MIN_SDK_WITH_PERMISSION: Int = 33

    fun isRequired(sdkInt: Int): Boolean = sdkInt >= MIN_SDK_WITH_PERMISSION

    fun shouldRequest(
        somethingToNotify: Boolean,
        granted: Boolean,
        sdkInt: Int,
        alreadyAskedThisTransition: Boolean,
    ): Boolean = somethingToNotify && !granted && isRequired(sdkInt) && !alreadyAskedThisTransition
}
