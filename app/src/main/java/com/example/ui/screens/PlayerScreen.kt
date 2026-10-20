package com.example.ui.screens

import android.app.Activity
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.example.data.util.maskPasswordInUrl
import com.example.ui.components.tvFocusable
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.delay

fun formatTime(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, sec)
    } else {
        String.format("%02d:%02d", m, sec)
    }
}

data class PlayErrorInfo(
    val errorCodeName: String,
    val httpCode: Int?,
    val maskedUrl: String,
    val detailMessage: String
)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    streamUrl: String,
    title: String,
    resumeSeconds: Long = 0,
    isLive: Boolean = false,
    onProgressUpdate: (currentSeconds: Long, durationSeconds: Long) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Manejo de botón Atrás nativo de Android
    BackHandler {
        onClose()
    }

    // Mantener pantalla encendida y modo inmersivo
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val window = activity?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Media3 ExoPlayer configurado con buffers moderados y control de velocidad en vivo
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(com.example.data.api.NetworkConfig.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val livePlaybackSpeedControl = DefaultLivePlaybackSpeedControl.Builder()
            .setFallbackMinPlaybackSpeed(0.97f)
            .setFallbackMaxPlaybackSpeed(1.03f)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setLivePlaybackSpeedControl(livePlaybackSpeedControl)
            .build()
    }

    var currentPlayingUrl by remember(streamUrl) { mutableStateOf(streamUrl) }
    var hasRetriedAlternative by remember(streamUrl) { mutableStateOf(false) }
    var isReconnecting by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var errorInfo by remember { mutableStateOf<PlayErrorInfo?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isSeeking by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    val playerFocusRequester = remember { FocusRequester() }

    // Función para construir el MediaItem adecuado según tipo de stream y extensión
    fun createMediaItem(url: String): MediaItem {
        val builder = MediaItem.Builder().setUri(Uri.parse(url))
        val lower = url.lowercase()
        if (isLive) {
            if (lower.contains(".m3u8")) {
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            } else if (lower.contains(".ts")) {
                builder.setMimeType(MimeTypes.VIDEO_MP2T)
            } else {
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
        } else {
            if (lower.contains(".m3u8")) {
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            } else if (lower.contains(".mpd")) {
                builder.setMimeType(MimeTypes.APPLICATION_MPD)
            }
        }
        return builder.build()
    }

    fun startPlayback(targetUrl: String) {
        errorInfo = null
        isLoading = true
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        val mediaItem = createMediaItem(targetUrl)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (resumeSeconds > 0 && !isLive) {
            exoPlayer.seekTo(resumeSeconds * 1000)
        }
        exoPlayer.playWhenReady = true
    }

    // Inicializar listener de reproducción
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> isLoading = true
                    Player.STATE_READY -> {
                        isLoading = false
                        isReconnecting = false
                        durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    }
                    Player.STATE_ENDED -> {
                        isLoading = false
                        onProgressUpdate(durationMs / 1000, durationMs / 1000)
                    }
                    Player.STATE_IDLE -> Unit
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                // Reintento automático para TV en vivo si falla el primer formato
                if (isLive && !hasRetriedAlternative) {
                    hasRetriedAlternative = true
                    isReconnecting = true
                    isLoading = true

                    val nextUrl = if (currentPlayingUrl.contains(".m3u8", ignoreCase = true)) {
                        currentPlayingUrl.replace(Regex("""\.m3u8""", RegexOption.IGNORE_CASE), ".ts")
                    } else {
                        currentPlayingUrl.replace(Regex("""\.ts""", RegexOption.IGNORE_CASE), ".m3u8")
                    }
                    currentPlayingUrl = nextUrl
                    startPlayback(nextUrl)
                    return
                }

                // Si no es live o ya se agotó el reintento, extraer diagnóstico
                isLoading = false
                isReconnecting = false

                var httpCode: Int? = null
                var cause: Throwable? = error.cause
                while (cause != null) {
                    if (cause is HttpDataSource.InvalidResponseCodeException) {
                        httpCode = cause.responseCode
                        break
                    }
                    cause = cause.cause
                }

                val codeName = try {
                    PlaybackException.getErrorCodeName(error.errorCode)
                } catch (_: Exception) {
                    error.errorCodeName
                }

                errorInfo = PlayErrorInfo(
                    errorCodeName = codeName,
                    httpCode = httpCode,
                    maskedUrl = maskPasswordInUrl(currentPlayingUrl),
                    detailMessage = error.message ?: "Sin mensaje detallado del servidor."
                )
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            if (!isLive && exoPlayer.currentPosition > 0) {
                val dur = exoPlayer.duration.coerceAtLeast(0L)
                onProgressUpdate(exoPlayer.currentPosition / 1000, dur / 1000)
            }
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    // Comenzar reproducción al iniciar o cambiar streamUrl
    LaunchedEffect(streamUrl) {
        currentPlayingUrl = streamUrl
        hasRetriedAlternative = false
        isReconnecting = false
        startPlayback(streamUrl)
    }

    // Solicitar foco inicial para control D-Pad
    LaunchedEffect(Unit) {
        playerFocusRequester.requestFocus()
    }

    // Ticker para posición y duración
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying) {
                val current = exoPlayer.currentPosition
                val dur = exoPlayer.duration.coerceAtLeast(0L)
                currentPositionMs = current
                durationMs = dur

                if (!isLive && current > 0) {
                    onProgressUpdate(current / 1000, dur / 1000)
                }
            }
            delay(1000)
        }
    }

    // Auto-ocultar controles tras 4 segundos
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(4000)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false

                when (keyEvent.key) {
                    // Reproducir / Pausar con OK, Enter o DPAD_CENTER
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        controlsVisible = true
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                        true
                    }

                    // Adelantar y retroceder 10 segundos en VOD
                    Key.DirectionLeft -> {
                        if (!isLive) {
                            controlsVisible = true
                            val target = (exoPlayer.currentPosition - 10000).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            true
                        } else false
                    }

                    Key.DirectionRight -> {
                        if (!isLive) {
                            controlsVisible = true
                            val target = (exoPlayer.currentPosition + 10000).coerceAtMost(durationMs)
                            exoPlayer.seekTo(target)
                            true
                        } else false
                    }

                    // Arriba y Abajo muestran los controles
                    Key.DirectionUp, Key.DirectionDown -> {
                        controlsVisible = true
                        true
                    }

                    // Teclas multimedia estándar de controles de TV
                    Key.MediaPlay -> {
                        controlsVisible = true
                        exoPlayer.play()
                        true
                    }

                    Key.MediaPause -> {
                        controlsVisible = true
                        exoPlayer.pause()
                        true
                    }

                    Key.MediaPlayPause -> {
                        controlsVisible = true
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        true
                    }

                    Key.MediaFastForward, Key.MediaSkipForward, Key.MediaStepForward -> {
                        if (!isLive) {
                            controlsVisible = true
                            val target = (exoPlayer.currentPosition + 10000).coerceAtMost(durationMs)
                            exoPlayer.seekTo(target)
                            true
                        } else false
                    }

                    Key.MediaRewind, Key.MediaSkipBackward, Key.MediaStepBackward -> {
                        if (!isLive) {
                            controlsVisible = true
                            val target = (exoPlayer.currentPosition - 10000).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            true
                        } else false
                    }

                    Key.MediaStop -> {
                        onClose()
                        true
                    }

                    Key.Back, Key.Escape -> {
                        onClose()
                        true
                    }

                    else -> false
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                controlsVisible = !controlsVisible
            }
            .testTag("player_screen")
    ) {
        // PlayerView de Media3
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Spinner de carga y mensaje de reconexión
        if (isLoading) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = GoldPrimary,
                    modifier = Modifier.size(54.dp),
                    strokeWidth = 3.5.dp
                )
                if (isReconnecting) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Reconectando con formato alternativo...",
                        color = GoldPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Mensaje de error útil, selectable y en español con código Media3, HTTP y URL enmascarada
        val currentError = errorInfo
        if (currentError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE60A0D12)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(24.dp)
                ) {
                    Text(
                        text = "No se pudo reproducir este contenido",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E252F))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Código Media3: ${currentError.errorCodeName}",
                                color = GoldPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (currentError.httpCode != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Código HTTP: ${currentError.httpCode}",
                                    color = Color(0xFFFF8E72),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "URL: ${currentError.maskedUrl}",
                                color = Color(0xFFD6DFE8),
                                fontSize = 13.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Detalle: ${currentError.detailMessage}",
                                color = TextMuted,
                                fontSize = 12.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Botón Reintentar con D-Pad focusable
                        Box(
                            modifier = Modifier
                                .tvFocusable(
                                    shape = RoundedCornerShape(8.dp),
                                    scaleOnFocus = 1.05f,
                                    onClick = {
                                        hasRetriedAlternative = false
                                        startPlayback(currentPlayingUrl)
                                    }
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .background(GoldPrimary)
                                .padding(horizontal = 24.dp, vertical = 11.dp)
                                .testTag("player_retry_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Reintentar",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // Botón Volver con D-Pad focusable
                        Box(
                            modifier = Modifier
                                .tvFocusable(
                                    shape = RoundedCornerShape(8.dp),
                                    scaleOnFocus = 1.05f,
                                    onClick = onClose
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF3E4856))
                                .padding(horizontal = 24.dp, vertical = 11.dp)
                                .testTag("player_close_error_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cerrar",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Controles superpuestos
        AnimatedVisibility(
            visible = controlsVisible && errorInfo == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
            ) {
                // Barra superior de controles: botón atrás y título
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .tvFocusable(
                                shape = CircleShape,
                                scaleOnFocus = 1.1f,
                                onClick = onClose
                            )
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0x80000000))
                            .testTag("player_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                // Botones centrales: Rewind, Play/Pause, Fast Forward
                if (!isLive) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .tvFocusable(
                                    shape = CircleShape,
                                    scaleOnFocus = 1.15f,
                                    onClick = {
                                        val target = (exoPlayer.currentPosition - 10000).coerceAtLeast(0L)
                                        exoPlayer.seekTo(target)
                                    }
                                )
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color(0x80000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Retroceder 10s",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .tvFocusable(
                                    shape = CircleShape,
                                    scaleOnFocus = 1.15f,
                                    onClick = {
                                        if (exoPlayer.isPlaying) {
                                            exoPlayer.pause()
                                        } else {
                                            exoPlayer.play()
                                        }
                                    }
                                )
                                .size(66.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                tint = Color.Black,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .tvFocusable(
                                    shape = CircleShape,
                                    scaleOnFocus = 1.15f,
                                    onClick = {
                                        val target = (exoPlayer.currentPosition + 10000).coerceAtMost(durationMs)
                                        exoPlayer.seekTo(target)
                                    }
                                )
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color(0x80000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Adelantar 10s",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                // Barra inferior: Slider de tiempo y duración
                if (!isLive) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 28.dp, vertical = 20.dp)
                    ) {
                        val posSec = if (isSeeking) sliderValue.toLong() else currentPositionMs / 1000
                        val durSec = durationMs / 1000

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(posSec),
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = formatTime(durSec),
                                color = TextMuted,
                                fontSize = 13.5.sp
                            )
                        }

                        Slider(
                            value = if (isSeeking) sliderValue else currentPositionMs.toFloat() / 1000f,
                            onValueChange = {
                                isSeeking = true
                                sliderValue = it
                            },
                            onValueChangeFinished = {
                                isSeeking = false
                                exoPlayer.seekTo((sliderValue * 1000).toLong())
                            },
                            valueRange = 0f..durSec.coerceAtLeast(1).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = GoldPrimary,
                                activeTrackColor = GoldPrimary,
                                inactiveTrackColor = Color(0x66FFFFFF)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("player_seek_bar")
                        )
                    }
                }
            }
        }
    }
}
