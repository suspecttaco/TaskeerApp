package com.simposio.taskeer.ui.permission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simposio.taskeer.data.repository.PreferencesRepository
import com.simposio.taskeer.data.repository.ReminderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationPermissionViewModel(
    preferences: PreferencesRepository,
    reminders: ReminderRepository,
    private val isGranted: () -> Boolean,
    private val sdkInt: Int,
) : ViewModel() {

    private val granted = MutableStateFlow(isGranted())
    private val askedThisTransition = MutableStateFlow(false)
    private val answeredThisTransition = MutableStateFlow(false)

    val somethingToNotify: StateFlow<Boolean> = combine(
        preferences.deadlineReminders,
        preferences.digest,
        reminders.observePending(),
    ) { deadline, digest, pending -> deadline.enabled || digest.enabled || pending.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val blocked: StateFlow<Boolean> = combine(
        somethingToNotify,
        granted,
        answeredThisTransition,
    ) { active, isGranted, answered ->
        NotificationPermissionPolicy.isBlocked(active, isGranted, sdkInt, answered)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            somethingToNotify.collect { active ->
                if (!active) {
                    askedThisTransition.value = false
                    answeredThisTransition.value = false
                }
            }
        }
    }

    fun refreshGranted() {
        granted.value = isGranted()
    }

    fun takeRequest(): Boolean {
        val should = NotificationPermissionPolicy.shouldRequest(
            somethingToNotify = somethingToNotify.value,
            granted = granted.value,
            sdkInt = sdkInt,
            alreadyAskedThisTransition = askedThisTransition.value,
        )
        if (should) {
            askedThisTransition.value = true
        }
        return should
    }

    fun onPermissionResult(wasGranted: Boolean) {
        granted.value = wasGranted || isGranted()
        answeredThisTransition.value = true
    }
}
