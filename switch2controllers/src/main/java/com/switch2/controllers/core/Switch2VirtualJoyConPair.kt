package com.switch2.controllers.core

import com.switch2.controllers.mappings.Switch2ControllerMappings

class Switch2VirtualJoyConPair(
    controllerId: Int,
    listener: Switch2ControllerListener? = null,
    private val rumbleCallback: (lowFreq: Short, highFreq: Short) -> Unit = { _, _ -> }
) : Switch2Controller(
    controllerId = controllerId,
    vendorId = Switch2Constants.NINTENDO_VENDOR_ID,
    productId = Switch2Constants.PRODUCT_PRO_CONTROLLER_2,
    name = "Joy-Con 2 (Combined Pair)",
    listener = listener
) {
    init {
        type = Switch2ControllerType.NINTENDO
        capabilities = (
            Switch2Capabilities.GYROSCOPE.toInt() or
                Switch2Capabilities.ACCELEROMETER.toInt() or
                Switch2Capabilities.RUMBLE.toInt()
        ).toShort()
        supportedButtonFlags = Switch2ControllerMappings.supportedButtonFlags()
    }

    override fun start(): Boolean = true

    override fun stop() = Unit

    override fun rumble(lowFreqMotor: Short, highFreqMotor: Short) {
        rumbleCallback(lowFreqMotor, highFreqMotor)
    }

    override fun rumbleTriggers(leftTrigger: Short, rightTrigger: Short) {
        rumbleCallback(leftTrigger, rightTrigger)
    }
}
