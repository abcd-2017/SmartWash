package com.smartwash.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartwash.App
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

object SharePreferenceUtils {
    private val PREF_NAME = "SmartWashAndroid"

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(PREF_NAME)
    private val dataStore = App.instance.dataStore

    suspend fun <T> saveData(key: String, value: T) {
        when (value) {
            is Int -> putIntData(key, value)
            is Long -> putLongData(key, value)
            is Double -> putDoubleData(key, value)
            is Float -> putFloatData(key, value)
            is String -> putStringData(key, value)
            is Boolean -> putBooleanData(key, value)
            else -> throw IllegalAccessException("This type cannot be saved to the Data Store")
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> getData(key: String, defaultValue: T): T {
        return when (defaultValue) {
            is Int -> getIntData(key, defaultValue) as T
            is Long -> getLongData(key, defaultValue) as T
            is Double -> getDoubleData(key, defaultValue) as T
            is Float -> getFloatData(key, defaultValue) as T
            is String -> getStringData(key, defaultValue) as T
            is Boolean -> getBooleanData(key, defaultValue) as T
            else -> throw IllegalAccessException("This type cannot be saved to the Data Store")
        }
    }

    fun saveDataBlocking(key: String, value: String) = runBlocking {
        saveData(key, value)
    }

    fun getDataBlocking(key: String, defaultValue: String): String = runBlocking {
        getData(key, defaultValue)
    }

    private suspend fun putIntData(key: String, value: Int) = dataStore.edit {
        it[intPreferencesKey(key)] = value
    }

    private suspend fun putLongData(key: String, value: Long) = dataStore.edit {
        it[longPreferencesKey(key)] = value
    }

    private suspend fun putDoubleData(key: String, value: Double) = dataStore.edit {
        it[doublePreferencesKey(key)] = value
    }

    private suspend fun putStringData(key: String, value: String) = dataStore.edit {
        it[stringPreferencesKey(key)] = value
    }

    private suspend fun putFloatData(key: String, value: Float) = dataStore.edit {
        it[floatPreferencesKey(key)] = value
    }

    private suspend fun putBooleanData(key: String, value: Boolean) = dataStore.edit {
        it[booleanPreferencesKey(key)] = value
    }

    private suspend fun getIntData(key: String, default: Int = 0): Int {
        return dataStore.data.map { it[intPreferencesKey(key)] ?: default }.first()
    }

    private suspend fun getLongData(key: String, default: Long = 0): Long {
        return dataStore.data.map { it[longPreferencesKey(key)] ?: default }.first()
    }

    private suspend fun getDoubleData(key: String, default: Double = 0.0): Double {
        return dataStore.data.map { it[doublePreferencesKey(key)] ?: default }.first()
    }

    private suspend fun getFloatData(key: String, default: Float = 0f): Float {
        return dataStore.data.map { it[floatPreferencesKey(key)] ?: default }.first()
    }

    private suspend fun getStringData(key: String, default: String? = null): String {
        return dataStore.data.map { it[stringPreferencesKey(key)] ?: default }.first()!!
    }

    private suspend fun getBooleanData(key: String, default: Boolean = false): Boolean {
        return dataStore.data.map { it[booleanPreferencesKey(key)] ?: default }.first()
    }
}