package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.RecentVideo
import com.example.data.RecentVideoRepository
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(private val repository: RecentVideoRepository) : ViewModel() {

    private var appContext: Context? = null

    private var _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentVideoUri = MutableStateFlow<String?>(null)
    val currentVideoUri: StateFlow<String?> = _currentVideoUri.asStateFlow()

    private val _currentVideoTitle = MutableStateFlow<String?>(null)
    val currentVideoTitle: StateFlow<String?> = _currentVideoTitle.asStateFlow()

    private val _isControlsVisible = MutableStateFlow(true)
    val isControlsVisible: StateFlow<Boolean> = _isControlsVisible.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    val recentVideos: StateFlow<List<RecentVideo>> = repository.allRecentVideos
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var progressJob: Job? = null
    private var autoHideJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startProgressUpdate()
                resetAutoHideTimer()
            } else {
                stopProgressUpdate()
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            val exoplayer = _player.value ?: return
            _duration.value = if (exoplayer.duration > 0) exoplayer.duration else 0L
            
            if (state == Player.STATE_READY) {
                _duration.value = exoplayer.duration
                // Update history with correct total duration
                val uri = _currentVideoUri.value
                val title = _currentVideoTitle.value
                if (uri != null && title != null) {
                    saveToHistory(uri, title, exoplayer.duration, exoplayer.currentPosition)
                }
            } else if (state == Player.STATE_ENDED) {
                _isPlaying.value = false
                _isControlsVisible.value = true
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _errorMessage.value = "Playback Error: ${error.localizedMessage ?: "Unsupported format"}"
            _isPlaying.value = false
            _isControlsVisible.value = true
        }
    }

    fun initPlayer(context: Context) {
        appContext = context.applicationContext
        if (_player.value == null) {
            val exoplayer = ExoPlayer.Builder(context.applicationContext).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
            }
            exoplayer.addListener(playerListener)
            _player.value = exoplayer
        }
    }

    fun playVideo(context: Context, uriString: String, title: String, startPosition: Long = 0L) {
        initPlayer(context)
        val exoplayer = _player.value ?: return

        _errorMessage.value = null
        _currentVideoUri.value = uriString
        _currentVideoTitle.value = title
        _isControlsVisible.value = true

        try {
            val uri = Uri.parse(uriString)
            val mediaItem = MediaItem.fromUri(uri)
            exoplayer.setMediaItem(mediaItem)
            exoplayer.seekTo(startPosition)
            exoplayer.setPlaybackSpeed(_playbackSpeed.value)
            exoplayer.prepare()
            exoplayer.play()

            saveToHistory(uriString, title, 0L, startPosition)
        } catch (e: Exception) {
            _errorMessage.value = "Failed to load video: ${e.localizedMessage}"
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        _player.value?.setPlaybackSpeed(speed)
        resetAutoHideTimer()
    }

    fun togglePlayPause() {
        val exoplayer = _player.value ?: return
        if (exoplayer.isPlaying) {
            exoplayer.pause()
            _isControlsVisible.value = true
        } else {
            exoplayer.play()
            resetAutoHideTimer()
        }
    }

    fun stop() {
        val exoplayer = _player.value ?: return
        
        // Save final position to database
        val uri = _currentVideoUri.value
        val title = _currentVideoTitle.value
        if (uri != null && title != null) {
            saveToHistory(uri, title, _duration.value, exoplayer.currentPosition)
        }

        exoplayer.stop()
        exoplayer.clearMediaItems()
        _currentVideoUri.value = null
        _currentVideoTitle.value = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        _isControlsVisible.value = true
        _errorMessage.value = null
    }

    fun seekTo(positionMs: Long) {
        val exoplayer = _player.value ?: return
        exoplayer.seekTo(positionMs)
        _currentPosition.value = positionMs
        resetAutoHideTimer()
    }

    fun toggleControlsVisibility() {
        _isControlsVisible.value = !_isControlsVisible.value
        if (_isControlsVisible.value) {
            resetAutoHideTimer()
        }
    }

    fun setControlsVisible(visible: Boolean) {
        _isControlsVisible.value = visible
        if (visible) {
            resetAutoHideTimer()
        }
    }

    private fun resetAutoHideTimer() {
        autoHideJob?.cancel()
        if (_isPlaying.value) {
            autoHideJob = viewModelScope.launch {
                delay(3000L)
                _isControlsVisible.value = false
            }
        }
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                _player.value?.let {
                    _currentPosition.value = it.currentPosition
                }
                delay(200L)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
    }

    private suspend fun extractThumbnail(context: Context, uriString: String): String? {
        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
                    val uri = Uri.parse(uriString)
                    retriever.setDataSource(context, uri)
                } else {
                    // Online Stream URL
                    retriever.setDataSource(uriString, HashMap<String, String>())
                }
                // Try to get frame at 1 second mark (1000000 microseconds)
                val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                
                if (bitmap != null) {
                    val cacheDir = File(context.cacheDir, "thumbnails")
                    if (!cacheDir.exists()) {
                        cacheDir.mkdirs()
                    }
                    val filename = "thumb_${uriString.hashCode()}.jpg"
                    val file = File(cacheDir, filename)
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    }
                    file.absolutePath
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    private fun saveToHistory(uriString: String, title: String, duration: Long, position: Long) {
        val context = appContext ?: return
        viewModelScope.launch {
            // Check if we already have a generated thumbnail for this URI in memory
            val existing = recentVideos.value.find { it.uriString == uriString }
            val thumbPath = existing?.thumbnailPath ?: extractThumbnail(context, uriString)

            repository.insertRecentVideo(
                RecentVideo(
                    uriString = uriString,
                    displayName = title,
                    durationMs = duration,
                    lastPositionMs = position,
                    thumbnailPath = thumbPath,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteRecentVideo(uriString: String) {
        viewModelScope.launch {
            repository.deleteRecentVideo(uriString)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun releasePlayer() {
        progressJob?.cancel()
        autoHideJob?.cancel()
        _player.value?.let {
            it.removeListener(playerListener)
            it.release()
        }
        _player.value = null
        _isPlaying.value = false
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}

class PlayerViewModelFactory(private val repository: RecentVideoRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PlayerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
