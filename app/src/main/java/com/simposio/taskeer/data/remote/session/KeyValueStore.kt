package com.simposio.taskeer.data.remote.session

import android.content.Context

interface KeyValueStore {
    fun get(key: String): String?

    fun put(key: String, value: String)

    fun remove(key: String)
}

class SharedPreferencesKeyValueStore(context: Context, name: String = FILE_NAME) : KeyValueStore {

    private val preferences by lazy { context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE) }

    override fun get(key: String): String? = preferences.getString(key, null)

    // commit() y no apply(): la rotación del refresh token debe ser durable antes de seguir.
    override fun put(key: String, value: String) {
        check(preferences.edit().putString(key, value).commit()) { "No se pudo guardar la sesión" }
    }

    override fun remove(key: String) {
        check(preferences.edit().remove(key).commit()) { "No se pudo borrar la sesión" }
    }

    companion object {
        const val FILE_NAME = "taskeer_session"
    }
}
