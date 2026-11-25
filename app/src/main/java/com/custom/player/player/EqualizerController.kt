package com.custom.player.player

import android.media.audiofx.Equalizer

data class EqBandUi(
    val index: Short,
    val centerFreqHz: Int,
    val levelMillibels: Int,
    val minLevel: Int,
    val maxLevel: Int
)

class EqualizerController {
    private var equalizer: Equalizer? = null

    fun attachToSession(audioSessionId: Int, enabled: Boolean, savedLevels: List<Int>) {
        release()
        if (audioSessionId == 0) return
        runCatching {
            val eq = Equalizer(0, audioSessionId).apply {
                this.enabled = enabled
            }
            if (savedLevels.size == eq.numberOfBands.toInt()) {
                val min = eq.bandLevelRange[0].toInt()
                val max = eq.bandLevelRange[1].toInt()
                savedLevels.forEachIndexed { idx, level ->
                    eq.setBandLevel(idx.toShort(), level.coerceIn(min, max).toShort())
                }
            }
            equalizer = eq
        }
    }

    fun setEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
    }

    fun getBands(): List<EqBandUi> {
        val eq = equalizer ?: return emptyList()
        return runCatching {
            val min = eq.bandLevelRange[0].toInt()
            val max = eq.bandLevelRange[1].toInt()
            (0 until eq.numberOfBands).map { i ->
                val band = i.toShort()
                EqBandUi(
                    index = band,
                    centerFreqHz = eq.getCenterFreq(band) / 1000,
                    levelMillibels = eq.getBandLevel(band).toInt(),
                    minLevel = min,
                    maxLevel = max
                )
            }
        }.getOrDefault(emptyList())
    }

    fun setBandLevel(bandIndex: Short, levelMillibels: Int) {
        val eq = equalizer ?: return
        runCatching {
            val min = eq.bandLevelRange[0].toInt()
            val max = eq.bandLevelRange[1].toInt()
            eq.setBandLevel(bandIndex, levelMillibels.coerceIn(min, max).toShort())
        }
    }

    fun getPresets(): List<String> {
        val eq = equalizer ?: return emptyList()
        return runCatching {
            (0 until eq.numberOfPresets).map { idx ->
                eq.getPresetName(idx.toShort())
            }
        }.getOrDefault(emptyList())
    }

    fun usePreset(presetIndex: Short): List<EqBandUi> {
        val eq = equalizer ?: return emptyList()
        runCatching {
            if (presetIndex in 0 until eq.numberOfPresets) {
                eq.usePreset(presetIndex)
            }
        }
        return getBands()
    }

    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
    }
}
