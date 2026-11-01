package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FileCategory
import com.example.data.model.ShareableItem
import com.example.ui.theme.AuroraViolet
import com.example.ui.theme.DeepSpaceCard
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FileItemRow(
    item: ShareableItem,
    onToggleSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, iconBg, iconTint) = getCategoryStyling(item.category)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (item.isSelected) ElectricCyan.copy(alpha = 0.08f) else DeepSpaceCard.copy(alpha = 0.4f))
            .clickable(onClick = onToggleSelect)
            .padding(12.dp)
            .testTag("file_item_${item.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category Icon container
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = item.category.title,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // File Details
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.name,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.formattedSize,
                    color = ElectricCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                if (item.versionName != null) {
                    Text(
                        text = " • v${item.versionName}",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (item.dateModified > 0) {
                    val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(item.dateModified))
                    Text(
                        text = " • $dateStr",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Selection Checkbox
        Checkbox(
            checked = item.isSelected,
            onCheckedChange = { onToggleSelect() },
            colors = CheckboxDefaults.colors(
                checkedColor = ElectricCyan,
                uncheckedColor = TextSecondary.copy(alpha = 0.5f),
                checkmarkColor = Color(0xFF0B0E14)
            ),
            modifier = Modifier.testTag("checkbox_${item.id}")
        )
    }
}

private fun getCategoryStyling(category: FileCategory): Triple<ImageVector, Color, Color> {
    return when (category) {
        FileCategory.APPS -> Triple(
            Icons.Default.Android,
            NeonEmerald.copy(alpha = 0.15f),
            NeonEmerald
        )
        FileCategory.PHOTOS -> Triple(
            Icons.Default.Image,
            ElectricCyan.copy(alpha = 0.15f),
            ElectricCyan
        )
        FileCategory.VIDEOS -> Triple(
            Icons.Default.Videocam,
            AuroraViolet.copy(alpha = 0.15f),
            AuroraViolet
        )
        FileCategory.MUSIC -> Triple(
            Icons.Default.AudioFile,
            Color(0xFFF59E0B).copy(alpha = 0.15f),
            Color(0xFFF59E0B)
        )
        FileCategory.DOCUMENTS -> Triple(
            Icons.Default.Description,
            Color(0xFF38BDF8).copy(alpha = 0.15f),
            Color(0xFF38BDF8)
        )
        FileCategory.ARCHIVES, FileCategory.LARGE_FILES -> Triple(
            Icons.Default.Archive,
            Color(0xFFEC4899).copy(alpha = 0.15f),
            Color(0xFFEC4899)
        )
    }
}
