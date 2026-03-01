package com.livestream.youtube.overlay

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.livestream.youtube.model.overlay.*

/**
 * Manages persistence of overlay configurations using SharedPreferences.
 * Uses Gson for JSON serialization/deserialization.
 */
class OverlayStorageManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(OverlayElement::class.java, OverlayElementJsonAdapter())
        .create()

    /**
     * Saves an overlay configuration.
     * @return Pair with success status and error message if failed
     */
    fun saveConfiguration(config: OverlayConfiguration): Pair<Boolean, String?> {
        return try {
            val configs = getAllConfigurations().toMutableList()
            val existingIndex = configs.indexOfFirst { it.id == config.id }
            if (existingIndex >= 0) {
                configs[existingIndex] = config
            } else {
                configs.add(config)
            }
            val json = gson.toJson(configs)
            prefs.edit().putString(KEY_CONFIGS, json).apply()
            Pair(true, null)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message)
        }
    }

    /**
     * Saves an overlay configuration (legacy method for compatibility).
     */
    fun saveConfigurationLegacy(config: OverlayConfiguration): Boolean {
        return saveConfiguration(config).first
    }

    /**
     * Loads a configuration by ID.
     */
    fun loadConfiguration(id: String): OverlayConfiguration? {
        return getAllConfigurations().find { it.id == id }
    }

    /**
     * Loads the active configuration.
     */
    fun loadActiveConfiguration(): OverlayConfiguration? {
        val activeId = prefs.getString(KEY_ACTIVE_CONFIG, null)
        return if (activeId != null) {
            loadConfiguration(activeId)
        } else {
            getAllConfigurations().firstOrNull()
        }
    }

    /**
     * Saves the active configuration ID.
     */
    fun setActiveConfiguration(id: String) {
        prefs.edit().putString(KEY_ACTIVE_CONFIG, id).apply()
    }

    /**
     * Returns the active configuration ID.
     */
    fun getActiveConfigurationId(): String? {
        return prefs.getString(KEY_ACTIVE_CONFIG, null)
    }

    /**
     * Gets all saved configurations.
     */
    fun getAllConfigurations(): List<OverlayConfiguration> {
        return try {
            val json = prefs.getString(KEY_CONFIGS, "[]")
            val type = object : TypeToken<List<OverlayConfiguration>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Deletes a configuration.
     */
    fun deleteConfiguration(id: String): Boolean {
        return try {
            val configs = getAllConfigurations().filter { it.id != id }
            val json = gson.toJson(configs)
            prefs.edit().putString(KEY_CONFIGS, json).apply()

            // If deleted was active, clear active
            if (getActiveConfigurationId() == id) {
                prefs.edit().remove(KEY_ACTIVE_CONFIG).apply()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Exports a configuration to JSON string.
     */
    fun exportConfiguration(config: OverlayConfiguration): String {
        return gson.toJson(config)
    }

    /**
     * Imports a configuration from JSON string.
     */
    fun importConfiguration(json: String): OverlayConfiguration? {
        return try {
            gson.fromJson(json, OverlayConfiguration::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Creates a default empty configuration.
     */
    fun createDefaultConfiguration(): OverlayConfiguration {
        val defaultConfig = OverlayConfiguration(
            id = java.util.UUID.randomUUID().toString(),
            name = "Default",
            isDefault = true
        )
        saveConfiguration(defaultConfig)
        return defaultConfig
    }

    /**
     * Exports all configurations as a single JSON string.
     */
    fun exportAllConfigurations(): String {
        return gson.toJson(getAllConfigurations())
    }

    /**
     * Imports configurations from JSON string (replace all).
     */
    fun importAllConfigurations(json: String): Boolean {
        return try {
            val type = object : TypeToken<List<OverlayConfiguration>>() {}.type
            val configs: List<OverlayConfiguration> = gson.fromJson(json, type)
            prefs.edit().putString(KEY_CONFIGS, gson.toJson(configs)).apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    companion object {
        private const val PREFS_NAME = "overlay_settings"
        private const val KEY_CONFIGS = "overlay_configurations"
        private const val KEY_ACTIVE_CONFIG = "active_overlay_config"
    }
}
