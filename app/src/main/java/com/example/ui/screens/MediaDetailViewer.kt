package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.decode.VideoFrameDecoder
import com.example.data.model.MediaItem
import com.example.ui.GalleryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaDetailViewer(
    initialMediaItem: MediaItem,
    mediaList: List<MediaItem>,
    viewModel: GalleryViewModel,
    onDismiss: () -> Unit
) {
    if (mediaList.isEmpty()) {
        onDismiss()
        return
    }

    val context = LocalContext.current
    
    // Find initial index of current media item
    val initialIndex = remember(initialMediaItem, mediaList) {
        val index = mediaList.indexOfFirst { it.id == initialMediaItem.id }
        if (index >= 0) index else 0
    }

    val pagerState = rememberPagerState(initialPage = initialIndex) {
        mediaList.size
    }

    var showDetails by remember { mutableStateOf(false) }

    // Safe current item derivation
    val safePage = pagerState.currentPage.coerceIn(0, mediaList.lastIndex)
    val currentItem = mediaList[safePage]

    val activeUpload by viewModel.activeUploadId.collectAsState()
    val isUploading = activeUpload == currentItem.id

    val formattedDate = remember(currentItem) {
        SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
            .format(Date(currentItem.dateTaken))
    }

    val formattedSize = remember(currentItem) {
        Formatter.formatShortFileSize(context, currentItem.size)
    }

    // Handle system back press inside the dialog to close details first, or dismiss the viewer
    BackHandler {
        if (showDetails) {
            showDetails = false
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0C0A0F)), // Obsidian black cinema background
            color = Color(0xFF0C0A0F)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Horizontal Swipable Pager (Pure Cinema Screen Canvas)
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("fullscreen_media_pager"),
                    pageSpacing = 16.dp
                ) { page ->
                    val item = mediaList[page.coerceIn(0, mediaList.lastIndex)]
                    val isPageActive = page == pagerState.currentPage

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.isVideo) {
                            FullscreenVideoPlayer(
                                mediaItem = item,
                                isActive = isPageActive,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val displayUri = item.localUri ?: item.telegramUrl
                            if (displayUri != null) {
                                val detailImageRequest = remember(displayUri) {
                                    ImageRequest.Builder(context)
                                        .data(displayUri)
                                        .crossfade(true)
                                        .build()
                                }
                                AsyncImage(
                                    model = detailImageRequest,
                                    contentDescription = item.displayName,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable {
                                            // Toggle controls or detail panel on click
                                            showDetails = !showDetails
                                        },
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                // Missing / Local deleted state
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = "Deleted file",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Local copy deleted",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Sleek Floating Translucent Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.75f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Close viewer",
                            tint = Color.White
                        )
                    }

                    // Count Indicator (e.g. 2 of 5)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${pagerState.currentPage + 1} of ${mediaList.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = currentItem.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 200.dp)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Backup state or upload trigger
                        if (!currentItem.isBackedUp && currentItem.localUri != null) {
                            IconButton(
                                onClick = { viewModel.backupItem(currentItem) },
                                enabled = !isUploading,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .size(40.dp)
                                    .testTag("backup_button_top")
                            ) {
                                if (isUploading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = "Backup to Telegram",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        } else if (currentItem.isBackedUp) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0x334CAF50), CircleShape)
                                    .size(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Backed Up",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Info toggle button (matches the cloud upload button styling)
                        IconButton(
                            onClick = { showDetails = !showDetails },
                            modifier = Modifier
                                .background(
                                    if (showDetails) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
                                    CircleShape
                                )
                                .size(40.dp)
                                .testTag("toggle_details_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Toggle media info details",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Smooth Sliding Details Overlay Card
                AnimatedVisibility(
                    visible = showDetails,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                            .testTag("media_info_card"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xEE1A1824) // Obsidian-indigo frosted glass look
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            // Header row with close detail card action
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "About Media File",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                IconButton(
                                    onClick = { showDetails = false },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Hide Info panel",
                                        tint = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = currentItem.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Details block
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DetailItemDark(
                                    icon = Icons.Default.CalendarToday,
                                    title = "Date Captured",
                                    value = formattedDate
                                )
                                DetailItemDark(
                                    icon = Icons.Default.Storage,
                                    title = "Disk Space",
                                    value = formattedSize
                                )
                                DetailItemDark(
                                    icon = if (currentItem.isVideo) Icons.Default.Movie else Icons.Default.Image,
                                    title = "Media Format",
                                    value = if (currentItem.isVideo) "Video" else "Image"
                                )

                                if (currentItem.isBackedUp) {
                                    DetailItemDark(
                                        icon = Icons.AutoMirrored.Filled.Send,
                                        title = "Telegram MSG ID",
                                        value = "#${currentItem.telegramMessageId ?: "N/A"}"
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Interactive Actions Panel inside the Info card
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (!currentItem.isBackedUp) {
                                        // Backup button inside panel
                                        Button(
                                            onClick = { viewModel.backupItem(currentItem) },
                                            enabled = !isUploading,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .testTag("info_panel_backup_btn"),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            if (isUploading) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    color = Color.White,
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Cloud Backup", fontSize = 13.sp)
                                            }
                                        }
                                    } else {
                                        // Cloud Actions
                                        if (currentItem.localUri != null) {
                                            Button(
                                                onClick = {
                                                    viewModel.deleteLocalFile(currentItem)
                                                    showDetails = false
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp)
                                                    .testTag("free_space_btn"),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Free Space", fontSize = 13.sp)
                                            }
                                        }

                                        if (currentItem.telegramUrl != null) {
                                            OutlinedButton(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentItem.telegramUrl))
                                                    context.startActivity(intent)
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Open Link", fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }

                                if (currentItem.isBackedUp) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.deleteFromCloud(setOf(currentItem.id))
                                            showDetails = false
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Delete from Telegram Cloud", fontSize = 13.sp)
                                    }
                                }

                                // External player helper action for high-compatibility play
                                val displayUri = currentItem.localUri ?: currentItem.telegramUrl
                                if (currentItem.isVideo && displayUri != null) {
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(Uri.parse(displayUri), "video/*")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "No video player application found.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primaryContainer),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open in External Player", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullscreenVideoPlayer(
    mediaItem: MediaItem,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayUri = mediaItem.localUri ?: mediaItem.telegramUrl
    
    var isPlayingVideo by remember { mutableStateOf(false) }
    var videoViewInstance by remember { mutableStateOf<VideoView?>(null) }
    var isVideoPlaying by remember { mutableStateOf(false) }
    var videoProgress by remember { mutableStateOf(0f) }
    var videoDuration by remember { mutableStateOf(0f) }

    // Auto-reset when swiped away
    LaunchedEffect(isActive) {
        if (!isActive) {
            isPlayingVideo = false
            isVideoPlaying = false
            videoViewInstance?.stopPlayback()
            videoViewInstance = null
        }
    }

    LaunchedEffect(isPlayingVideo, isVideoPlaying, videoViewInstance, isActive) {
        if (isPlayingVideo && isActive) {
            while (isActive && isPlayingVideo) {
                videoViewInstance?.let { vv ->
                    if (vv.isPlaying) {
                        videoProgress = vv.currentPosition.toFloat()
                        val dur = vv.duration.toFloat()
                        if (dur > 0) {
                            videoDuration = dur
                        }
                    }
                }
                kotlinx.coroutines.delay(200)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (displayUri != null) {
            if (isPlayingVideo) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            requestFocus()
                            setOnErrorListener { _, what, extra ->
                                isPlayingVideo = false
                                android.widget.Toast.makeText(context, "Codec error. Play in external player instead.", android.widget.Toast.LENGTH_LONG).show()
                                false
                            }
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                                isVideoPlaying = true
                                val dur = duration.toFloat()
                                if (dur > 0) {
                                    videoDuration = dur
                                }
                            }
                            setVideoURI(Uri.parse(displayUri))
                            videoViewInstance = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        videoViewInstance = view
                    }
                )

                // High fidelity video controls panel overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = {
                                videoViewInstance?.let { vv ->
                                    if (vv.isPlaying) {
                                        vv.pause()
                                        isVideoPlaying = false
                                    } else {
                                        vv.start()
                                        isVideoPlaying = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isVideoPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = formatVideoTime(videoProgress),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Slider(
                            value = videoProgress.coerceIn(0f, videoDuration.coerceAtLeast(1f)),
                            onValueChange = { newValue ->
                                videoProgress = newValue
                                videoViewInstance?.seekTo(newValue.toInt())
                            },
                            valueRange = 0f..videoDuration.coerceAtLeast(1f),
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )

                        Text(
                            text = formatVideoTime(videoDuration),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Show video thumbnail with play overlay button
                val detailImageRequest = remember(displayUri) {
                    ImageRequest.Builder(context)
                        .data(displayUri)
                        .decoderFactory(VideoFrameDecoder.Factory())
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = detailImageRequest,
                    contentDescription = mediaItem.displayName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { isPlayingVideo = true },
                    contentScale = ContentScale.Fit
                )

                IconButton(
                    onClick = { isPlayingVideo = true },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .size(72.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Play Inline Video",
                        tint = Color.White,
                        modifier = Modifier.size(54.dp)
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Deleted video",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Local copy deleted",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun DetailItemDark(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

private fun formatVideoTime(ms: Float): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
