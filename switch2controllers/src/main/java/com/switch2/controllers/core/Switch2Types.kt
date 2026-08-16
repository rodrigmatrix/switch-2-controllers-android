package com.switch2.controllers.core

object Switch2ControllerType {
    const val UNKNOWN: Byte = 0x00
    const val XBOX: Byte = 0x01
    const val PLAYSTATION: Byte = 0x02
    const val NINTENDO: Byte = 0x03
}

object Switch2MotionType {
    const val ACCELEROMETER: Byte = 0x01
    const val GYROSCOPE: Byte = 0x02
}

object Switch2Capabilities {
    const val ANALOG_TRIGGERS: Short = 0x01
    const val RUMBLE: Short = 0x02
    const val TRIGGER_RUMBLE: Short = 0x04
    const val TOUCHPAD: Short = 0x08
    const val ACCELEROMETER: Short = 0x10
    const val GYROSCOPE: Short = 0x20
    const val BATTERY_STATE: Short = 0x40
    const val RGB_LED: Short = 0x80
}
