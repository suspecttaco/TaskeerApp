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

    // Antes de Android 13 no hay diálogo que esperar: basta con que el sistema no deje mostrar.
    fun isBlocked(
        somethingToNotify: Boolean,
        granted: Boolean,
        sdkInt: Int,
        answeredThisTransition: Boolean,
    ): Boolean = somethingToNotify && !granted && (!isRequired(sdkInt) || answeredThisTransition)
}
