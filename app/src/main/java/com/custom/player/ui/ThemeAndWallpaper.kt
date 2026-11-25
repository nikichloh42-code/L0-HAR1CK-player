package com.custom.player.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.custom.player.data.AppThemeMode
import com.custom.player.data.PlayerSettings

private val DefaultDarkScheme = darkColorScheme(
    primary = Color(0xFFFF4D6D),
    secondary = Color(0xFFFF8FA3),
    background = Color(0xFF101014),
    surface = Color(0xFF18181F)
)

private val DefaultLightScheme = lightColorScheme(
    primary = Color(0xFFD90429),
    secondary = Color(0xFF8D99AE),
    background = Color(0xFFF7F7FA),
    surface = Color(0xFFFFFFFF)
)

@Composable
fun rememberDynamicColorScheme(
    artworkUri: String?,
    fallbackDark: Boolean = true
): ColorScheme {
    val context = LocalContext.current
    var dominantColor by remember(artworkUri) {
        mutableStateOf(if (fallbackDark) Color(0xFF7A1C24) else Color(0xFFC93B4B))
    }

    LaunchedEffect(artworkUri) {
        if (artworkUri.isNullOrBlank()) return@LaunchedEffect
        runCatching {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(artworkUri)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            val bitmap: Bitmap? = (result as? SuccessResult)?.drawable?.let { drawable ->
                (drawable as? BitmapDrawable)?.bitmap
            }
            if (bitmap != null) {
                val palette = Palette.from(bitmap).generate()
                val swatch = palette.vibrantSwatch
                    ?: palette.dominantSwatch
                    ?: palette.mutedSwatch
                if (swatch != null) {
                    dominantColor = Color(swatch.rgb)
                }
            }
        }
    }

    return if (fallbackDark) {
        darkColorScheme(
            primary = dominantColor,
            secondary = dominantColor.copy(alpha = 0.75f),
            background = Color(0xFF110D0E),
            surface = Color(0xFF1C1315)
        )
    } else {
        lightColorScheme(
            primary = dominantColor,
            secondary = dominantColor.copy(alpha = 0.7f),
            background = Color(0xFFFBF7F7),
            surface = Color(0xFFFFFFFF)
        )
    }
}

@Composable
fun PlayerThemeWrapper(
    settings: PlayerSettings,
    currentArtworkUri: String?,
    content: @Composable () -> Unit
) {
    val dynamicScheme = rememberDynamicColorScheme(
        artworkUri = currentArtworkUri,
        fallbackDark = true
    )

    val colorScheme = when (settings.themeMode) {
        AppThemeMode.DARK -> DefaultDarkScheme
        AppThemeMode.LIGHT -> DefaultLightScheme
        AppThemeMode.DYNAMIC_COVER -> dynamicScheme
    }

    val currentDensity = LocalDensity.current
    val scaledDensity = remember(currentDensity, settings.uiScale) {
        Density(
            density = currentDensity.density * settings.uiScale,
            fontScale = currentDensity.fontScale * settings.uiScale
        )
    }

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(colorScheme = colorScheme) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (!settings.wallpaperUri.isNullOrBlank()) {
                    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && settings.wallpaperBlur > 0f) {
                        Modifier.blur(settings.wallpaperBlur.dp)
                    } else {
                        Modifier
                    }

                    AsyncImage(
                        model = Uri.parse(settings.wallpaperUri),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(blurModifier)
                            .alpha(settings.wallpaperAlpha)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = settings.wallpaperDim))
                    )
                }

                content()
            }
        }
    }
}
