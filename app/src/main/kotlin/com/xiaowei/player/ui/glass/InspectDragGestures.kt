package com.xiaowei.player.ui.glass

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.util.fastFirstOrNull
import kotlin.math.abs

internal suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    inspectDragGestures(
        directionalLock = false,
        onDragStart = onDragStart,
        onDragEnd = onDragEnd,
        onDragCancel = onDragCancel,
        onDrag = onDrag
    )
}

internal suspend fun PointerInputScope.inspectDragGestures(
    directionalLock: Boolean,
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)

        val down = awaitFirstDown(false)
        val drag = initialDown

        val slop = viewConfiguration.touchSlop
        var lockAxis = 0
        var accX = 0f
        var accY = 0f

        onDragStart(down)
        onDrag(drag, Offset.Zero)
        val upEvent =
            drag(
                pointerId = drag.id,
                onDrag = { change ->
                    val delta = change.positionChange()
                    if (!directionalLock) {
                        onDrag(change, delta)
                    } else {
                        if (lockAxis == 0) {
                            accX += delta.x
                            accY += delta.y
                            if (abs(accX) > slop || abs(accY) > slop) {
                                lockAxis = if (abs(accX) * 1.2f >= abs(accY)) 1 else 2
                            }
                        }
                        val filtered = when (lockAxis) {
                            1 -> Offset(delta.x, 0f)
                            2 -> Offset(0f, delta.y)
                            else -> Offset.Zero
                        }
                        onDrag(change, filtered)
                    }
                }
            )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) {
        return null
    }
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) {
            return null
        }
        if (change.changedToUpIgnoreConsumed()) {
            return change
        }
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else {
            val hasDragged = dragEvent.previousPosition != dragEvent.position
            if (hasDragged) {
                return dragEvent
            }
        }
    }
}
