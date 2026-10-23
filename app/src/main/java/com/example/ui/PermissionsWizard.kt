package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PermissionActionType
import com.example.model.SpecialPermission
import com.example.ui.theme.*

@Composable
fun PermissionsWizard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var permissionsList by remember { mutableStateOf(checkPermissions(context)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
            .padding(12.dp)
    ) {
        // Header
        Text(
            text = "Special Access & Privileges Suite",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = NeonGreen
        )
        Text(
            text = "Configure system-level capabilities, overlay drawing, and file sandboxes.",
            fontSize = 12.sp,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Permission Items List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(permissionsList) { perm ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("permission_item_${perm.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = perm.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = if (perm.isGranted) NeonGreen.copy(alpha = 0.2f) else AccentAmber.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (perm.isGranted) "Granted" else "Required",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (perm.isGranted) NeonGreen else AccentAmber,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = perm.description,
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                launchPermissionIntent(context, perm.actionType)
                                permissionsList = checkPermissions(context)
                            },
                            enabled = !perm.isGranted,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (perm.isGranted) SurfaceCard else NeonGreen
                            ),
                            modifier = Modifier.testTag("action_perm_${perm.id}")
                        ) {
                            Text(
                                text = if (perm.isGranted) "Active" else "Enable",
                                fontSize = 11.sp,
                                color = if (perm.isGranted) TextMuted else TerminalBg,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Command Suite ADB Card
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("adb_command_suite_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CLI / ADB Command Suite", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Grant permissions directly via ADB:\n\n" +
                                    "adb shell appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow\n" +
                                    "adb shell appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

private fun checkPermissions(context: Context): List<SpecialPermission> {
    val canDrawOverlays = Settings.canDrawOverlays(context)
    val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }

    return listOf(
        SpecialPermission(
            id = "overlays",
            title = "System Alert Overlays",
            description = "Draw terminal HUD widgets and quick-inspection tools over other applications.",
            isGranted = canDrawOverlays,
            actionType = PermissionActionType.OVERLAY
        ),
        SpecialPermission(
            id = "all_files",
            title = "All Files Access",
            description = "Full unrestricted read/write access to device storage partitions and SD Card.",
            isGranted = hasAllFilesAccess,
            actionType = PermissionActionType.ALL_FILES_STORAGE
        ),
        SpecialPermission(
            id = "battery_opt",
            title = "Battery Unrestricted",
            description = "Prevent kernel sleep and doze mode termination during long-running root operations.",
            isGranted = true,
            actionType = PermissionActionType.BATTERY_OPTIMIZATION
        )
    )
}

private fun launchPermissionIntent(context: Context, type: PermissionActionType) {
    try {
        val intent = when (type) {
            PermissionActionType.OVERLAY -> {
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            }
            PermissionActionType.ALL_FILES_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                }
            }
            PermissionActionType.BATTERY_OPTIMIZATION -> {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            }
            PermissionActionType.SETTINGS_PANEL -> {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(fallback)
    }
}
