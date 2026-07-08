package com.example.ui.screens

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.decode.VideoFrameDecoder
import com.example.data.model.MediaItem
import com.example.data.telegram.BackupStatus
import com.example.ui.GalleryViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LocalGalleryScreen(
    viewModel: GalleryViewModel,
    onItemClick: (MediaItem) -> Unit
) {
    val items by viewModel.mediaItems.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val backupProgress by viewModel.backupProgress.collectAsState()
    val backupStatus by viewModel.backupStatus.collectAsState()
    val activeUploadId by viewModel.activeUploadId.collectAsState()

    val searchQuery by viewModel.localSearchQuery.collectAsState()
    val mediaTypeFilter by viewModel.localMediaTypeFilter.collectAsState()

    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Handlers back button press when selection mode is active to clear the selection first
    androidx.activity.compose.BackHandler(enabled = selectedIds.isNotEmpty()) {
        selectedIds = emptySet()
    }

    val groupedItems = remember(items) {
        items.sortedByDescending { it.dateTaken }
            .groupBy { formatDate(it.dateTaken) }
    }

    // Setup Gallery Permissions based on Build SDK version
    val storagePermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        rememberMultiplePermissionsState(
            permissions = listOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO
            )
        )
    } else {
        rememberMultiplePermissionsState(
            permissions = listOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
        )
    }

    // Immediately prompt for permissions on first launch
    LaunchedEffect(Unit) {
        if (!storagePermissions.allPermissionsGranted) {
            storagePermissions.launchMultiplePermissionRequest()
        }
    }

    LaunchedEffect(storagePermissions.allPermissionsGranted) {
        if (storagePermissions.allPermissionsGranted) {
            viewModel.scanLocalGallery()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
        // Welcome and Hero Banner
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Local Gallery",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${items.size} Media items detected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Filter Row (Slim & Modern Design)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search local by name or date (yyyy-mm-dd)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setLocalSearchQuery(it) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input"),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { viewModel.setLocalSearchQuery("") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Media Type Filter Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = mediaTypeFilter == "all",
                onClick = { viewModel.setLocalMediaTypeFilter("all") },
                label = { Text("All") },
                leadingIcon = { Icon(Icons.Default.AllInclusive, null, Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = mediaTypeFilter == "image",
                onClick = { viewModel.setLocalMediaTypeFilter("image") },
                label = { Text("Images") },
                leadingIcon = { Icon(Icons.Default.Image, null, Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = mediaTypeFilter == "video",
                onClick = { viewModel.setLocalMediaTypeFilter("video") },
                label = { Text("Videos") },
                leadingIcon = { Icon(Icons.Default.Videocam, null, Modifier.size(16.dp)) }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Beautiful Elevated Suggestion Chip for Backup All Pending
            Surface(
                onClick = { viewModel.backupAllPending() },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.height(32.dp).testTag("backup_all_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Backup All Pending Files",
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Backup All",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Active backup Progress Bar
        backupProgress?.let { progress ->
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (backupStatus == BackupStatus.PAUSED) "Backup Paused" else "Backing up items...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pause / Resume Button
                        IconButton(
                            onClick = {
                                if (backupStatus == BackupStatus.PAUSED) {
                                    viewModel.resumeBackup()
                                } else {
                                    viewModel.pauseBackup()
                                }
                            },
                            modifier = Modifier.testTag("pause_resume_backup_button")
                        ) {
                            Icon(
                                imageVector = if (backupStatus == BackupStatus.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (backupStatus == BackupStatus.PAUSED) "Resume Backup" else "Pause Backup",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Cancel Button
                        IconButton(
                            onClick = { viewModel.cancelBackup() },
                            modifier = Modifier.testTag("cancel_backup_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Cancel Backup",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!storagePermissions.allPermissionsGranted) {
            // Permission Prompt Empty State
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Permissions Required",
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Access to Storage Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "VishwaKosh needs gallery access to display and backup your photos and videos.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { storagePermissions.launchMultiplePermissionRequest() },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Lock, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Grant Gallery Access")
                }
            }
        } else if (items.isEmpty()) {
            // Empty State
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Empty gallery",
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (searchQuery.isNotEmpty()) "No results found" else "Your gallery is empty",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (searchQuery.isNotEmpty()) "Try adjusting your search criteria" else "Tap sync above to trigger a local file scan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            // Gallery Grid divided by Date
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("local_gallery_grid"),
                contentPadding = PaddingValues(top = 8.dp, bottom = if (selectedIds.isNotEmpty()) 88.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedItems.forEach { (dateHeader, itemsInGroup) ->
                    // Date Separator Row Header with Select All checkpoint
                    item(
                        span = { GridItemSpan(maxLineSpan) },
                        key = "header_$dateHeader"
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = dateHeader,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Clean, circle checkpoint "select all of that day"
                            val allItemsInGroupSelected = itemsInGroup.all { it.id in selectedIds }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        val newSelected = selectedIds.toMutableSet()
                                        if (allItemsInGroupSelected) {
                                            itemsInGroup.forEach { newSelected.remove(it.id) }
                                        } else {
                                            itemsInGroup.forEach { newSelected.add(it.id) }
                                        }
                                        selectedIds = newSelected
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (allItemsInGroupSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = "Select All of $dateHeader",
                                    tint = if (allItemsInGroupSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Select All",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Grid Photos/Videos under this Date Header
                    items(itemsInGroup, key = { it.id }) { item ->
                        val isSelected = item.id in selectedIds
                        GalleryGridItem(
                            item = item,
                            isUploading = activeUploadId == item.id,
                            isSelected = isSelected,
                            isSelectionModeActive = selectedIds.isNotEmpty(),
                            onToggleSelection = {
                                val newSelected = selectedIds.toMutableSet()
                                if (isSelected) {
                                    newSelected.remove(item.id)
                                } else {
                                    newSelected.add(item.id)
                                }
                                selectedIds = newSelected
                            },
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
        }
    }

    // Floating Premium Multi-Select Upload Bar overlayed at the bottom
    if (selectedIds.isNotEmpty()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Circular Count Badge
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${selectedIds.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Metadata Column (with flex-grow weight to fill available width)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Selected",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Ready to upload",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                // Compact Close Button instead of text button to conserve space
                IconButton(
                    onClick = { selectedIds = emptySet() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel selection",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Premium, non-wrapping upload action button
                Button(
                    onClick = {
                        viewModel.backupSelectedItems(selectedIds) {
                            selectedIds = emptySet()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Upload",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        // Floating Action Button for Scan / Refresh when selection mode is not active
        if (selectedIds.isEmpty()) {
            val rotationAngle = remember { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(isScanning) {
                if (isScanning) {
                    rotationAngle.animateTo(
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        )
                    )
                } else {
                    rotationAngle.snapTo(0f)
                }
            }

            FloatingActionButton(
                onClick = {
                    if (storagePermissions.allPermissionsGranted) {
                        viewModel.scanLocalGallery()
                    } else {
                        storagePermissions.launchMultiplePermissionRequest()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 16.dp)
                    .testTag("scan_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Scan Storage",
                        modifier = Modifier.rotate(rotationAngle.value)
                    )
                    Text("Sync Storage", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryGridItem(
    item: MediaItem,
    isUploading: Boolean,
    isSelected: Boolean,
    isSelectionModeActive: Boolean,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(item.localUri, item.isVideo) {
        ImageRequest.Builder(context)
            .data(item.localUri)
            .apply {
                if (item.isVideo) {
                    decoderFactory(VideoFrameDecoder.Factory())
                }
            }
            .crossfade(true)
            .size(300) // Downscale huge local files to 300px thumbnail
            .build()
    }
    
    val gradientBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.2f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.4f)
            )
        )
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = {
                    if (isSelectionModeActive) {
                        onToggleSelection()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    onToggleSelection()
                }
            )
    ) {
        // Thumbnail Image
        AsyncImage(
            model = imageRequest,
            contentDescription = item.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay for visual contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
        )

        // Play icon overlay for Video
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Video file",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        // Selection Checkpoint Overlays
        if (isSelectionModeActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f),
                            CircleShape
                        )
                        .padding(2.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "Selected" else "Not Selected",
                        tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Backup Status Icon Indicator
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
        ) {
            if (isUploading) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .padding(4.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                when (item.backupStatus) {
                    "BACKED_UP" -> {
                        Box(
                            modifier = Modifier
                                .background(Color.White, CircleShape)
                                .padding(1.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Backed up",
                                tint = Color(0xFF4CAF50), // Nice green success circle
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    "FAILED" -> {
                        Box(
                            modifier = Modifier
                                .background(Color.White, CircleShape)
                                .padding(1.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Backup failed",
                                tint = Color(0xFFE53935), // Nice red error icon
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    // "NOT_BACKED_UP" shows nothing or a subtle cloud icon
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                .padding(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "Not backed up",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
    val itemDateStr = sdf.format(Date(timestamp))
    
    val todayStr = sdf.format(Date())
    val yesterdayStr = sdf.format(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000))
    
    return when (itemDateStr) {
        todayStr -> "Today"
        yesterdayStr -> "Yesterday"
        else -> itemDateStr
    }
}
