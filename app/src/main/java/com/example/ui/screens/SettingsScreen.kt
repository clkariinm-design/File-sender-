package com.example.ui.screens

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.theme.AuroraViolet
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

@Composable
fun SettingsScreen(
    deviceName: String,
    modifier: Modifier = Modifier
) {
    val downloadPath = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "XShare"
    ).path

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpaceBlack)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings & Device",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Device Profile
        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                Column {
                    SettingsItem(
                        icon = Icons.Default.PhoneAndroid,
                        iconColor = ElectricCyan,
                        title = "Device Identity",
                        subtitle = deviceName
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    SettingsItem(
                        icon = Icons.Default.Folder,
                        iconColor = AuroraViolet,
                        title = "Download Location",
                        subtitle = downloadPath
                    )
                }
            }
        }

        // Privacy First Guarantee
        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(NeonEmerald.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = NeonEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "100% Offline & Privacy First",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• No internet connection required\n• Zero ads, trackers, or telemetries\n• No account, registration, or logins needed\n• Direct peer-to-peer Wi-Fi transmission only",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // Network Specifications
        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                Column {
                    SettingsItem(
                        icon = Icons.Default.Wifi,
                        iconColor = ElectricCyan,
                        title = "Transfer Engines",
                        subtitle = "Wi-Fi Direct (P2P) + Local Hotspot + Direct TCP Sockets"
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    SettingsItem(
                        icon = Icons.Default.Code,
                        iconColor = AuroraViolet,
                        title = "Cross-Platform Sharing",
                        subtitle = "Embedded Offline Web Portal enabled on Port 8080"
                    )
                }
            }
        }

        // About & Version
        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                Column {
                    SettingsItem(
                        icon = Icons.Default.Info,
                        iconColor = ElectricCyan,
                        title = "XShare",
                        subtitle = "Version 1.0.0 (Flagship Release)"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Crafted with Kotlin & Jetpack Compose. Modern offline file sharing engineered for speed, privacy, and beauty.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
