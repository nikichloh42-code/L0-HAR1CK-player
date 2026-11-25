package com.custom.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "player_prefs")

enum class AppThemeMode {
    DARK, LIGHT, DYNAMIC_COVER
}

enum class SortOption {
    TITLE_ASC, TITLE_DESC, ARTIST, DATE_ADDED, DURATION, PLAY_COUNT
}

data class PlayerSettings(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val uiScale: Float = 1.0f,
    val wallpaperUri: String? = null,
    val wallpaperDim: Float = 0.45f,
    val wallpaperBlur: Float = 0f,
    val wallpaperAlpha: Float = 0.85f,
    val languageCode: String = "ru",
    val sortOption: SortOption = SortOption.TITLE_ASC,
    val equalizerEnabled: Boolean = false,
    val equalizerPresetIndex: Int = 0,
    val equalizerBands: List<Int> = emptyList()
)

class PreferencesManager(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val UI_SCALE = floatPreferencesKey("ui_scale")
        val WALLPAPER_URI = stringPreferencesKey("wallpaper_uri")
        val WALLPAPER_DIM = floatPreferencesKey("wallpaper_dim")
        val WALLPAPER_BLUR = floatPreferencesKey("wallpaper_blur")
        val WALLPAPER_ALPHA = floatPreferencesKey("wallpaper_alpha")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_BANDS = stringPreferencesKey("eq_bands")
    }

    val settingsFlow: Flow<PlayerSettings> = context.dataStore.data.map { prefs ->
        val theme = runCatching {
            AppThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: AppThemeMode.DARK.name)
        }.getOrDefault(AppThemeMode.DARK)

        val sort = runCatching {
            SortOption.valueOf(prefs[Keys.SORT_OPTION] ?: SortOption.TITLE_ASC.name)
        }.getOrDefault(SortOption.TITLE_ASC)

        val bands = prefs[Keys.EQ_BANDS]
            ?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?: emptyList()

        PlayerSettings(
            themeMode = theme,
            uiScale = (prefs[Keys.UI_SCALE] ?: 1.0f).coerceIn(0.85f, 1.25f),
            wallpaperUri = prefs[Keys.WALLPAPER_URI],
            wallpaperDim = (prefs[Keys.WALLPAPER_DIM] ?: 0.45f).coerceIn(0f, 0.9f),
            wallpaperBlur = (prefs[Keys.WALLPAPER_BLUR] ?: 0f).coerceIn(0f, 25f),
            wallpaperAlpha = (prefs[Keys.WALLPAPER_ALPHA] ?: 0.85f).coerceIn(0.1f, 1f),
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: "ru",
            sortOption = sort,
            equalizerEnabled = prefs[Keys.EQ_ENABLED] ?: false,
            equalizerPresetIndex = prefs[Keys.EQ_PRESET]?.toIntOrNull() ?: 0,
            equalizerBands = bands
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setUiScale(scale: Float) {
        context.dataStore.edit { it[Keys.UI_SCALE] = scale.coerceIn(0.85f, 1.25f) }
    }

    suspend fun setWallpaper(uri: String?) {
        context.dataStore.edit {
            if (uri == null) it.remove(Keys.WALLPAPER_URI) else it[Keys.WALLPAPER_URI] = uri
        }
    }

    suspend fun setWallpaperDim(dim: Float) {
        context.dataStore.edit { it[Keys.WALLPAPER_DIM] = dim.coerceIn(0f, 0.9f) }
    }

    suspend fun setWallpaperBlur(blur: Float) {
        context.dataStore.edit { it[Keys.WALLPAPER_BLUR] = blur.coerceIn(0f, 25f) }
    }

    suspend fun setWallpaperAlpha(alpha: Float) {
        context.dataStore.edit { it[Keys.WALLPAPER_ALPHA] = alpha.coerceIn(0.1f, 1f) }
    }

    suspend fun setLanguageCode(lang: String) {
        context.dataStore.edit { it[Keys.LANGUAGE_CODE] = lang }
    }

    suspend fun setSortOption(option: SortOption) {
        context.dataStore.edit { it[Keys.SORT_OPTION] = option.name }
    }

    suspend fun setEqualizerState(enabled: Boolean, presetIndex: Int, bands: List<Int>) {
        context.dataStore.edit {
            it[Keys.EQ_ENABLED] = enabled
            it[Keys.EQ_PRESET] = presetIndex.toString()
            it[Keys.EQ_BANDS] = bands.joinToString(",")
        }
    }
}
