package com.retro.grooveplayer.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class StorageManager(context: Context) {
    private val prefs = context.getSharedPreferences("groove_player_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getFavourites(): List<String> {
        val json = prefs.getString("favourites", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveFavourites(favourites: List<String>) {
        prefs.edit().putString("favourites", gson.toJson(favourites)).apply()
    }

    fun getPlaylists(): List<Playlist> {
        val json = prefs.getString("playlists", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Playlist>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePlaylists(playlists: List<Playlist>) {
        prefs.edit().putString("playlists", gson.toJson(playlists)).apply()
    }

    fun getCachedSongs(): List<Song> {
        val json = prefs.getString("cached_songs", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Song>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCachedSongs(songs: List<Song>) {
        prefs.edit().putString("cached_songs", gson.toJson(songs)).apply()
    }

    fun getAccentColor(): String {
        return prefs.getString("accent_color", "#7C4DFF") ?: "#7C4DFF"
    }

    /**
     * One-shot migration to the redesigned theme: existing installs carry an accent and
     * theme mode from the old palette, so move them onto the new light-first defaults once.
     */
    fun needsThemeMigration(): Boolean = !prefs.getBoolean("theme_v2_migrated", false)

    fun markThemeMigrated() {
        prefs.edit().putBoolean("theme_v2_migrated", true).apply()
    }

    fun saveAccentColor(colorHex: String) {
        prefs.edit().putString("accent_color", colorHex).apply()
    }

    fun getEqPreset(): String {
        return prefs.getString("eq_preset", "Flat") ?: "Flat"
    }

    fun saveEqPreset(preset: String) {
        prefs.edit().putString("eq_preset", preset).apply()
    }

    fun getSpeed(): Float {
        return prefs.getFloat("speed", 1.0f)
    }

    fun saveSpeed(speed: Float) {
        prefs.edit().putFloat("speed", speed).apply()
    }

    fun getPitch(): Int = prefs.getInt("pitch", 0)
    fun savePitch(value: Int) = prefs.edit().putInt("pitch", value).apply()

    fun getReverb(): Int = prefs.getInt("reverb", 0)
    fun saveReverb(value: Int) = prefs.edit().putInt("reverb", value).apply()

    fun getBassLevel(): Int = prefs.getInt("bass_level", 0)
    fun saveBassLevel(value: Int) = prefs.edit().putInt("bass_level", value).apply()

    fun getSortBy(): String = prefs.getString("sort_by", "Title") ?: "Title"
    fun saveSortBy(value: String) = prefs.edit().putString("sort_by", value).apply()

    fun getCustomEqBands(): List<Int> {
        val json = prefs.getString("custom_eq_bands", null) ?: return List(10) { 0 }
        return try {
            val type = object : TypeToken<List<Int>>() {}.type
            gson.fromJson<List<Int>>(json, type) ?: List(10) { 0 }
        } catch (e: Exception) {
            List(10) { 0 }
        }
    }

    fun saveCustomEqBands(bands: List<Int>) {
        prefs.edit().putString("custom_eq_bands", gson.toJson(bands)).apply()
    }

    fun getGapless(): Boolean = prefs.getBoolean("gapless", true)
    fun saveGapless(value: Boolean) = prefs.edit().putBoolean("gapless", value).apply()

    fun getCrossfade(): Boolean = prefs.getBoolean("crossfade", false)
    fun saveCrossfade(value: Boolean) = prefs.edit().putBoolean("crossfade", value).apply()

    fun getBassBoost(): Boolean = prefs.getBoolean("bass_boost", false)
    fun saveBassBoost(value: Boolean) = prefs.edit().putBoolean("bass_boost", value).apply()

    fun getSurround(): Boolean = prefs.getBoolean("surround", false)
    fun saveSurround(value: Boolean) = prefs.edit().putBoolean("surround", value).apply()

    fun getVisualizerEnabled(): Boolean = prefs.getBoolean("visualizer_enabled", true)
    fun saveVisualizerEnabled(value: Boolean) = prefs.edit().putBoolean("visualizer_enabled", value).apply()

    fun getSpinningDiscEnabled(): Boolean = prefs.getBoolean("spinning_disc_enabled", true)
    fun saveSpinningDiscEnabled(value: Boolean) = prefs.edit().putBoolean("spinning_disc_enabled", value).apply()

    fun getNotificationEnabled(): Boolean = prefs.getBoolean("notification_enabled", true)
    fun saveNotificationEnabled(value: Boolean) = prefs.edit().putBoolean("notification_enabled", value).apply()

    fun getLockScreenEnabled(): Boolean = prefs.getBoolean("lock_screen_enabled", true)
    fun saveLockScreenEnabled(value: Boolean) = prefs.edit().putBoolean("lock_screen_enabled", value).apply()

    fun getRecentlyPlayed(): List<String> {
        val json = prefs.getString("recently_played", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveRecentlyPlayed(list: List<String>) {
        prefs.edit().putString("recently_played", gson.toJson(list)).apply()
    }

    fun getMostPlayed(): Map<String, Int> {
        val json = prefs.getString("most_played", null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Int>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveMostPlayed(map: Map<String, Int>) {
        prefs.edit().putString("most_played", gson.toJson(map)).apply()
    }

    fun getMinDuration(): Int {
        return prefs.getInt("min_duration", 30)
    }

    fun saveMinDuration(seconds: Int) {
        prefs.edit().putInt("min_duration", seconds).apply()
    }

    /** Session count, used to hold ads and the review prompt back on early launches. */
    fun getSessionCount(): Int = prefs.getInt("session_count", 0)
    fun incrementSessionCount(): Int {
        val next = getSessionCount() + 1
        prefs.edit().putInt("session_count", next).apply()
        return next
    }

    fun hasRequestedReview(): Boolean = prefs.getBoolean("review_requested", false)
    fun markReviewRequested() = prefs.edit().putBoolean("review_requested", true).apply()

    fun getVocalMode(): String = prefs.getString("vocal_mode", "OFF") ?: "OFF"
    fun saveVocalMode(mode: String) = prefs.edit().putString("vocal_mode", mode).apply()

    fun getThemeMode(): String {
        return prefs.getString("theme_mode", "light") ?: "light"
    }

    fun saveThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
