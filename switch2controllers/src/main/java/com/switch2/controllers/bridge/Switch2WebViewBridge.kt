package com.switch2.controllers.bridge

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.switch2.controllers.core.Switch2ButtonFlags
import com.switch2.controllers.core.Switch2Controller
import com.switch2.controllers.core.Switch2ControllerListener
import com.switch2.controllers.core.Switch2ControllerState
import com.switch2.controllers.core.Switch2Log
import com.switch2.controllers.core.Switch2MotionState
import com.switch2.controllers.manager.Switch2Manager
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class Switch2WebViewBridge(
    private val manager: Switch2Manager
) : Switch2ControllerListener {

    private val latestStates = ConcurrentHashMap<Int, Switch2ControllerState>()
    private val connectedControllers = ConcurrentHashMap<Int, Switch2Controller>()

    init {
        manager.addListener(this)
        manager.getConnectedControllers().forEach {
            connectedControllers[it.controllerId] = it
        }
    }

    override fun onControllerAdded(controller: Switch2Controller) {
        connectedControllers[controller.controllerId] = controller
        Switch2Log.i("Switch2WebViewBridge: controller added ${controller.controllerId}")
    }

    override fun onControllerRemoved(controller: Switch2Controller) {
        connectedControllers.remove(controller.controllerId)
        latestStates.remove(controller.controllerId)
        Switch2Log.i("Switch2WebViewBridge: controller removed ${controller.controllerId}")
    }

    override fun onControllerStateReported(state: Switch2ControllerState) {
        latestStates[state.controllerId] = state
    }

    override fun onControllerMotionReported(motion: Switch2MotionState) = Unit

    @JavascriptInterface
    fun getGamepadsJson(): String {
        val array = JSONArray()
        for ((id, controller) in connectedControllers) {
            val state = latestStates[id] ?: Switch2ControllerState(id)
            val pad = JSONObject()
            pad.put("id", "Nintendo Switch 2 Controller (${controller.name})")
            pad.put("index", id)
            pad.put("connected", true)
            pad.put("mapping", "standard")
            pad.put("timestamp", state.timestamp)

            // 17 Standard W3C Gamepad Buttons
            // 0: A/Bottom, 1: B/Right, 2: X/Left, 3: Y/Top, 4: L1, 5: R1, 6: L2, 7: R2,
            // 8: Select/Back, 9: Start/Plus, 10: L3, 11: R3, 12: DpadUp, 13: DpadDown,
            // 14: DpadLeft, 15: DpadRight, 16: Guide/Home
            val buttonsArray = JSONArray()
            val flags = state.buttonFlags

            fun addButton(pressed: Boolean, value: Float = if (pressed) 1.0f else 0.0f) {
                val b = JSONObject()
                b.put("pressed", pressed)
                b.put("touched", pressed)
                b.put("value", value)
                buttonsArray.put(b)
            }

            // Note: Standard mapping translates A/B/X/Y positions
            // In W3C standard: button 0 = Bottom (B on Switch or A on Xbox), 1 = Right, 2 = Left, 3 = Top
            addButton((flags and Switch2ButtonFlags.A_FLAG) != 0)          // 0: Bottom
            addButton((flags and Switch2ButtonFlags.B_FLAG) != 0)          // 1: Right
            addButton((flags and Switch2ButtonFlags.X_FLAG) != 0)          // 2: Left
            addButton((flags and Switch2ButtonFlags.Y_FLAG) != 0)          // 3: Top
            addButton((flags and Switch2ButtonFlags.LB_FLAG) != 0)         // 4: L1
            addButton((flags and Switch2ButtonFlags.RB_FLAG) != 0)         // 5: R1
            addButton(state.leftTrigger > 0.1f, state.leftTrigger)         // 6: L2
            addButton(state.rightTrigger > 0.1f, state.rightTrigger)       // 7: R2
            addButton((flags and Switch2ButtonFlags.BACK_FLAG) != 0)       // 8: Back / Minus
            addButton((flags and Switch2ButtonFlags.PLAY_FLAG) != 0)       // 9: Start / Plus
            addButton((flags and Switch2ButtonFlags.LS_CLK_FLAG) != 0)     // 10: L3
            addButton((flags and Switch2ButtonFlags.RS_CLK_FLAG) != 0)     // 11: R3
            addButton((flags and Switch2ButtonFlags.UP_FLAG) != 0)         // 12: Dpad Up
            addButton((flags and Switch2ButtonFlags.DOWN_FLAG) != 0)       // 13: Dpad Down
            addButton((flags and Switch2ButtonFlags.LEFT_FLAG) != 0)       // 14: Dpad Left
            addButton((flags and Switch2ButtonFlags.RIGHT_FLAG) != 0)      // 15: Dpad Right
            addButton((flags and Switch2ButtonFlags.SPECIAL_BUTTON_FLAG) != 0) // 16: Home / Guide
            pad.put("buttons", buttonsArray)

            // Axes: [LeftStickX, LeftStickY, RightStickX, RightStickY]
            val axesArray = JSONArray()
            axesArray.put(state.leftStickX.toDouble())
            axesArray.put(state.leftStickY.toDouble())
            axesArray.put(state.rightStickX.toDouble())
            axesArray.put(state.rightStickY.toDouble())
            pad.put("axes", axesArray)

            array.put(pad)
        }
        return array.toString()
    }

    @JavascriptInterface
    fun vibrate(controllerId: Int, durationMs: Long, weakMagnitude: Double, strongMagnitude: Double) {
        val lowFreq = (strongMagnitude.coerceIn(0.0, 1.0) * 0xff).toInt().toShort()
        val highFreq = (weakMagnitude.coerceIn(0.0, 1.0) * 0xff).toInt().toShort()
        manager.rumble(controllerId, lowFreq, highFreq)
    }

    fun attachToWebView(webView: WebView) {
        webView.addJavascriptInterface(this, "Switch2GamepadNative")
    }

    fun injectPolyfill(webView: WebView) {
        val js = """
            (function() {
                if (window.__switch2GamepadInjected) return;
                window.__switch2GamepadInjected = true;

                const originalGetGamepads = navigator.getGamepads ? navigator.getGamepads.bind(navigator) : () => [];

                navigator.getGamepads = function() {
                    let nativeList = [];
                    try {
                        if (window.Switch2GamepadNative && window.Switch2GamepadNative.getGamepadsJson) {
                            const raw = window.Switch2GamepadNative.getGamepadsJson();
                            nativeList = JSON.parse(raw);
                        }
                    } catch (e) {
                        console.error("Switch2Gamepad getGamepads error:", e);
                    }

                    const original = originalGetGamepads() || [];
                    const result = [...original];

                    nativeList.forEach((pad, index) => {
                        const targetIndex = original.length > index && original[index] ? original.length + index : index;
                        pad.index = targetIndex;
                        pad.vibrationActuator = {
                            type: 'dual-rumble',
                            playEffect: function(type, params) {
                                if (type === 'dual-rumble' && window.Switch2GamepadNative && window.Switch2GamepadNative.vibrate) {
                                    const duration = params.duration || 200;
                                    const weak = params.weakMagnitude || 0.5;
                                    const strong = params.strongMagnitude || 0.5;
                                    window.Switch2GamepadNative.vibrate(pad.index, duration, weak, strong);
                                }
                                return Promise.resolve('complete');
                            },
                            reset: function() {
                                if (window.Switch2GamepadNative && window.Switch2GamepadNative.vibrate) {
                                    window.Switch2GamepadNative.vibrate(pad.index, 0, 0, 0);
                                }
                                return Promise.resolve('complete');
                            }
                        };
                        result[targetIndex] = pad;
                    });

                    return result;
                };

                // Dispatch connected event if controllers already active
                try {
                    if (window.Switch2GamepadNative) {
                        const list = JSON.parse(window.Switch2GamepadNative.getGamepadsJson());
                        list.forEach((pad) => {
                            window.dispatchEvent(new CustomEvent('gamepadconnected', { detail: { gamepad: pad } }));
                        });
                    }
                } catch (_) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    fun detach() {
        manager.removeListener(this)
        connectedControllers.clear()
        latestStates.clear()
    }
}
