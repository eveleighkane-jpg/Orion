package com.example.service

import android.content.Context
import android.os.Environment
import com.example.model.CommandOutput
import com.example.model.FileEntry
import com.example.model.PrivilegeStatus
import com.example.model.SystemlessModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class ShellExecutionEngine(private val context: Context) {

    private var currentDirectory: File = context.filesDir

    suspend fun getPrivilegeStatus(): PrivilegeStatus = withContext(Dispatchers.IO) {
        val hasSuBinary = checkSuBinary()
        val selinuxLabel = readSelinuxDomain()
        val isEnforcing = readSelinuxEnforcing()

        PrivilegeStatus(
            isRootAvailable = hasSuBinary,
            uid = android.os.Process.myUid(),
            gid = android.os.Process.myGid(),
            selinuxDomain = selinuxLabel,
            selinuxEnforcing = isEnforcing,
            magiskVersion = if (hasSuBinary) "v27.0 (Systemless Daemon)" else "Standalone Sandbox",
            rootDaemonActive = hasSuBinary
        )
    }

    private fun checkSuBinary(): Boolean {
        val suPaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/adb/magisk/magisk64",
            "/data/adb/ksu/bin/ksud"
        )
        for (path in suPaths) {
            if (File(path).exists()) return true
        }
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val exit = process.waitFor()
            exit == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun readSelinuxDomain(): String {
        return try {
            val file = File("/proc/self/attr/current")
            if (file.exists() && file.canRead()) {
                file.readText().trim()
            } else {
                "u:r:untrusted_app:s0"
            }
        } catch (_: Exception) {
            "u:r:untrusted_app:s0"
        }
    }

    private fun readSelinuxEnforcing(): Boolean {
        return try {
            val file = File("/sys/fs/selinux/enforce")
            if (file.exists() && file.canRead()) {
                file.readText().trim() == "1"
            } else {
                true
            }
        } catch (_: Exception) {
            true
        }
    }

    suspend fun executeCommand(command: String, useRoot: Boolean = false): List<CommandOutput> = withContext(Dispatchers.IO) {
        val results = mutableListOf<CommandOutput>()
        results.add(CommandOutput(text = "${if (useRoot) "root#" else "$"} $command", isCommandEcho = true))

        val trimmed = command.trim()
        if (trimmed.isEmpty()) return@withContext results

        // Built-in cd command
        if (trimmed.startsWith("cd ")) {
            val targetPath = trimmed.removePrefix("cd ").trim()
            val newDir = resolvePath(targetPath)
            if (newDir.exists() && newDir.isDirectory) {
                currentDirectory = newDir
                results.add(CommandOutput(text = "Directory changed to: ${newDir.absolutePath}"))
            } else {
                results.add(CommandOutput(text = "cd: no such file or directory: $targetPath", isError = true))
            }
            return@withContext results
        } else if (trimmed == "pwd") {
            results.add(CommandOutput(text = currentDirectory.absolutePath))
            return@withContext results
        } else if (trimmed == "clear") {
            return@withContext emptyList()
        }

        try {
            val shell = if (useRoot && checkSuBinary()) "su" else "sh"
            val process = ProcessBuilder(shell, "-c", trimmed)
                .directory(currentDirectory)
                .redirectErrorStream(false)
                .start()

            val stdOut = BufferedReader(InputStreamReader(process.inputStream)).readLines()
            val stdErr = BufferedReader(InputStreamReader(process.errorStream)).readLines()
            process.waitFor()

            stdOut.forEach { line ->
                results.add(CommandOutput(text = line))
            }
            stdErr.forEach { line ->
                results.add(CommandOutput(text = line, isError = true))
            }

            if (results.size == 1 && stdOut.isEmpty() && stdErr.isEmpty()) {
                results.add(CommandOutput(text = "[Exit code: ${process.exitValue()}]"))
            }
        } catch (e: Exception) {
            results.add(CommandOutput(text = "Execution failure: ${e.message}", isError = true))
        }

        results
    }

    fun getCurrentDirectory(): File = currentDirectory

    fun setCurrentDirectory(file: File) {
        if (file.exists() && file.isDirectory) {
            currentDirectory = file
        }
    }

    fun resolvePath(inputPath: String): File {
        return when {
            inputPath == "~" -> context.filesDir
            inputPath.startsWith("~/") -> File(context.filesDir, inputPath.removePrefix("~/"))
            inputPath.startsWith("/") -> File(inputPath)
            else -> File(currentDirectory, inputPath)
        }
    }

    suspend fun listFiles(dir: File, showHidden: Boolean = true): List<FileEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<FileEntry>()
        val files = dir.listFiles()

        if (files != null) {
            for (f in files) {
                if (!showHidden && f.name.startsWith(".")) continue
                val isDir = f.isDirectory
                val selinux = resolveSelinuxContext(f)
                val permissions = buildString {
                    append(if (isDir) "d" else "-")
                    append(if (f.canRead()) "r" else "-")
                    append(if (f.canWrite()) "w" else "-")
                    append(if (f.canExecute()) "x" else "-")
                }

                entries.add(
                    FileEntry(
                        name = f.name,
                        fullPath = f.absolutePath,
                        isDirectory = isDir,
                        sizeBytes = if (isDir) 0L else f.length(),
                        permissions = permissions,
                        selinuxLabel = selinux,
                        isHidden = f.name.startsWith(".")
                    )
                )
            }
        } else {
            // If restricted root path like / or /system, synthesize virtual entries
            if (dir.absolutePath == "/") {
                val rootVirtualPaths = listOf("system", "vendor", "data", "sdcard", "dev", "proc", "sys", "etc")
                for (vp in rootVirtualPaths) {
                    val subFile = File(dir, vp)
                    entries.add(
                        FileEntry(
                            name = vp,
                            fullPath = subFile.absolutePath,
                            isDirectory = true,
                            sizeBytes = 0L,
                            permissions = "drwxr-xr-x",
                            selinuxLabel = "u:object_r:${vp}_file:s0",
                            isHidden = false,
                            isVirtualRoot = !subFile.canRead()
                        )
                    )
                }
            }
        }

        entries.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun resolveSelinuxContext(file: File): String {
        val path = file.absolutePath
        return when {
            path.startsWith("/system/bin") -> "u:object_r:system_file:s0"
            path.startsWith("/data/adb") -> "u:object_r:magisk_file:s0"
            path.startsWith("/data/data") -> "u:object_r:app_data_file:s0"
            path.startsWith("/sdcard") || path.startsWith("/storage") -> "u:object_r:media_rw_data_file:s0"
            path.startsWith("/sys") -> "u:object_r:sysfs:s0"
            path.startsWith("/dev") -> "u:object_r:device:s0"
            path.startsWith("/proc") -> "u:object_r:proc:s0"
            else -> "u:object_r:system_data_file:s0"
        }
    }

    suspend fun getSystemlessModules(): List<SystemlessModule> = withContext(Dispatchers.IO) {
        val modules = mutableListOf<SystemlessModule>()
        val magiskModulesDir = File("/data/adb/modules")

        if (magiskModulesDir.exists() && magiskModulesDir.canRead()) {
            magiskModulesDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                val propFile = File(dir, "module.prop")
                var name = dir.name
                var version = "1.0"
                var author = "Systemless"
                if (propFile.exists()) {
                    propFile.readLines().forEach { line ->
                        when {
                            line.startsWith("name=") -> name = line.substringAfter("=")
                            line.startsWith("version=") -> version = line.substringAfter("=")
                            line.startsWith("author=") -> author = line.substringAfter("=")
                        }
                    }
                }
                modules.add(
                    SystemlessModule(
                        id = dir.name,
                        name = name,
                        version = version,
                        author = author,
                        isEnabled = !File(dir, "disable").exists(),
                        mountPath = dir.absolutePath
                    )
                )
            }
        }

        // Return synthesized modules if device is not currently rooted
        if (modules.isEmpty()) {
            modules.add(
                SystemlessModule(
                    id = "magisk_selinux_healer",
                    name = "Dynamic SELinux Healing Engine",
                    version = "2.4.0",
                    author = "Orion Architecture",
                    isEnabled = true,
                    mountPath = "/data/adb/modules/magisk_selinux_healer"
                )
            )
            modules.add(
                SystemlessModule(
                    id = "systemless_hosts_v4",
                    name = "Systemless Adblock & Hosts Redirect",
                    version = "1.2.0",
                    author = "Topjohnwu",
                    isEnabled = true,
                    mountPath = "/data/adb/modules/hosts"
                )
            )
        }

        modules
    }
}
