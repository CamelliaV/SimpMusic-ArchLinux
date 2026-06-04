package com.maxrave.simpmusic.extension

import androidx.compose.ui.geometry.Rect

internal data class DesktopMiddleMouseHorizontalDragTargetHit(
    val targetId: Long,
    val coordinateScale: Float = 1f,
)

internal object DesktopMiddleMouseHorizontalDragTargets {
    private data class Target(
        val id: Long,
        val bounds: Rect,
        val scrollBy: (Float) -> Unit,
    )

    private val lock = Any()
    private val targets = linkedMapOf<Long, Target>()
    private var nextId = 1L

    fun register(): Long =
        synchronized(lock) {
            nextId++
        }

    fun update(
        id: Long,
        bounds: Rect,
        scrollBy: (Float) -> Unit,
    ) {
        synchronized(lock) {
            targets[id] = Target(id = id, bounds = bounds, scrollBy = scrollBy)
        }
    }

    fun unregister(id: Long) {
        synchronized(lock) {
            targets.remove(id)
        }
    }

    fun findTargetAt(
        windowX: Float,
        windowY: Float,
    ): Long? = findTargetIdAt(windowX = windowX, windowY = windowY)

    fun findTargetHitAt(
        windowX: Float,
        windowY: Float,
        uiScale: Float,
    ): DesktopMiddleMouseHorizontalDragTargetHit? {
        val normalizedScale = uiScale.takeIf { it > 0f } ?: 1f
        return findTargetIdAt(windowX = windowX, windowY = windowY)?.let { targetId ->
            DesktopMiddleMouseHorizontalDragTargetHit(targetId = targetId)
        } ?: findTargetIdAt(windowX = windowX * normalizedScale, windowY = windowY * normalizedScale)?.let { targetId ->
            DesktopMiddleMouseHorizontalDragTargetHit(targetId = targetId, coordinateScale = normalizedScale)
        } ?: findTargetIdAt(windowX = windowX / normalizedScale, windowY = windowY / normalizedScale)?.let { targetId ->
            DesktopMiddleMouseHorizontalDragTargetHit(targetId = targetId, coordinateScale = 1f / normalizedScale)
        }
    }

    private fun findTargetIdAt(
        windowX: Float,
        windowY: Float,
    ): Long? =
        synchronized(lock) {
            targets.values
                .lastOrNull { target ->
                    windowX >= target.bounds.left &&
                        windowX <= target.bounds.right &&
                        windowY >= target.bounds.top &&
                        windowY <= target.bounds.bottom
                }?.id
        }

    fun dispatchAt(
        windowX: Float,
        windowY: Float,
        deltaX: Float,
    ): Boolean {
        val targetId = findTargetAt(windowX = windowX, windowY = windowY) ?: return false
        return dispatchToTarget(targetId = targetId, deltaX = deltaX)
    }

    fun dispatchToTarget(
        hit: DesktopMiddleMouseHorizontalDragTargetHit?,
        deltaX: Float,
    ): Boolean {
        if (hit == null) return false
        return dispatchToTarget(targetId = hit.targetId, deltaX = deltaX * hit.coordinateScale)
    }

    fun dispatchToTarget(
        targetId: Long?,
        deltaX: Float,
    ): Boolean {
        if (targetId == null || deltaX == 0f) return false
        val scrollBy =
            synchronized(lock) {
                targets[targetId]?.scrollBy
            } ?: return false
        scrollBy(deltaX)
        return true
    }

    fun clearForTest() {
        synchronized(lock) {
            targets.clear()
            nextId = 1L
        }
    }
}
