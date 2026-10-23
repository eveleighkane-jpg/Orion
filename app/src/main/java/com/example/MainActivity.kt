package com.example

import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.CommandOutput
import com.example.model.FileEntry
import com.example.model.PrivilegeStatus
import com.example.model.SystemlessModule
import com.example.model.TerminalTab
import com.example.service.ShellExecutionEngine
import com.example.ui.PermissionsWizard
import com.example.ui.RootEnginePanel
import com.example.ui.TerminalScreen
import com.example.ui.VisualFileBrowser
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.OrionTerminalTheme
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(private val shellEngine: ShellExecutionEngine) : ViewModel() {
    private val _currentTab = MutableStateFlow(TerminalTab.CONSOLE)
    val currentTab: StateFlow<TerminalTab> = _currentTab.asStateFlow()

    private val _outputs = MutableStateFlow<List<CommandOutput>>(emptyList())
    val outputs: StateFlow<List<CommandOutput>> = _outputs.asStateFlow()

    private val _privilegeStatus = MutableStateFlow(
        PrivilegeStatus(
            isRootAvailable = false,
            uid = 10000,
            gid = 10000,
            selinuxDomain = "u:r:untrusted_app:s0",
            selinuxEnforcing = true,
            magiskVersion = "Standalone",
            rootDaemonActive = false
        )
    )
    val privilegeStatus: StateFlow<PrivilegeStatus> = _privilegeStatus.asStateFlow()

    private val _currentDir = MutableStateFlow(shellEngine.getCurrentDirectory())
    val currentDir: StateFlow<File> = _currentDir.asStateFlow()

    private val _fileEntries = MutableStateFlow<List<FileEntry>>(emptyList())
    val fileEntries: StateFlow<List<FileEntry>> = _fileEntries.asStateFlow()

    private val _showHiddenFiles = MutableStateFlow(true)
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles.asStateFlow()

    private val _modules = MutableStateFlow<List<SystemlessModule>>(emptyList())
    val modules: StateFlow<List<SystemlessModule>> = _modules.asStateFlow()

    init {
        refreshPrivilegeStatus()
        refreshFiles()
        loadModules()
    }

    fun selectTab(tab: TerminalTab) {
        _currentTab.value = tab
    }

    fun executeCommand(command: String, useRoot: Boolean) {
        viewModelScope.launch {
            val newOutputs = shellEngine.executeCommand(command, useRoot)
            _outputs.value = _outputs.value + newOutputs
            _currentDir.value = shellEngine.getCurrentDirectory()
        }
    }

    fun clearTerminal() {
        _outputs.value = emptyList()
    }

    fun refreshPrivilegeStatus() {
        viewModelScope.launch {
            _privilegeStatus.value = shellEngine.getPrivilegeStatus()
        }
    }

    fun navigateTo(dir: File) {
        shellEngine.setCurrentDirectory(dir)
        _currentDir.value = dir
        refreshFiles()
    }

    fun navigateUp() {
        val parent = _currentDir.value.parentFile
        if (parent != null) {
            navigateTo(parent)
        }
    }

    fun toggleShowHidden() {
        _showHiddenFiles.value = !_showHiddenFiles.value
        refreshFiles()
    }

    fun refreshFiles() {
        viewModelScope.launch {
            _fileEntries.value = shellEngine.listFiles(_currentDir.value, _showHiddenFiles.value)
        }
    }

    private fun loadModules() {
        viewModelScope.launch {
            _modules.value = shellEngine.getSystemlessModules()
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val shellEngine = ShellExecutionEngine(applicationContext)

        setContent {
            OrionTerminalTheme {
                val viewModel = remember { MainViewModel(shellEngine) }
                val currentTab by viewModel.currentTab.collectAsState()
                val outputs by viewModel.outputs.collectAsState()
                val privilegeStatus by viewModel.privilegeStatus.collectAsState()
                val currentDir by viewModel.currentDir.collectAsState()
                val fileEntries by viewModel.fileEntries.collectAsState()
                val showHidden by viewModel.showHiddenFiles.collectAsState()
                val modules by viewModel.modules.collectAsState()

                val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    true
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            containerColor = SurfaceDark,
                            modifier = Modifier.testTag("main_navigation_bar")
                        ) {
                            NavigationBarItem(
                                selected = currentTab == TerminalTab.CONSOLE,
                                onClick = { viewModel.selectTab(TerminalTab.CONSOLE) },
                                icon = { Icon(Icons.Default.Terminal, contentDescription = "Console") },
                                label = { Text("Console", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NeonGreen,
                                    selectedTextColor = NeonGreen,
                                    indicatorColor = NeonGreen.copy(alpha = 0.2f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("nav_console")
                            )

                            NavigationBarItem(
                                selected = currentTab == TerminalTab.FILE_BROWSER,
                                onClick = { viewModel.selectTab(TerminalTab.FILE_BROWSER) },
                                icon = { Icon(Icons.Default.Folder, contentDescription = "Files") },
                                label = { Text("Files", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NeonGreen,
                                    selectedTextColor = NeonGreen,
                                    indicatorColor = NeonGreen.copy(alpha = 0.2f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("nav_files")
                            )

                            NavigationBarItem(
                                selected = currentTab == TerminalTab.ROOT_ENGINE,
                                onClick = { viewModel.selectTab(TerminalTab.ROOT_ENGINE) },
                                icon = { Icon(Icons.Default.Build, contentDescription = "Magisk") },
                                label = { Text("Magisk", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NeonGreen,
                                    selectedTextColor = NeonGreen,
                                    indicatorColor = NeonGreen.copy(alpha = 0.2f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("nav_magisk")
                            )

                            NavigationBarItem(
                                selected = currentTab == TerminalTab.PERMISSIONS,
                                onClick = { viewModel.selectTab(TerminalTab.PERMISSIONS) },
                                icon = { Icon(Icons.Default.Security, contentDescription = "Access") },
                                label = { Text("Access", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NeonGreen,
                                    selectedTextColor = NeonGreen,
                                    indicatorColor = NeonGreen.copy(alpha = 0.2f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("nav_access")
                            )
                        }
                    }
                ) { innerPadding ->
                    when (currentTab) {
                        TerminalTab.CONSOLE -> TerminalScreen(
                            outputs = outputs,
                            privilegeStatus = privilegeStatus,
                            onExecuteCommand = { cmd, root -> viewModel.executeCommand(cmd, root) },
                            onClear = { viewModel.clearTerminal() },
                            modifier = Modifier.padding(innerPadding)
                        )
                        TerminalTab.FILE_BROWSER -> VisualFileBrowser(
                            currentDir = currentDir,
                            files = fileEntries,
                            showHidden = showHidden,
                            hasAllFilesAccess = hasAllFilesAccess,
                            onNavigateTo = { viewModel.navigateTo(it) },
                            onNavigateUp = { viewModel.navigateUp() },
                            onToggleHidden = { viewModel.toggleShowHidden() },
                            onRequestAllFilesAccess = { viewModel.selectTab(TerminalTab.PERMISSIONS) },
                            modifier = Modifier.padding(innerPadding)
                        )
                        TerminalTab.ROOT_ENGINE -> RootEnginePanel(
                            privilegeStatus = privilegeStatus,
                            modules = modules,
                            modifier = Modifier.padding(innerPadding)
                        )
                        TerminalTab.PERMISSIONS -> PermissionsWizard(
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
