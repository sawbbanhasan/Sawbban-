package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import coil.compose.AsyncImage
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.RecentVideo
import com.example.data.RecentVideoDatabase
import com.example.data.RecentVideoRepository
import com.example.ui.PlayerViewModel
import com.example.ui.PlayerViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.VlcBlack
import com.example.ui.theme.VlcCharcoal
import com.example.ui.theme.VlcOrange
import com.example.ui.theme.VlcWhite
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Room Database and Repository
        val database = RecentVideoDatabase.getDatabase(this)
        val repository = RecentVideoRepository(database.recentVideoDao())
        val factory = PlayerViewModelFactory(repository)

        setContent {
            MyApplicationTheme {
                val playerViewModel: PlayerViewModel = viewModel(factory = factory)
                MainScreen(viewModel = playerViewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val currentVideoUri by viewModel.currentVideoUri.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Display playback errors reactively via SnackBar
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Long
            )
            viewModel.clearErrorMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VlcBlack)
    ) {
        if (currentVideoUri != null) {
            // Immersive Video Player
            VideoPlayerContainer(viewModel = viewModel)
        } else {
            // Video Library & Dashboard Dashboard
            LibraryDashboardScreen(viewModel = viewModel)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )
    }
}

@Composable
fun LibraryDashboardScreen(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val recentVideos by viewModel.recentVideos.collectAsStateWithLifecycle()
    
    // Configure Video Picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                // Try to persist the read permission across reboots/launches
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore failure if scheme is not content or persistable flag is missing
            }
            val displayName = getFileName(context, it) ?: "Selected Local Video"
            viewModel.playVideo(context, it.toString(), displayName)
        }
    }

    // Permission handling (checks / alerts)
    val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_VIDEO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            videoPickerLauncher.launch(arrayOf("video/*"))
        } else {
            Toast.makeText(
                context,
                "Permission denied. Cannot select local files.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = VlcBlack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val permissionCheck = ContextCompat.checkSelfPermission(context, readPermission)
                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        videoPickerLauncher.launch(arrayOf("video/*"))
                    } else {
                        permissionLauncher.launch(readPermission)
                    }
                },
                containerColor = VlcOrange,
                contentColor = Color.Black,
                modifier = Modifier
                    .padding(16.dp)
                    .testTag("file_picker_fab")
            ) {
                Icon(
                    imageVector = Icons.Filled.VideoLibrary,
                    contentDescription = "Open Video File"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Polished Header with Traffic Cone Canvas Drawing
            VlcAppHeader()

            // 2. Demo Streams & Recent Library List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // VLC Quick Play Online Streams
                item {
                    Text(
                        text = "VLC Demo Streams",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = VlcOrange,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = VlcCharcoal),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column {
                            DemoStreamItem(
                                title = "Big Buck Bunny (MP4 Stream)",
                                duration = "10:00",
                                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                                onClick = {
                                    viewModel.playVideo(context, it, "Big Buck Bunny (Stream)")
                                }
                            )
                            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                            DemoStreamItem(
                                title = "Sintel Movie (HD MP4)",
                                duration = "08:48",
                                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                                onClick = {
                                    viewModel.playVideo(context, it, "Sintel Movie (Stream)")
                                }
                            )
                            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                            DemoStreamItem(
                                title = "Tears of Steel (Sci-Fi Trailer)",
                                duration = "12:14",
                                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                                onClick = {
                                    viewModel.playVideo(context, it, "Tears of Steel (Stream)")
                                }
                            )
                        }
                    }
                }

                // Recent Playback Library Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recently Played",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = VlcOrange
                        )
                        if (recentVideos.isNotEmpty()) {
                            TextButton(
                                onClick = { viewModel.clearHistory() },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.LightGray)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear History",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear All", fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (recentVideos.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "No video history",
                                tint = Color.DarkGray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Your media library is empty.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Tap the orange folder FAB to pick a local file, or play one of the demo streams above!",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.DarkGray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                            )
                        }
                    }
                } else {
                    items(recentVideos, key = { it.uriString }) { video ->
                        RecentVideoItem(
                            video = video,
                            onPlay = {
                                viewModel.playVideo(context, video.uriString, video.displayName, video.lastPositionMs)
                            },
                            onDelete = {
                                viewModel.deleteRecentVideo(video.uriString)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VlcAppHeader() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = VlcCharcoal),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VlcConeIcon(
                modifier = Modifier
                    .size(60.dp)
                    .padding(end = 12.dp)
            )
            Column {
                Text(
                    text = "VLC Media Player",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = VlcOrange
                )
                Text(
                    text = "Android Core Playback Engine",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            }
        }
    }
}

@Composable
fun VlcConeIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Draw base of the traffic cone (black charcoal rounded base)
        val baseH = h * 0.15f
        val baseW = w * 0.95f
        val baseLeft = (w - baseW) / 2
        val baseTop = h - baseH
        val basePath = Path().apply {
            moveTo(baseLeft, h)
            lineTo(baseLeft + baseW, h)
            lineTo(baseLeft + baseW * 0.85f, baseTop)
            lineTo(baseLeft + baseW * 0.15f, baseTop)
            close()
        }
        drawPath(basePath, Color(0xFF222222))

        // 2. Draw outer orange core cone trapezoid
        val conePath = Path().apply {
            moveTo(w * 0.15f, baseTop)
            lineTo(w * 0.85f, baseTop)
            lineTo(w * 0.58f, h * 0.05f)
            lineTo(w * 0.42f, h * 0.05f)
            close()
        }
        drawPath(conePath, Color(0xFFFF8800))

        // 3. Draw middle white reflective stripe
        val whiteStripe1 = Path().apply {
            moveTo(w * 0.27f, h * 0.65f)
            lineTo(w * 0.73f, h * 0.65f)
            lineTo(w * 0.65f, h * 0.45f)
            lineTo(w * 0.35f, h * 0.45f)
            close()
        }
        drawPath(whiteStripe1, Color.White)

        // 4. Draw upper white reflective stripe
        val whiteStripe2 = Path().apply {
            moveTo(w * 0.38f, h * 0.38f)
            lineTo(w * 0.62f, h * 0.38f)
            lineTo(w * 0.57f, h * 0.24f)
            lineTo(w * 0.43f, h * 0.24f)
            close()
        }
        drawPath(whiteStripe2, Color.White)

        // 5. Draw top tip highlight
        drawCircle(
            color = Color(0xFFFFB366),
            radius = w * 0.04f,
            center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.08f)
        )
    }
}

