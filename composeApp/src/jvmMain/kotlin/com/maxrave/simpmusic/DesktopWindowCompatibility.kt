package com.maxrave.simpmusic

object DesktopWindowCompatibility {
    fun requiresOpaqueWindow(
        osName: String = System.getProperty("os.name", ""),
        xdgSessionType: String? = System.getenv("XDG_SESSION_TYPE"),
        waylandDisplay: String? = System.getenv("WAYLAND_DISPLAY"),
        noTransparentProperty: String? = System.getProperty("compose.window.no-transparent"),
        windowsSystemInfoProvider: () -> String = ::readWindowsSystemInfo,
    ): Boolean {
        if (noTransparentProperty?.toBooleanStrictOrNull() == true) {
            return true
        }

        if (isLinuxWayland(osName, xdgSessionType, waylandDisplay)) {
            return true
        }

        if (!osName.contains("Windows", ignoreCase = true)) {
            return false
        }

        val sysInfo = windowsSystemInfoProvider()
        return vmTokens.any { sysInfo.contains(it, ignoreCase = true) }
    }

    private fun isLinuxWayland(
        osName: String,
        xdgSessionType: String?,
        waylandDisplay: String?,
    ): Boolean =
        osName.contains("Linux", ignoreCase = true) &&
            (xdgSessionType?.equals("wayland", ignoreCase = true) == true || !waylandDisplay.isNullOrBlank())

    private fun readWindowsSystemInfo(): String {
        val probes =
            listOf(
                listOf(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "(Get-CimInstance Win32_ComputerSystem | " +
                        "Select-Object Manufacturer,Model | " +
                        "Format-List | Out-String).Trim()",
                ),
                listOf("wmic", "computersystem", "get", "manufacturer,model"),
            )

        return probes
            .asSequence()
            .mapNotNull { cmd ->
                runCatching {
                    val p =
                        ProcessBuilder(cmd)
                            .redirectErrorStream(true)
                            .start()
                    val out = p.inputStream.bufferedReader().readText()
                    if (p.waitFor() == 0 && out.isNotBlank()) out else null
                }.getOrNull()
            }
            .firstOrNull()
            .orEmpty()
    }

    private val vmTokens = listOf("Parallels", "VirtualBox", "VMware", "QEMU", "KVM", "Xen", "Hyper-V")
}
