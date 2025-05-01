package ca.amandeep.path

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.os.Vibrator
import android.view.MotionEvent
import com.github.ajalt.timberkt.d
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DeveloperMenuTrigger(
    private val context: Context,
    private val onTriggered: () -> Unit,
    private val holdDuration: Duration = 2.seconds,
    private val waitTimeoutDuration: Duration = 25.seconds,
    private val cornerThresholdPercent: Float = 0.25f,
) {
    private enum class TriggerState { IDLE, HOLDING_FIRST, WAITING_SECOND, HOLDING_SECOND }

    private enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    private var currentState: TriggerState = TriggerState.IDLE
    private val activePointers = mutableMapOf<Int, PointF>()
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var corners = mapOf<Corner, RectF>()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var holdJob: Job? = null
    private var waitTimeoutJob: Job? = null

    private val firstComboCorners = setOf(Corner.TOP_LEFT, Corner.BOTTOM_RIGHT)
    private val secondComboCorners = setOf(Corner.TOP_RIGHT, Corner.BOTTOM_LEFT)

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator by lazy {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun setScreenDimensions(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        screenWidth = width
        screenHeight = height
        val cornerSizeX = screenWidth * cornerThresholdPercent
        val cornerSizeY = screenHeight * cornerThresholdPercent
        corners = mapOf(
            Corner.TOP_LEFT to RectF(0f, 0f, cornerSizeX, cornerSizeY),
            Corner.TOP_RIGHT to RectF(screenWidth - cornerSizeX, 0f, screenWidth.toFloat(), cornerSizeY),
            Corner.BOTTOM_LEFT to RectF(0f, screenHeight - cornerSizeY, cornerSizeX, screenHeight.toFloat()),
            Corner.BOTTOM_RIGHT to RectF(
                screenWidth - cornerSizeX,
                screenHeight - cornerSizeY,
                screenWidth.toFloat(),
                screenHeight.toFloat(),
            ),
        )
        d { "Screen dimensions set: ($screenWidth, $screenHeight)" }
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (screenWidth == 0 || screenHeight == 0) return false

        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                activePointers[pointerId] = PointF(event.getX(pointerIndex), event.getY(pointerIndex))
                checkState()
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    activePointers[id]?.set(event.getX(i), event.getY(i))
                }
                // Reset immediately if fingers slide out during holds
                if (currentState in listOf(TriggerState.HOLDING_FIRST, TriggerState.HOLDING_SECOND) &&
                    !areRequiredCornersHeld()
                ) {
                    d { "Pointer slid out during $currentState. Resetting." }
                    reset()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                activePointers.remove(pointerId)
                // If a required pointer was lifted during hold, reset
                if (currentState in listOf(TriggerState.HOLDING_FIRST, TriggerState.HOLDING_SECOND) &&
                    !areRequiredCornersHeld()
                ) {
                    d { "Required pointer lifted during $currentState. Resetting." }
                    reset()
                } else if (activePointers.isEmpty() && currentState != TriggerState.WAITING_SECOND) {
                    // Reset if all fingers are lifted unless specifically waiting
                    d { "All pointers up outside WAITING_SECOND. Resetting." }
                    reset()
                } else {
                    // Check state if fingers are lifted in WAITING_SECOND or if some remain down
                    checkState()
                }
            }
            MotionEvent.ACTION_CANCEL -> reset()
        }

        return currentState != TriggerState.IDLE
    }

    private fun getPressedCorners(): Set<Corner> =
        activePointers.values.mapNotNull { point ->
            corners.entries.find { (_, rect) -> rect.contains(point.x, point.y) }?.key
        }.toSet()

    private fun areRequiredCornersHeld(): Boolean {
        val pressed = getPressedCorners()
        val required = when (currentState) {
            TriggerState.HOLDING_FIRST -> firstComboCorners
            TriggerState.HOLDING_SECOND -> secondComboCorners
            else -> emptySet()
        }
        // Must have at least 2 pointers and contain all required corners
        return activePointers.size >= 2 && pressed.containsAll(required)
    }

    private fun checkState() {
        val pressedCorners = getPressedCorners()

        when (currentState) {
            TriggerState.IDLE -> {
                if (activePointers.size >= 2 && pressedCorners == firstComboCorners) {
                    startFirstHold()
                }
            }
            TriggerState.WAITING_SECOND -> {
                if (activePointers.size >= 2 && pressedCorners == secondComboCorners) {
                    startSecondHold()
                }
                // Timeout job handles reset if wrong/no corners are pressed for too long
            }
            TriggerState.HOLDING_FIRST, TriggerState.HOLDING_SECOND -> {
                // Hold validity checked in ACTION_MOVE / ACTION_UP
            }
        }
    }

    private fun startFirstHold() {
        d { "Starting first hold (TL+BR)" }
        // Vibrate to indicate first hold.
        vibrate()

        currentState = TriggerState.HOLDING_FIRST
        holdJob?.cancel()
        holdJob = scope.launch {
            delay(holdDuration)
            if (currentState == TriggerState.HOLDING_FIRST) { // Ensure state hasn't changed
                onFirstHoldSuccess()
            }
        }
    }

    private fun onFirstHoldSuccess() {
        d { "First hold success. Waiting for second combo (TR+BL) with timeout $waitTimeoutDuration" }
        // Vibrate to indicate waiting for second combo
        vibrate()

        currentState = TriggerState.WAITING_SECOND
        holdJob = null // Clear hold job
        waitTimeoutJob?.cancel()
        waitTimeoutJob = scope.launch {
            delay(waitTimeoutDuration)
            if (currentState == TriggerState.WAITING_SECOND) {
                d { "Wait timeout expired. Resetting." }
                reset()
            }
        }
    }

    private fun startSecondHold() {
        d { "Starting second hold (TR+BL)" }
        vibrate()
        waitTimeoutJob?.cancel() // Cancel waiting timeout
        waitTimeoutJob = null
        currentState = TriggerState.HOLDING_SECOND
        holdJob?.cancel()
        holdJob = scope.launch {
            delay(holdDuration)
            if (currentState == TriggerState.HOLDING_SECOND) { // Ensure state hasn't changed
                onSecondHoldSuccess()
            }
        }
    }

    private fun onSecondHoldSuccess() {
        d { "Second hold success! Triggering action." }
        vibrate()
        reset() // Reset state after success
        onTriggered()
    }

    private fun vibrate() {
        @Suppress("DEPRECATION")
        vibrator.vibrate(100.milliseconds.inWholeMilliseconds)
    }

    private fun reset() {
        if (currentState != TriggerState.IDLE) {
            d { "Resetting state from $currentState to IDLE." }
        }
        holdJob?.cancel()
        waitTimeoutJob?.cancel()
        holdJob = null
        waitTimeoutJob = null
        activePointers.clear()
        currentState = TriggerState.IDLE
    }

    fun cleanup() {
        d { "Cleaning up trigger." }
        scope.cancel()
        activePointers.clear()
        currentState = TriggerState.IDLE
    }
}
