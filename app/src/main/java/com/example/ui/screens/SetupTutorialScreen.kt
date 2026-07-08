package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

data class TutorialStepItem(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val codeText: String?,
    val codeIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    val imageUrl: String
)

@Composable
fun SetupTutorialScreen(
    onDismiss: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(1) }
    val totalSteps = 4
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val steps = remember {
        listOf(
            TutorialStepItem(
                stepNumber = 1,
                title = "Open @BotFather",
                description = "Launch Telegram and search for the official BotFather account. This is the central hub for creating and managing all Telegram bots.",
                codeText = "@BotFather",
                codeIcon = Icons.Default.Search,
                imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuCP_9r0jYTw21R9AfDbYnwW5UpQrFJp7f-auZime2UKcTag8d4zMMjlBB5nHGZSSSZ0CeErMlI_9NtNPbNXw0UE33Tcp66p8RTROid5kO2Zo0ptg795Btcc2tV_HaC7rRJkLNJ7KxJOiQshvUh2GROdCcAEvgaRDtzGco-E1UQmMjB5FohSMXJb9V2PCWkCqqyNJsS7ALqaXFG96mLerC-BR5motWmRV_d7PYZEvi4OSz0-rQxXKtroNg"
            ),
            TutorialStepItem(
                stepNumber = 2,
                title = "Create New Bot",
                description = "Send the command to initiate the creation process. BotFather will ask you for a name and a unique username for your vault.",
                codeText = "/newbot",
                codeIcon = Icons.Default.AddCircle,
                imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuD021qxrnwvZsG9a1UnBo1D6_SR5jW6WjMOJjdOJtMkFtcy1cA-lt9ux72giv4IAWfttwO8euHJHdn1mWRPtb59xwH53X-Tr0VTl19J5s3s8X1QQhwhIXUNt8ubHG_qTFcVivqCxz4F2yNdNMYgU2dt-Ltz1AtgBWwkuoYUe_rHhTlHsYByYYhuJHiUn9YVKHJ7PFdzLU-ivfieLRTLP6sqEbEO-k-W6Ohc4m5b7ijTMX8Z9c2q5LoiHA"
            ),
            TutorialStepItem(
                stepNumber = 3,
                title = "Copy API Token",
                description = "Once created, BotFather will provide an HTTP API token. Keep this secret! It's the unique key that allows VishwaKosh to connect to your cloud storage.",
                codeText = "123456789:ABCdefGHi...",
                codeIcon = Icons.Default.ContentCopy,
                imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDZcXZuwpYhP6I30332Kkqt_vcgpeXh6mWEcb2NaRZhjYJkN6iouhovQEwPGvi8R407PfBjiAQPiEvgEUb2TbyEOtvuECgfTKbyMhW1zL6ehTrHbOuhYb2nz27djb0XnH6wLIEOqNGT1QlzHGqYlTumAE9PRIZNCMkq3z4v3oAGtrv4bZKW0zZ8twmbZeNGYl2yqkyPIhCYNV6nzVsyS6-Nuo8UfSFOABusHYbezeWzB_IH2gt2IDv-nQ"
            ),
            TutorialStepItem(
                stepNumber = 4,
                title = "Add as Admin",
                description = "Create a private Telegram Channel to serve as your storage vault. Add your new bot as an Administrator with 'Post Messages' permissions.",
                codeText = "Full Admin Privileges",
                codeIcon = Icons.Default.AdminPanelSettings,
                imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuA9EEta5JhH-Euv6krQfC8OAyMiRFwUdVHOE_LzHZ7LVgfWY69b-LkREVLvfNrJ2APkmfEnWgUx-9wqcH3NhZmWHMcbvbUVbLb_kSILOo67QtZ0NBHP0zd55V_OX4qBPN-W4zLUtjLDIJtEBSVZIP0G3LzYEb1MZbMO2IZlwJ6T-V6TlOnhEUGoAmbkUSL-ZWYmRsJ0WHeW_hYKVwR7_qZV0qxfYi7n343LM7cDP2v6QH_ZVnmK98EnvQ"
            )
        )
    }

    val stepItem = steps[currentStep - 1]

    Surface(
        modifier = Modifier.fillMaxSize().testTag("setup_tutorial_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header: Top App Bar simulation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Cloud Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "VishwaKosh",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Profile photo avatar
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("https://lh3.googleusercontent.com/aida-public/AB6AXuDQY6FDSCbPF50q0kS3-RL8NSqiyxI1VO-wNx0R1UE9a1sWNUFoFg_LEqDosguVBrcgBzbm2S6q05YQM4L50E7fR1pY62Y70ApQZl0TvnRF7E7QATWvn6YQBBoWjBGe80dPdNXbZdWNvhJlcnlvrFMYETywvs1UWbq8_dwLxAfGM-Dw7-OieG2YBMlSWMh7jWPRjVkO8rOnlG440ukYTp2JQLspfMEPMjHcEwbroQonluwa_DMMo4sWjg")
                        .crossfade(true)
                        .size(100)
                        .build(),
                    contentDescription = "User Avatar",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            // Progress Bar & Stats Row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Step $currentStep of $totalSteps",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${((currentStep.toFloat() / totalSteps) * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { currentStep.toFloat() / totalSteps },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Content Area with animations
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Image/Illustration Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.1f)
                        .clip(RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(stepItem.imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = stepItem.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // Step text details
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.9f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stepItem.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = stepItem.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // Optional Code/Instruction box with micro-interaction
                    stepItem.codeText?.let { text ->
                        Row(
                            modifier = Modifier
                                .wrapContentWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable {
                                    if (stepItem.stepNumber == 3) {
                                        clipboardManager.setText(AnnotatedString(text))
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            stepItem.codeIcon?.let { icon ->
                                Icon(
                                    imageVector = icon,
                                    contentDescription = "Action Icon",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // Bottom Action Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.background,
                border = borderBrush()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button (disabled on step 1)
                    TextButton(
                        onClick = { if (currentStep > 1) currentStep-- },
                        enabled = currentStep > 1,
                        modifier = Modifier.testTag("prev_step_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            Text("Back", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    // Next / Finish Setup buttons
                    if (currentStep < totalSteps) {
                        Button(
                            onClick = { currentStep++ },
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.height(48.dp).testTag("next_step_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Next Step", style = MaterialTheme.typography.labelLarge)
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                            }
                        }
                    } else {
                        Button(
                            onClick = { onDismiss() },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.height(48.dp).testTag("finish_setup_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Finish Setup", style = MaterialTheme.typography.labelLarge)
                                Icon(Icons.Default.CheckCircle, contentDescription = "Check")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun borderBrush(): BorderStroke? {
    return BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}
