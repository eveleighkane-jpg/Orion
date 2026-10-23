package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CommandOutput
import com.example.model.PrivilegeStatus
import com.example.ui.theme.*

@Composable
fun TerminalScreen(
    outputs: List<CommandOutput>,
    privilegeStatus: PrivilegeStatus,
    onExecuteCommand: (String, Boolean) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var commandInput by remember { mutableStateOf("") }
    var useRoot by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(outputs.size) {
        if (outputs.isNotEmpty()) {
            listState.animateScrollToItem(outputs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
            .padding(12.dp)
    ) {
        // Status header banner
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("terminal_status_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "SELinux Status",
                        tint = if (privilegeStatus.selinuxEnforcing) NeonGreen else AccentAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SELinux: ${if (privilegeStatus.selinuxEnforcing) "Enforcing" else "Permissive"}",
                        fontSize = 11.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "UID: ${privilegeStatus.uid}",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = useRoot,
                        onClick = { useRoot = !useRoot },
                        label = {
                            Text(
                                text = if (useRoot) "SU (Root)" else "SH (User)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (useRoot) AccentRed else NeonGreen
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SurfaceCard,
                            selectedContainerColor = AccentRed.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("root_mode_toggle")
                    )

                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(32.dp).testTag("clear_terminal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Command Shortcuts
        val quickCommands = listOf("uname -a", "id", "getprop", "df -h", "ps -A", "pwd", "ls -l")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickCommands.forEach { cmd ->
                SuggestionChip(
                    onClick = { onExecuteCommand(cmd, useRoot) },
                    label = { Text(cmd, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentCyan) },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = SurfaceCard),
                    modifier = Modifier.testTag("quick_cmd_$cmd")
                )
            }
        }

        // Terminal Console Output Box
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .testTag("terminal_output_list")
            ) {
                if (outputs.isEmpty()) {
                    item {
                        Text(
                            text = "Orion Privileged Terminal v1.0.0\nType a command below or tap a quick chip.\nNo personal data or secrets configured.\n",
                            color = NeonGreen,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                items(outputs) { line ->
                    val color = when {
                        line.isCommandEcho -> AccentCyan
                        line.isError -> AccentRed
                        else -> TextPrimary
                    }
                    Text(
                        text = line.text,
                        color = color,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }

        // Interactive Command Input Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("command_input_field"),
                placeholder = {
                    Text(
                        text = if (useRoot) "Enter root command..." else "Enter shell command...",
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                leadingIcon = {
                    Text(
                        text = if (useRoot) "#" else "$",
                        color = if (useRoot) AccentRed else NeonGreen,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp
                    )
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonGreen,
                    unfocusedBorderColor = BorderDark,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (commandInput.isNotBlank()) {
                        onExecuteCommand(commandInput, useRoot)
                        commandInput = ""
                    }
                })
            )

            Spacer(modifier = Modifier.width(8.dp))

            FilledIconButton(
                onClick = {
                    if (commandInput.isNotBlank()) {
                        onExecuteCommand(commandInput, useRoot)
                        commandInput = ""
                    }
                },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = NeonGreen),
                modifier = Modifier
                    .size(52.dp)
                    .testTag("send_command_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Execute",
                    tint = TerminalBg
                )
            }
        }
    }
}
