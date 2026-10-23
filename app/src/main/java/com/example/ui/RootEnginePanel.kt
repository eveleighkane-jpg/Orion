package com.example.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PrivilegeStatus
import com.example.model.SystemlessModule
import com.example.service.BootPatcherSimulator
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun RootEnginePanel(
    privilegeStatus: PrivilegeStatus,
    modules: List<SystemlessModule>,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val bootPatcher = remember { BootPatcherSimulator() }
    var patchingLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var isOperating by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
            .padding(12.dp)
    ) {
        // Core Magisk Architecture Overview Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("root_architecture_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Magisk Privilege Daemon",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGreen
                        )
                        Text(
                            text = privilegeStatus.magiskVersion,
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                    Surface(
                        color = if (privilegeStatus.isRootAvailable) NeonGreen.copy(alpha = 0.2f) else SurfaceCard,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (privilegeStatus.isRootAvailable) "UID 0 (Active)" else "Standalone Sandbox",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (privilegeStatus.isRootAvailable) NeonGreen else AccentAmber,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = BorderDark)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("SELinux Domain", fontSize = 10.sp, color = TextMuted)
                        Text(privilegeStatus.selinuxDomain, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentPurple)
                    }
                    Column {
                        Text("Policy Mode", fontSize = 10.sp, color = TextMuted)
                        Text(if (privilegeStatus.selinuxEnforcing) "Enforcing" else "Permissive", fontSize = 11.sp, color = TextPrimary)
                    }
                    Column {
                        Text("Daemon Bind", fontSize = 10.sp, color = TextMuted)
                        Text("u:r:magisk:s0", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentCyan)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Actions Row: Boot Patcher & Systemless Teardown
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (!isOperating) {
                        isOperating = true
                        patchingLogs = listOf("Starting boot image patch pipeline...")
                        coroutineScope.launch {
                            bootPatcher.patchBootImage().collect { step ->
                                patchingLogs = patchingLogs + step.message
                                if (step.isComplete) isOperating = false
                            }
                        }
                    }
                },
                enabled = !isOperating,
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                modifier = Modifier.weight(1f).testTag("simulate_boot_patch_button")
            ) {
                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp), tint = TerminalBg)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Simulate Patch", fontSize = 12.sp, color = TerminalBg, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    if (!isOperating) {
                        isOperating = true
                        patchingLogs = listOf("Initiating automated systemless overlay teardown...")
                        coroutineScope.launch {
                            bootPatcher.teardownModules().collect { step ->
                                patchingLogs = patchingLogs + step.message
                                if (step.isComplete) isOperating = false
                            }
                        }
                    }
                },
                enabled = !isOperating,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                modifier = Modifier.weight(1f).testTag("teardown_overlays_button")
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Module Teardown", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // Live Log Box
        if (patchingLogs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
            ) {
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    items(patchingLogs) { log ->
                        Text(
                            text = log,
                            color = NeonGreen,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Systemless Modules Section
        Text(
            text = "Systemless Modules (/data/adb/modules)",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(modules) { module ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("module_item_${module.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(module.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("v${module.version} by ${module.author}", fontSize = 11.sp, color = TextMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(module.mountPath, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AccentPurple)
                        }
                        Switch(
                            checked = module.isEnabled,
                            onCheckedChange = { /* Dynamic overlay toggle */ },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonGreen,
                                checkedTrackColor = NeonGreen.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
        }
    }
}
