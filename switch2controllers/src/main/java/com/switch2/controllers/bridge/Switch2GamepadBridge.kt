package com.switch2.controllers.bridge

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.switch2.controllers.core.Switch2ButtonFlags
import com.switch2.controllers.core.Switch2ControllerState

class Switch2GamepadBridge {

    fun buildKeyEvents(
        state: Switch2ControllerState,
        previousFlags: Int
    ): List<KeyEvent> {
        val events = mutableListOf<KeyEvent>()
        val current = state.buttonFlags
        val changed = current xor previousFlags
        val now = SystemClock.uptimeMillis()

        fun check(flag: Int, keyCode: Int) {
            if ((changed and flag) != 0) {
                val action = if ((current and flag) != 0) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
                events.add(KeyEvent(now, now, action, keyCode, 0, 0, state.controllerId, 0, 0, InputDevice.SOURCE_GAMEPAD))
            }
        }

        check(Switch2ButtonFlags.A_FLAG, KeyEvent.KEYCODE_BUTTON_A)
        check(Switch2ButtonFlags.B_FLAG, KeyEvent.KEYCODE_BUTTON_B)
        check(Switch2ButtonFlags.X_FLAG, KeyEvent.KEYCODE_BUTTON_X)
        check(Switch2ButtonFlags.Y_FLAG, KeyEvent.KEYCODE_BUTTON_Y)
        check(Switch2ButtonFlags.LB_FLAG, KeyEvent.KEYCODE_BUTTON_L1)
        check(Switch2ButtonFlags.RB_FLAG, KeyEvent.KEYCODE_BUTTON_R1)
        check(Switch2ButtonFlags.BACK_FLAG, KeyEvent.KEYCODE_BUTTON_SELECT)
        check(Switch2ButtonFlags.PLAY_FLAG, KeyEvent.KEYCODE_BUTTON_START)
        check(Switch2ButtonFlags.LS_CLK_FLAG, KeyEvent.KEYCODE_BUTTON_THUMBL)
        check(Switch2ButtonFlags.RS_CLK_FLAG, KeyEvent.KEYCODE_BUTTON_THUMBR)
        check(Switch2ButtonFlags.SPECIAL_BUTTON_FLAG, KeyEvent.KEYCODE_BUTTON_MODE)
        check(Switch2ButtonFlags.UP_FLAG, KeyEvent.KEYCODE_DPAD_UP)
        check(Switch2ButtonFlags.DOWN_FLAG, KeyEvent.KEYCODE_DPAD_DOWN)
        check(Switch2ButtonFlags.LEFT_FLAG, KeyEvent.KEYCODE_DPAD_LEFT)
        check(Switch2ButtonFlags.RIGHT_FLAG, KeyEvent.KEYCODE_DPAD_RIGHT)

        return events
    }

    fun buildMotionEvent(
        state: Switch2ControllerState
    ): MotionEvent? {
        val now = SystemClock.uptimeMillis()
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_UNKNOWN
            }
        )

        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = state.leftStickX
                y = state.leftStickY
                setAxisValue(MotionEvent.AXIS_X, state.leftStickX)
                setAxisValue(MotionEvent.AXIS_Y, state.leftStickY)
                setAxisValue(MotionEvent.AXIS_Z, state.rightStickX)
                setAxisValue(MotionEvent.AXIS_RZ, state.rightStickY)
                setAxisValue(MotionEvent.AXIS_LTRIGGER, state.leftTrigger)
                setAxisValue(MotionEvent.AXIS_RTRIGGER, state.rightTrigger)
                setAxisValue(MotionEvent.AXIS_BRAKE, state.leftTrigger)
                setAxisValue(MotionEvent.AXIS_GAS, state.rightTrigger)
                setAxisValue(
                    MotionEvent.AXIS_HAT_X,
                    when {
                        (state.buttonFlags and Switch2ButtonFlags.LEFT_FLAG) != 0 -> -1f
                        (state.buttonFlags and Switch2ButtonFlags.RIGHT_FLAG) != 0 -> 1f
                        else -> 0f
                    }
                )
                setAxisValue(
                    MotionEvent.AXIS_HAT_Y,
                    when {
                        (state.buttonFlags and Switch2ButtonFlags.UP_FLAG) != 0 -> -1f
                        (state.buttonFlags and Switch2ButtonFlags.DOWN_FLAG) != 0 -> 1f
                        else -> 0f
                    }
                )
            }
        )

        return MotionEvent.obtain(
            now,
            now,
            MotionEvent.ACTION_MOVE,
            1,
            properties,
            coords,
            0,
            0,
            0.1f,
            0.1f,
            state.controllerId,
            0,
            InputDevice.SOURCE_JOYSTICK,
            0
        )
    }
}
