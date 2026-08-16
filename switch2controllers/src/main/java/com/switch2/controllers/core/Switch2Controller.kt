package com.switch2.controllers.core

data class Switch2ControllerState(
    val controllerId: Int,
    val buttonFlags: Int = 0,
    val leftStickX: Float = 0f,
    val leftStickY: Float = 0f,
    val rightStickX: Float = 0f,
    val rightStickY: Float = 0f,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

data class Switch2MotionState(
    val controllerId: Int,
    val motionType: Byte,
    val x: Float,
    val y: Float,
    val z: Float,
    val timestamp: Long = System.currentTimeMillis()
)

interface Switch2ControllerListener {
    fun onControllerAdded(controller: Switch2Controller)
    fun onControllerRemoved(controller: Switch2Controller)
    fun onControllerStateReported(state: Switch2ControllerState)
    fun onControllerMotionReported(motion: Switch2MotionState)
}

abstract class Switch2Controller(
    val controllerId: Int,
    val vendorId: Int = Switch2Constants.NINTENDO_VENDOR_ID,
    val productId: Int = Switch2Constants.PRODUCT_PRO_CONTROLLER_2,
    val name: String = Switch2Constants.controllerNameForProduct(productId),
    var listener: Switch2ControllerListener? = null
) {
    var type: Byte = Switch2ControllerType.NINTENDO
    var capabilities: Short = (
        Switch2Capabilities.GYROSCOPE.toInt() or
            Switch2Capabilities.ACCELEROMETER.toInt() or
            Switch2Capabilities.RUMBLE.toInt()
    ).toShort()
    var supportedButtonFlags: Int = 0

    abstract fun start(): Boolean
    abstract fun stop()
    abstract fun rumble(lowFreqMotor: Short, highFreqMotor: Short)
    open fun rumbleTriggers(leftTrigger: Short, rightTrigger: Short) = Unit

    fun notifyDeviceAdded() {
        listener?.onControllerAdded(this)
    }

    fun notifyDeviceRemoved() {
        listener?.onControllerRemoved(this)
    }

    fun reportState(state: Switch2ControllerState) {
        listener?.onControllerStateReported(state)
    }

    fun reportMotion(motion: Switch2MotionState) {
        listener?.onControllerMotionReported(motion)
    }
}