@Composable
fun DemoStreamItem(
    title: String,
    duration: String,
    url: String,
    onClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(url) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = "Play Stream",
                tint = VlcOrange,
                modifier = Modifier
                    .size(32.dp)
                    .padding(end = 8.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Demo Online Media Link",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = duration,
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(end = 4.dp)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Go",
                tint = Color.DarkGray
            )
        }
    }
}

@Composable
fun RecentVideoItem(
    video: RecentVideo,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val dateString = remember(video.timestamp) {
        val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(video.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() },
        colors = CardDefaults.cardColors(containerColor = VlcCharcoal),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left custom mock thumbnail icon
            Box(
                modifier = Modifier
                    .size(80.dp, 50.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(VlcBlack),
                contentAlignment = Alignment.Center
            ) {
                if (video.thumbnailPath != null) {
                    AsyncImage(
                        model = video.thumbnailPath,
                        contentDescription = "Video frame thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = "Video file icon",
                        tint = VlcOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text information
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Last played: $dateString",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                
                if (video.durationMs > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val progress = video.lastPositionMs.toFloat() / video.durationMs.toFloat()
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = VlcOrange,
                        trackColor = Color.DarkGray
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Delete History Item Button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_recent_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove from history",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerContainer(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isControlsVisible by viewModel.isControlsVisible.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val title by viewModel.currentVideoTitle.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        viewModel.initPlayer(context)
        onDispose {
            viewModel.releasePlayer()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Re-appear controls on screen tap
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { viewModel.toggleControlsVisibility() }
                )
            }
    ) {
        player?.let { activePlayer ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = activePlayer
                        useController = false // Custom UI overlay used instead
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                update = { view ->
                    view.player = activePlayer
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Custom Overlay Controls (VLC inspired)
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = { viewModel.stop() },
                            modifier = Modifier.testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = title ?: "Playing Video",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Playback Speed Toggle
                    var speedMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { speedMenuExpanded = true },
                            modifier = Modifier.testTag("player_speed_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(VlcOrange.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SlowMotionVideo,
                                    contentDescription = "Playback Speed",
                                    tint = VlcOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${playbackSpeed}x",
                                    color = VlcOrange,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = speedMenuExpanded,
                            onDismissRequest = { speedMenuExpanded = false },
                            modifier = Modifier.background(VlcCharcoal)
                        ) {
                            val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
                            speeds.forEach { speed ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (speed == 1.0f) "1.0x (Normal)" else "${speed}x",
                                            color = if (playbackSpeed == speed) VlcOrange else Color.White,
                                            fontWeight = if (playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.setPlaybackSpeed(speed)
                                        speedMenuExpanded = false
                                    },
                                    modifier = Modifier.testTag("speed_option_$speed")
                                )
                            }
                        }
                    }
                }

                // Center Action Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // STOP Button
                    IconButton(
                        onClick = { viewModel.stop() },
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .testTag("player_stop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "Stop Video",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // PLAY / PAUSE Button
                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier
                            .size(72.dp)
                            .background(VlcOrange, CircleShape)
                            .testTag("player_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Bottom Seek Bar Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                        .align(Alignment.BottomCenter)
                        .pointerInput(Unit) {
                            // Catch clicks in control area so they don't toggle visibility
                            detectTapGestures { viewModel.setControlsVisible(true) }
                        }
                ) {
                    // Time and Progress Indicators
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDuration(currentPosition),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatDuration(duration),
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Media Slider
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { newValue ->
                            viewModel.seekTo(newValue.toLong())
                        },
                        valueRange = 0f..(if (duration > 0f) duration.toFloat() else 1000f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("player_seek_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = VlcOrange,
                            activeTrackColor = VlcOrange,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                }
            }
        }
    }
}

// Format duration helper (hh:mm:ss or mm:ss)
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = (ms / (1000 * 60 * 60)) % 24
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

// Retrieve file name helper for content uri
fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result
}
