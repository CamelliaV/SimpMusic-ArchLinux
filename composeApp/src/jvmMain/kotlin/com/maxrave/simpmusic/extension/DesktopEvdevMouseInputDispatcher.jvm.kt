package com.maxrave.simpmusic.extension

import java.awt.Component
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Window
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.swing.SwingUtilities

internal class DesktopEvdevMouseInputDispatcher(
    private val window: Window,
    private val contentComponent: Component,
    private val uiScale: Float,
    private val cachedPointerInContent: () -> Point?,
    private val debug: (String) -> Unit,
) {
    private val readers = mutableListOf<DesktopEvdevMouseInputReader>()

    fun start() {
        val devices = discoverDesktopEvdevMouseDevices()
        debug("evdev devices=${devices.joinToString { it.path }}")
        devices.forEach { device ->
            val reader =
                DesktopEvdevMouseInputReader(
                    device = device,
                    onEvent = { event, state ->
                        SwingUtilities.invokeLater {
                            handleEvent(event = event, state = state, device = device)
                        }
                    },
                    debug = debug,
                )
            readers += reader
            reader.start()
        }
    }

    fun stop() {
        readers.forEach(DesktopEvdevMouseInputReader::stop)
        readers.clear()
    }

    private fun handleEvent(
        event: EvdevInputEvent,
        state: DesktopEvdevMouseDeviceState,
        device: DesktopEvdevInputDevice,
    ) {
        evdevMouseNavigationDirection(event)?.let { direction ->
            if (!window.isActive && !isPointerInsideContent()) return@let
            val dispatched =
                when (direction) {
                    DesktopMouseNavigationDirection.Back -> DesktopMouseNavigationActions.dispatchBack()
                    DesktopMouseNavigationDirection.Forward -> DesktopMouseNavigationActions.dispatchForward()
                }
            if (dispatched) {
                debug("evdev ${device.path} side-button $direction code=${event.code}")
            }
            return
        }

        val dragDelta = state.handleMiddleMouseDrag(event)
        when {
            event.isMiddleButtonPress() -> {
                state.activeTargetHit = currentTargetHit()
                debug("evdev ${device.path} middle press target=${state.activeTargetHit}")
            }
            event.isMiddleButtonRelease() -> {
                debug("evdev ${device.path} middle release")
                state.activeTargetHit = null
            }
            dragDelta != null -> {
                if (state.activeTargetHit == null) {
                    state.activeTargetHit = currentTargetHit()
                    debug("evdev ${device.path} drag-start target=${state.activeTargetHit}")
                }
                if (DesktopMiddleMouseHorizontalDragTargets.dispatchToTarget(state.activeTargetHit, dragDelta)) {
                    debug("evdev ${device.path} drag delta=$dragDelta target=${state.activeTargetHit}")
                }
            }
        }
    }

    private fun currentTargetHit(): DesktopMiddleMouseHorizontalDragTargetHit? {
        val point = pointerInContent() ?: return null
        return DesktopMiddleMouseHorizontalDragTargets.findTargetHitAt(
            windowX = point.x.toFloat(),
            windowY = point.y.toFloat(),
            uiScale = uiScale,
        )
    }

    private fun isPointerInsideContent(): Boolean {
        val point = pointerInContent() ?: return false
        return point.x >= 0 &&
            point.y >= 0 &&
            point.x < contentComponent.width &&
            point.y < contentComponent.height
    }

    private fun pointerInContent(): Point? {
        cachedPointerInContent()?.let { return Point(it) }
        val pointer = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull() ?: return null
        val point = Point(pointer)
        return runCatching {
            SwingUtilities.convertPointFromScreen(point, contentComponent)
            point
        }.getOrNull()
    }
}

internal class DesktopEvdevMouseDeviceState {
    var activeTargetHit: DesktopMiddleMouseHorizontalDragTargetHit? = null
    private var middlePressed = false

    fun handleMiddleMouseDrag(event: EvdevInputEvent): Float? {
        if (event.type == EV_KEY && event.code == BTN_MIDDLE) {
            middlePressed = event.value != 0
            return null
        }
        if (event.type != EV_REL || event.code != REL_X || !middlePressed || event.value == 0) {
            return null
        }
        return event.value.toFloat()
    }
}

internal data class DesktopEvdevInputDevice(
    val path: String,
    val name: String,
)

