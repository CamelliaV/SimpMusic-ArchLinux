package com.maxrave.simpmusic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopWindowCompatibilityTest {
    @Test
    fun `requires opaque window on linux wayland session`() {
        assertTrue(
            DesktopWindowCompatibility.requiresOpaqueWindow(
                osName = "Linux",
                xdgSessionType = "wayland",
                waylandDisplay = "wayland-0",
                noTransparentProperty = null,
                windowsSystemInfoProvider = { "" },
            ),
        )
    }

    @Test
    fun `requires opaque window when wayland display is present`() {
        assertTrue(
            DesktopWindowCompatibility.requiresOpaqueWindow(
                osName = "Linux",
                xdgSessionType = null,
                waylandDisplay = "wayland-0",
                noTransparentProperty = null,
                windowsSystemInfoProvider = { "" },
            ),
        )
    }

    @Test
    fun `keeps transparent window on linux x11`() {
        assertFalse(
            DesktopWindowCompatibility.requiresOpaqueWindow(
                osName = "Linux",
                xdgSessionType = "x11",
                waylandDisplay = null,
                noTransparentProperty = null,
                windowsSystemInfoProvider = { "" },
            ),
        )
    }

    @Test
    fun `manual no transparent property forces opaque window`() {
        assertTrue(
            DesktopWindowCompatibility.requiresOpaqueWindow(
                osName = "Linux",
                xdgSessionType = "x11",
                waylandDisplay = null,
                noTransparentProperty = "true",
                windowsSystemInfoProvider = { "" },
            ),
        )
    }

    @Test
    fun `keeps windows virtual machine detection`() {
        assertTrue(
            DesktopWindowCompatibility.requiresOpaqueWindow(
                osName = "Windows 11",
                xdgSessionType = null,
                waylandDisplay = null,
                noTransparentProperty = null,
                windowsSystemInfoProvider = { "Manufacturer: innotek GmbH\nModel: VirtualBox" },
            ),
        )
    }
}
