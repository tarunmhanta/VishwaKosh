package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.GalleryDatabase
import com.example.data.repository.MediaRepository
import com.example.data.telegram.TelegramBackupManager
import com.example.data.telegram.TelegramSettingsManager
import com.example.data.model.MediaItem
import com.example.ui.GalleryViewModel
import com.example.ui.GalleryViewModelFactory
import com.example.ui.screens.CloudGalleryScreen
import com.example.ui.screens.LocalGalleryScreen
import com.example.ui.screens.MediaDetailViewer
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SetupTutorialScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Coil with dynamic and small cache bounds to strictly minimize cache consumption
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.1) // limit memory footprint to 10%
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("vishwakesh_image_cache"))
                    .maxSizeBytes(20 * 1024 * 1024) // STRICT 20 MB disk cache limit
                    .build()
            }
            .build()
        Coil.setImageLoader(imageLoader)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContainer()
            }
        }
    }
}

@Composable
fun MainAppContainer() {
    val context = LocalContext.current
    val database = remember { GalleryDatabase.getDatabase(context) }
    val repository = remember { MediaRepository(context, database.mediaDao()) }
    val settingsManager = remember { TelegramSettingsManager(context) }
    val backupManager = remember { TelegramBackupManager(context) }

    val viewModel: GalleryViewModel = viewModel(
        factory = GalleryViewModelFactory(context, repository, settingsManager, backupManager)
    )

    val selectedTab by viewModel.selectedTab.collectAsState()
    val backupProgress by viewModel.backupProgress.collectAsState()
    val isBackupActive = backupProgress != null
    var showExitDialog by remember { mutableStateOf(false) }

    var selectedMediaItem by remember { mutableStateOf<MediaItem?>(null) }
    var activeDetailMediaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var showTutorial by remember { mutableStateOf(false) }

    // Intercept back button behavior: if in Cloud or Settings tab, go back to Local tab first.
    // If we are on the local tab and backup is active, show the background/stop dialog.
    androidx.activity.compose.BackHandler(enabled = isBackupActive || selectedTab != 0) {
        if (selectedTab != 0) {
            viewModel.setTab(0)
        } else if (isBackupActive) {
            showExitDialog = true
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Backup in Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = { Text("A media backup is currently running. You can let it continue securely in the background, or stop it now.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        val activity = context as? android.app.Activity
                        activity?.moveTaskToBack(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Continue in Background")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        viewModel.cancelBackup()
                        val activity = context as? android.app.Activity
                        activity?.finish()
                    }
                ) {
                    Text("Stop Backup", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            modifier = Modifier.testTag("exit_backup_dialog")
        )
    }

    // Observe snackbar/message signals from ViewModel and display a floating top toast instead
    val backupMessage by viewModel.backupMessage.collectAsState()
    var floatingNotification by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(backupMessage) {
        backupMessage?.let { message ->
            floatingNotification = message
            viewModel.dismissMessage()
        }
    }

    LaunchedEffect(floatingNotification) {
        if (floatingNotification != null) {
            kotlinx.coroutines.delay(4000)
            floatingNotification = null
        }
    }

    // Faster launch delay (300ms instead of 1200ms)
    var showSplashScreen by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        showSplashScreen = false
    }

    if (showSplashScreen) {
        SplashScreenView()
    } else {
        Scaffold(
            modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
            bottomBar = {
                NavigationBar(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .height(64.dp)
                        .testTag("bottom_nav_bar")
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { viewModel.setTab(0) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Local Gallery"
                            )
                        },
                        label = { Text("Local") },
                        modifier = Modifier.testTag("nav_item_local")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { viewModel.setTab(1) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "Telegram Cloud"
                            )
                        },
                        label = { Text("Cloud") },
                        modifier = Modifier.testTag("nav_item_cloud")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { viewModel.setTab(2) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        },
                        label = { Text("Settings") },
                        modifier = Modifier.testTag("nav_item_settings")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val localItems by viewModel.mediaItems.collectAsState()
                val cloudItems by viewModel.cloudMediaItems.collectAsState()

                when (selectedTab) {
                    0 -> {
                        LocalGalleryScreen(
                            viewModel = viewModel,
                            onItemClick = { item ->
                                selectedMediaItem = item
                                activeDetailMediaList = localItems
                            }
                        )
                    }
                    1 -> {
                        CloudGalleryScreen(
                            viewModel = viewModel,
                            onItemClick = { item ->
                                selectedMediaItem = item
                                activeDetailMediaList = cloudItems
                            }
                        )
                    }
                    2 -> {
                        SettingsScreen(
                            botTokenFlow = viewModel.botToken,
                            chatIdFlow = viewModel.chatId,
                            autoBackupPhotosFlow = viewModel.autoBackupPhotosEnabled,
                            autoBackupVideosFlow = viewModel.autoBackupVideosEnabled,
                            originalQualityFlow = viewModel.originalQualityEnabled,
                            wifiOnlyFlow = viewModel.wifiOnlyEnabled,
                            onSaveSettings = { token, chat, autoPhotos, autoVideos, originalQuality, wifiOnly ->
                                viewModel.updateSettings(token, chat, autoPhotos, autoVideos, originalQuality, wifiOnly)
                            },
                            onClearCache = { viewModel.clearAppCache() },
                            onViewGuide = { showTutorial = true }
                        )
                    }
                }

                // Beautiful, custom floating top toast notification overlay
                floatingNotification?.let { message ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(16.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseOnSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .testTag("floating_toast")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val isSuccess = message.contains("success", ignoreCase = true) || message.contains("completed", ignoreCase = true) || message.contains("uploaded", ignoreCase = true)
                                val isError = message.contains("fail", ignoreCase = true) || message.contains("error", ignoreCase = true) || message.contains("credentials", ignoreCase = true)

                                Icon(
                                    imageVector = when {
                                        isSuccess -> Icons.Default.CheckCircle
                                        isError -> Icons.Default.Error
                                        else -> Icons.Default.Info
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        isSuccess -> Color(0xFF4CAF50)
                                        isError -> Color(0xFFE53935)
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(24.dp)
                                )

                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.inverseSurface,
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = { floatingNotification = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Media Detail Dialog Overlay
        selectedMediaItem?.let { item ->
            MediaDetailViewer(
                initialMediaItem = item,
                mediaList = activeDetailMediaList.ifEmpty { listOf(item) },
                viewModel = viewModel,
                onDismiss = { selectedMediaItem = null }
            )
        }

        // Onboarding Setup Tutorial Overlay
        if (showTutorial) {
            SetupTutorialScreen(
                onDismiss = { showTutorial = false }
            )
        }
    }
}

@Composable
fun SplashScreenView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF171A26)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.image1),
                contentDescription = "VishwaKosh Cloud Vault",
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "VishwaKosh",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Secure Telegram Media Vault",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}
