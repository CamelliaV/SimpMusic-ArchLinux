package com.maxrave.simpmusic.extension

import androidx.compose.ui.geometry.Rect
import java.awt.event.MouseEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopPointerExtTest {
    @Test
    fun `registered middle mouse drag target receives horizontal delta when pointer is inside bounds`() {
        DesktopMiddleMouseHorizontalDragTargets.clearForTest()
        var scrolledBy = 0f
        val targetId = DesktopMiddleMouseHorizontalDragTargets.register()
        DesktopMiddleMouseHorizontalDragTargets.update(
            id = targetId,
            bounds = Rect(left = 10f, top = 20f, right = 110f, bottom = 120f),
            scrollBy = { scrolledBy += it },
        )

        assertTrue(
            DesktopMiddleMouseHorizontalDragTargets.dispatchAt(
                windowX = 32f,
                windowY = 64f,
                deltaX = 18f,
            ),
        )
        assertEquals(18f, scrolledBy)

        DesktopMiddleMouseHorizontalDragTargets.clearForTest()
    }

    @Test
    fun `middle mouse drag target selection prefers the most recently registered overlapping target`() {
        DesktopMiddleMouseHorizontalDragTargets.clearForTest()
        var first = 0f
        var second = 0f
        val firstId = DesktopMiddleMouseHorizontalDragTargets.register()
        val secondId = DesktopMiddleMouseHorizontalDragTargets.register()
        DesktopMiddleMouseHorizontalDragTargets.update(
            id = firstId,
            bounds = Rect(left = 0f, top = 0f, right = 100f, bottom = 100f),
            scrollBy = { first += it },
        )
        DesktopMiddleMouseHorizontalDragTargets.update(
            id = secondId,
            bounds = Rect(left = 0f, top = 0f, right = 100f, bottom = 100f),
            scrollBy = { second += it },
        )

        val activeTargetId = DesktopMiddleMouseHorizontalDragTargets.findTargetAt(windowX = 50f, windowY = 50f)
        assertEquals(secondId, activeTargetId)
        assertTrue(DesktopMiddleMouseHorizontalDragTargets.dispatchToTarget(activeTargetId, deltaX = 9f))
        assertEquals(0f, first)
        assertEquals(9f, second)

        DesktopMiddleMouseHorizontalDragTargets.clearForTest()
    }

    @Test
    fun `middle mouse drag target lookup compensates for desktop ui scale`() {
        DesktopMiddleMouseHorizontalDragTargets.clearForTest()
        var scrolledBy = 0f
        val targetId = DesktopMiddleMouseHorizontalDragTargets.register()
        DesktopMiddleMouseHorizontalDragTargets.update(
            id = targetId,
            bounds = Rect(left = 150f, top = 150f, right = 300f, bottom = 300f),
            scrollBy = { scrolledBy += it },
        )

        val hit =
            DesktopMiddleMouseHorizontalDragTargets.findTargetHitAt(
                windowX = 100f,
                windowY = 100f,
                uiScale = 1.5f,
            )

        assertEquals(targetId, hit?.targetId)
        assertTrue(DesktopMiddleMouseHorizontalDragTargets.dispatchToTarget(hit, deltaX = 10f))
        assertEquals(15f, scrolledBy)

        DesktopMiddleMouseHorizontalDragTargets.clearForTest()
    }

    @Test
    fun `middle mouse horizontal drag starts from native middle mouse events`() {
        assertTrue(
            shouldStartMiddleMouseHorizontalDrag(
                isComposeMiddleMouseEvent = false,
                isNativeMiddleMouseEvent = true,
            ),
        )
    }

    @Test
    fun `middle mouse horizontal drag can start from move while middle button is pressed`() {
        assertTrue(shouldCaptureMiddleMouseHorizontalDragEvent(isMiddleMouseEvent = true))
        assertFalse(shouldCaptureMiddleMouseHorizontalDragEvent(isMiddleMouseEvent = false))
    }

    @Test
    fun `middle mouse horizontal drag continues while gesture is active`() {
        assertEquals(
            -32f,
            middleMouseHorizontalDragDelta(
                isDragging = true,
                previousX = 96f,
                currentX = 64f,
            ),
        )
    }

    @Test
    fun `middle mouse horizontal drag ignores inactive gestures and stationary moves`() {
        assertNull(
            middleMouseHorizontalDragDelta(
                isDragging = false,
                previousX = 96f,
                currentX = 64f,
            ),
        )
        assertNull(
            middleMouseHorizontalDragDelta(
                isDragging = true,
                previousX = 96f,
                currentX = 96f,
            ),
        )
    }

    @Test
    fun `awt middle mouse pressed accepts button and modifier state`() {
        assertTrue(
            isAwtMiddleMousePressed(
                button = MouseEvent.BUTTON2,
                modifiersEx = 0,
            ),
        )
        assertTrue(
            isAwtMiddleMousePressed(
                button = MouseEvent.NOBUTTON,
                modifiersEx = MouseEvent.BUTTON2_DOWN_MASK,
            ),
        )
        assertFalse(
            isAwtMiddleMousePressed(
                button = MouseEvent.BUTTON1,
                modifiersEx = MouseEvent.BUTTON1_DOWN_MASK,
            ),
        )
    }

    @Test
    fun `awt middle mouse drag accepts moved events while middle button is down`() {
        assertTrue(isAwtMiddleMouseDragMotionEvent(MouseEvent.MOUSE_DRAGGED))
        assertTrue(isAwtMiddleMouseDragMotionEvent(MouseEvent.MOUSE_MOVED))
        assertFalse(isAwtMiddleMouseDragMotionEvent(MouseEvent.MOUSE_PRESSED))
    }

    @Test
    fun `awt middle mouse drag clears only after middle button release`() {
        assertFalse(
            shouldClearAwtMiddleMouseDrag(
                eventId = MouseEvent.MOUSE_DRAGGED,
                button = MouseEvent.NOBUTTON,
                isMiddleMousePressed = true,
            ),
        )
        assertTrue(
            shouldClearAwtMiddleMouseDrag(
                eventId = MouseEvent.MOUSE_RELEASED,
                button = MouseEvent.BUTTON2,
                isMiddleMousePressed = false,
            ),
        )
    }

    @Test
    fun `desktop side mouse buttons map to browser style navigation`() {
        assertEquals(DesktopMouseNavigationDirection.Back, desktopMouseNavigationDirection(MouseEvent.MOUSE_PRESSED, 4))
        assertEquals(DesktopMouseNavigationDirection.Back, desktopMouseNavigationDirection(MouseEvent.MOUSE_PRESSED, 8))
        assertEquals(DesktopMouseNavigationDirection.Forward, desktopMouseNavigationDirection(MouseEvent.MOUSE_PRESSED, 5))
        assertEquals(DesktopMouseNavigationDirection.Forward, desktopMouseNavigationDirection(MouseEvent.MOUSE_PRESSED, 9))
        assertNull(desktopMouseNavigationDirection(MouseEvent.MOUSE_RELEASED, 4))
        assertNull(desktopMouseNavigationDirection(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON2))
    }

    @Test
    fun `evdev mouse discovery finds mouse event handlers and ignores non mouse blocks`() {
        val devices =
            discoverDesktopEvdevMouseDevices(
                """
                I: Bus=0003 Vendor=248a Product=8514 Version=0111
                N: Name="Telink VGN S99 Mouse"
                P: Phys=usb-0000:00:14.0-1/input0
                H: Handlers=event10 mouse0

                I: Bus=0003 Vendor=04f2 Product=b6be Version=0111
                N: Name="Laptop Keyboard"
                H: Handlers=sysrq kbd event4

                I: Bus=0003 Vendor=1bcf Product=0005 Version=0111
                N: Name="SSCYPL Wireless-Receiver"
                H: Handlers=mouse1 event11
                """.trimIndent(),
            )

        assertEquals(
            listOf(
                DesktopEvdevInputDevice(path = "/dev/input/event10", name = "Telink VGN S99 Mouse"),
                DesktopEvdevInputDevice(path = "/dev/input/event11", name = "SSCYPL Wireless-Receiver"),
            ),
            devices,
        )
    }

    @Test
    fun `evdev input event parses native 64 bit linux input event layout`() {
        val buffer =
            ByteBuffer
                .allocate(EVDEV_INPUT_EVENT_SIZE)
                .order(ByteOrder.nativeOrder())
                .apply {
                    position(16)
                    putShort(EV_REL.toShort())
                    putShort(REL_X.toShort())
                    putInt(-12)
                }.array()

        assertEquals(EvdevInputEvent(type = EV_REL, code = REL_X, value = -12), parseEvdevInputEvent(buffer))
        assertNull(parseEvdevInputEvent(ByteArray(EVDEV_INPUT_EVENT_SIZE - 1)))
    }

    @Test
    fun `evdev middle mouse drag emits horizontal delta only while middle button is held`() {
        val state = DesktopEvdevMouseDeviceState()

        assertNull(state.handleMiddleMouseDrag(EvdevInputEvent(type = EV_REL, code = REL_X, value = 8)))
        assertNull(state.handleMiddleMouseDrag(EvdevInputEvent(type = EV_KEY, code = BTN_MIDDLE, value = 1)))
        assertEquals(8f, state.handleMiddleMouseDrag(EvdevInputEvent(type = EV_REL, code = REL_X, value = 8)))
        assertEquals(-7f, state.handleMiddleMouseDrag(EvdevInputEvent(type = EV_REL, code = REL_X, value = -7)))
        assertNull(state.handleMiddleMouseDrag(EvdevInputEvent(type = EV_REL, code = REL_X, value = 0)))
        assertNull(state.handleMiddleMouseDrag(EvdevInputEvent(type = EV_KEY, code = BTN_MIDDLE, value = 0)))
        assertNull(state.handleMiddleMouseDrag(EvdevInputEvent(type = EV_REL, code = REL_X, value = 8)))
    }

    @Test
    fun `evdev side mouse buttons map to browser style navigation`() {
        assertEquals(DesktopMouseNavigationDirection.Back, evdevMouseNavigationDirection(EvdevInputEvent(EV_KEY, BTN_SIDE, 1)))
        assertEquals(DesktopMouseNavigationDirection.Back, evdevMouseNavigationDirection(EvdevInputEvent(EV_KEY, BTN_BACK, 1)))
        assertEquals(DesktopMouseNavigationDirection.Back, evdevMouseNavigationDirection(EvdevInputEvent(EV_KEY, KEY_BACK, 1)))
        assertEquals(DesktopMouseNavigationDirection.Forward, evdevMouseNavigationDirection(EvdevInputEvent(EV_KEY, BTN_EXTRA, 1)))
        assertEquals(DesktopMouseNavigationDirection.Forward, evdevMouseNavigationDirection(EvdevInputEvent(EV_KEY, BTN_FORWARD, 1)))
        assertEquals(DesktopMouseNavigationDirection.Forward, evdevMouseNavigationDirection(EvdevInputEvent(EV_KEY, KEY_FORWARD, 1)))
        assertNull(evdevMouseNavigationDirection(EvdevInputEvent(EV_KEY, BTN_SIDE, 0)))
        assertNull(evdevMouseNavigationDirection(EvdevInputEvent(EV_REL, REL_X, 1)))
    }

    @Test
    fun `click away dismisses only primary taps within touch slop`() {
        assertTrue(
            shouldDismissClickAway(
                isPrimaryButton = true,
                maxDistancePx = 4f,
                touchSlopPx = 18f,
            ),
        )
        assertFalse(
            shouldDismissClickAway(
                isPrimaryButton = true,
                maxDistancePx = 24f,
                touchSlopPx = 18f,
            ),
        )
        assertFalse(
            shouldDismissClickAway(
                isPrimaryButton = false,
                maxDistancePx = 4f,
                touchSlopPx = 18f,
            ),
        )
    }
}
