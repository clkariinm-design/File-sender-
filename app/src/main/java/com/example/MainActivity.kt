package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.TransferProgressModal
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReceiveScreen
import com.example.ui.screens.SendScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.DeepSpaceCard
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.XShareTheme
import com.example.viewmodel.AppDestination
import com.example.viewmodel.HistoryViewModel
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.ReceiveViewModel
import com.example.viewmodel.SendViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            XShareTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp(
    mainViewModel: MainViewModel = viewModel(),
    sendViewModel: SendViewModel = viewModel(),
    receiveViewModel: ReceiveViewModel = viewModel(),
    historyViewModel: HistoryViewModel = viewModel()
) {
    val currentDestination by mainViewModel.currentDestination.collectAsState()
    val storageOverview by mainViewModel.storageOverview.collectAsState()
    val overallStats by mainViewModel.overallStats.collectAsState()
    val recentTransfers by mainViewModel.recentTransfers.collectAsState()
    val activeProgress by mainViewModel.activeProgress.collectAsState()
    val localIp by mainViewModel.localIp.collectAsState()

    // Request permissions dynamically based on Android version
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        mainViewModel.refreshStorage()
        sendViewModel.loadCategory(sendViewModel.selectedCategory.value)
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        permissionsLauncher.launch(permissions.toTypedArray())
    }

    // Handle back button: navigate back to Home if in another screen
    BackHandler(enabled = currentDestination != AppDestination.HOME) {
        mainViewModel.navigateTo(AppDestination.HOME)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceBlack)
            .windowInsetsPadding(WindowInsets.statusBars),
        bottomBar = {
            BottomGlassNavigationBar(
                currentDestination = currentDestination,
                onSelectDestination = { destination ->
                    mainViewModel.navigateTo(destination)
                    if (destination == AppDestination.SEND) {
                        sendViewModel.startPeerDiscovery()
                    }
                }
            )
        },
        containerColor = DeepSpaceBlack
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ScreenTransition"
            ) { destination ->
                when (destination) {
                    AppDestination.HOME -> HomeScreen(
                        deviceName = mainViewModel.deviceName,
                        localIp = localIp,
                        storageOverview = storageOverview,
                        overallStats = overallStats,
                        recentTransfers = recentTransfers,
                        onNavigateToSend = { mainViewModel.navigateTo(AppDestination.SEND) },
                        onNavigateToReceive = { mainViewModel.navigateTo(AppDestination.RECEIVE) },
                        onNavigateToHistory = { mainViewModel.navigateTo(AppDestination.HISTORY) }
                    )

                    AppDestination.SEND -> SendScreen(
                        viewModel = sendViewModel
                    )

                    AppDestination.RECEIVE -> ReceiveScreen(
                        viewModel = receiveViewModel
                    )

                    AppDestination.HISTORY -> HistoryScreen(
                        viewModel = historyViewModel
                    )

                    AppDestination.SETTINGS -> SettingsScreen(
                        deviceName = mainViewModel.deviceName
                    )
                }
            }

            // Global Live Transfer Progress Modal
            TransferProgressModal(
                state = activeProgress,
                onCancel = { mainViewModel.cancelActiveTransfer() },
                onDismiss = { mainViewModel.dismissProgressModal() }
            )
        }
    }
}

@Composable
fun BottomGlassNavigationBar(
    currentDestination: AppDestination,
    onSelectDestination: (AppDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(DeepSpaceCard.copy(alpha = 0.95f))
            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("bottom_nav_bar")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = currentDestination == AppDestination.HOME,
                onClick = { onSelectDestination(AppDestination.HOME) }
            )

            BottomNavItem(
                icon = Icons.Default.ArrowUpward,
                label = "Send",
                isSelected = currentDestination == AppDestination.SEND,
                onClick = { onSelectDestination(AppDestination.SEND) }
            )

            BottomNavItem(
                icon = Icons.Default.ArrowDownward,
                label = "Receive",
                isSelected = currentDestination == AppDestination.RECEIVE,
                onClick = { onSelectDestination(AppDestination.RECEIVE) }
            )

            BottomNavItem(
                icon = Icons.Default.History,
                label = "History",
                isSelected = currentDestination == AppDestination.HISTORY,
                onClick = { onSelectDestination(AppDestination.HISTORY) }
            )

            BottomNavItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                isSelected = currentDestination == AppDestination.SETTINGS,
                onClick = { onSelectDestination(AppDestination.SETTINGS) }
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("nav_item_${label.lowercase()}")
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) ElectricCyan else TextMuted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (isSelected) ElectricCyan else TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