internal data class EvdevInputEvent(
    val type: Int,
    val code: Int,
    val value: Int,
) {
    fun isMiddleButtonPress(): Boolean = type == EV_KEY && code == BTN_MIDDLE && value != 0

    fun isMiddleButtonRelease(): Boolean = type == EV_KEY && code == BTN_MIDDLE && value == 0
}

private class DesktopEvdevMouseInputReader(
    private val device: DesktopEvdevInputDevice,
    private val onEvent: (EvdevInputEvent, DesktopEvdevMouseDeviceState) -> Unit,
    private val debug: (String) -> Unit,
) {
    private val state = DesktopEvdevMouseDeviceState()
    @Volatile private var stopped = false
    @Volatile private var stream: FileInputStream? = null

    fun start() {
        Thread(
            {
                readLoop()
            },
            "SimpMusic-evdev-${File(device.path).name}",
        ).apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        stopped = true
        runCatching { stream?.close() }
    }

    private fun readLoop() {
        try {
            FileInputStream(device.path).use { input ->
                stream = input
                val buffer = ByteArray(EVDEV_INPUT_EVENT_SIZE)
                while (!stopped) {
                    if (!readFully(input, buffer)) return
                    val event = parseEvdevInputEvent(buffer) ?: continue
                    onEvent(event, state)
                }
            }
        } catch (error: IOException) {
            if (!stopped) {
                debug("evdev ${device.path} read failed: ${error.message}")
            }
        } finally {
            stream = null
        }
    }
}

private fun readFully(
    input: FileInputStream,
    buffer: ByteArray,
): Boolean {
    var offset = 0
    while (offset < buffer.size) {
        val read = input.read(buffer, offset, buffer.size - offset)
        if (read < 0) return false
        offset += read
    }
    return true
}

internal fun parseEvdevInputEvent(buffer: ByteArray): EvdevInputEvent? {
    if (buffer.size < EVDEV_INPUT_EVENT_SIZE) return null
    val bytes = ByteBuffer.wrap(buffer).order(ByteOrder.nativeOrder())
    bytes.position(16)
    return EvdevInputEvent(
        type = bytes.short.toInt() and 0xffff,
        code = bytes.short.toInt() and 0xffff,
        value = bytes.int,
    )
}

internal fun evdevMouseNavigationDirection(event: EvdevInputEvent): DesktopMouseNavigationDirection? {
    if (event.type != EV_KEY || event.value == 0) return null
    return when (event.code) {
        BTN_SIDE,
        BTN_BACK,
        KEY_BACK,
        -> DesktopMouseNavigationDirection.Back
        BTN_EXTRA,
        BTN_FORWARD,
        KEY_FORWARD,
        -> DesktopMouseNavigationDirection.Forward
        else -> null
    }
}

internal fun discoverDesktopEvdevMouseDevices(
    inputDevicesText: String = runCatching { File("/proc/bus/input/devices").readText() }.getOrDefault(""),
): List<DesktopEvdevInputDevice> =
    inputDevicesText
        .split(Regex("\\n\\s*\\n"))
        .mapNotNull(::parseDesktopEvdevMouseDeviceBlock)
        .distinctBy { it.path }

private fun parseDesktopEvdevMouseDeviceBlock(block: String): DesktopEvdevInputDevice? {
    val name =
        block
            .lineSequence()
            .firstOrNull { it.startsWith("N: Name=") }
            ?.substringAfter("N: Name=")
            ?.trim()
            ?.trim('"')
            ?: return null
    val handlers =
        block
            .lineSequence()
            .firstOrNull { it.startsWith("H: Handlers=") }
            ?.substringAfter("H: Handlers=")
            ?: return null
    if (!handlers.split(Regex("\\s+")).any { it.startsWith("mouse") }) return null
    val event = handlers.split(Regex("\\s+")).firstOrNull { it.startsWith("event") } ?: return null
    return DesktopEvdevInputDevice(path = "/dev/input/$event", name = name)
}

internal const val EV_SYN = 0x00
internal const val EV_KEY = 0x01
internal const val EV_REL = 0x02
internal const val REL_X = 0x00
internal const val BTN_MIDDLE = 0x112
internal const val BTN_SIDE = 0x113
internal const val BTN_EXTRA = 0x114
internal const val BTN_FORWARD = 0x115
internal const val BTN_BACK = 0x116
internal const val KEY_BACK = 158
internal const val KEY_FORWARD = 159
internal const val EVDEV_INPUT_EVENT_SIZE = 24
