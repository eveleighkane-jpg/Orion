package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FileEntry
import com.example.ui.theme.*
import java.io.File

@Composable
fun VisualFileBrowser(
    currentDir: File,
    files: List<FileEntry>,
    showHidden: Boolean,
    hasAllFilesAccess: Boolean,
    onNavigateTo: (File) -> Unit,
    onNavigateUp: () -> Unit,
    onToggleHidden: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
            .padding(12.dp)
    ) {
        // Quick Jump Bookmarks Bar
        val bookmarks = listOf(
            "~" to "Home",
            "/sdcard" to "Storage",
            "/system" to "System",
            "/" to "Root (/)"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            bookmarks.forEach { (path, label) ->
                val isSelected = currentDir.absolutePath == path || (path == "~" && currentDir.name == "files")
                AssistChip(
                    onClick = { onNavigateTo(File(if (path == "~") currentDir.absolutePath else path)) },
                    label = {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) NeonGreen else TextPrimary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = when (path) {
                                "~" -> Icons.Default.Home
                                "/sdcard" -> Icons.Default.SdCard
                                "/system" -> Icons.Default.Settings
                                else -> Icons.Default.FolderSpecial
                            },
                            contentDescription = label,
                            tint = if (isSelected) NeonGreen else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(containerColor = SurfaceDark),
                    modifier = Modifier.testTag("bookmark_$label")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // All Files Access prompt banner if storage is locked
        if (!hasAllFilesAccess && (currentDir.absolutePath.startsWith("/sdcard") || currentDir.absolutePath.startsWith("/storage"))) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AccentAmber.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Storage Restricted",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentAmber
                        )
                        Text(
                            text = "Grant All Files Access to explore device storage without restriction.",
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRequestAllFilesAccess,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("grant_storage_access_button")
                    ) {
                        Text("Grant", fontSize = 11.sp, color = TerminalBg, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Current Path Breadcrumb & Controls Row
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateUp,
                        enabled = currentDir.parentFile != null,
                        modifier = Modifier.size(32.dp).testTag("navigate_up_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Navigate Up",
                            tint = if (currentDir.parentFile != null) NeonGreen else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = currentDir.absolutePath,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary,
                        maxLines = 1
                    )
                }

                FilterChip(
                    selected = showHidden,
                    onClick = onToggleHidden,
                    label = { Text(".*", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = SurfaceCard,
                        selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
                        selectedLabelColor = NeonGreen
                    ),
                    modifier = Modifier.testTag("toggle_hidden_files")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Files List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("file_browser_list"),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (files.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Empty Directory or Access Restricted",
                            color = TextMuted,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            items(files) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (item.isDirectory) {
                                onNavigateTo(File(item.fullPath))
                            }
                        }
                        .testTag("file_item_${item.name}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                            contentDescription = if (item.isDirectory) "Folder" else "File",
                            tint = if (item.isDirectory) NeonGreen else AccentCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (item.isHidden) TextMuted else TextPrimary
                                )
                                if (item.isVirtualRoot) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = AccentAmber.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Virtual",
                                            fontSize = 9.sp,
                                            color = AccentAmber,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = item.permissions,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextMuted
                                )
                                // SELinux Badge
                                Surface(
                                    color = SurfaceCard,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = item.selinuxLabel,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = AccentPurple,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
