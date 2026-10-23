package com.example.model

data class CommandOutput(
    val text: String,
    val isError: Boolean = false,
    val isCommandEcho: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class FileEntry(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val permissions: String,
    val selinuxLabel: String,
    val isHidden: Boolean,
    val isVirtualRoot: Boolean = false
)

data class PrivilegeStatus(
    val isRootAvailable: Boolean,
    val uid: Int,
    val gid: Int,
    val selinuxDomain: String,
    val selinuxEnforcing: Boolean,
    val magiskVersion: String,
    val rootDaemonActive: Boolean
)

data class SpecialPermission(
    val id: String,
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val actionType: PermissionActionType
)

enum class PermissionActionType {
    OVERLAY,
    ALL_FILES_STORAGE,
    BATTERY_OPTIMIZATION,
    SETTINGS_PANEL
}

data class SystemlessModule(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val isEnabled: Boolean,
    val mountPath: String
)

enum class TerminalTab {
    CONSOLE,
    FILE_BROWSER,
    ROOT_ENGINE,
    PERMISSIONS
}
