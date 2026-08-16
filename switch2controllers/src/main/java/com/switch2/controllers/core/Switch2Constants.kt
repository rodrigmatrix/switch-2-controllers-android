package com.switch2.controllers.core

import java.util.UUID

object Switch2Constants {
    const val NINTENDO_VENDOR_ID = 0x057e
    const val NINTENDO_BLUETOOTH_MANUFACTURER_ID = 0x0553

    // Product IDs
    const val PRODUCT_PRO_CONTROLLER_2 = 0x2069
    const val PRODUCT_JOYCON_2_LEFT = 0x2067
    const val PRODUCT_JOYCON_2_RIGHT = 0x2066
    const val PRODUCT_JOYCON_L = 0x2006
    const val PRODUCT_JOYCON_R = 0x2007
    const val PRODUCT_NSO_GAMECUBE_CONTROLLER = 0x2073

    // GATT UUIDs
    val INPUT_REPORT_UUID: UUID = UUID.fromString("ab7de9be-89fe-49ad-828f-118f09df7fd2")
    val VIBRATION_WRITE_JOYCON_R_UUID: UUID = UUID.fromString("fa19b0fb-cd1f-46a7-84a1-bbb09e00c149")
    val VIBRATION_WRITE_JOYCON_L_UUID: UUID = UUID.fromString("289326cb-a471-485d-a8f4-240c14f18241")
    val VIBRATION_WRITE_PRO_CONTROLLER_UUID: UUID = UUID.fromString("cc483f51-9258-427d-a939-630c31f72b05")
    val COMMAND_WRITE_UUID: UUID = UUID.fromString("649d4ac9-8eb7-4e6c-af44-1ea54fe5f005")
    val COMMAND_RESPONSE_UUID: UUID = UUID.fromString("c765a961-d9d8-4d36-a20a-5315b111836a")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Commands & Subcommands
    const val COMMAND_MEMORY = 0x02
    const val SUBCOMMAND_MEMORY_READ = 0x04
    const val CALIBRATION_JOYSTICK_L = 0x0130A8
    const val CALIBRATION_JOYSTICK_R = 0x0130E8
    const val CALIBRATION_COLOR_FACTORY = 0x6050
    const val CALIBRATION_COLOR_SWITCH2 = 0x016050
    const val CALIBRATION_COLOR_USER = 0x8010

    // Joy-Con Colors
    const val COLOR_PURPLE = "purple"
    const val COLOR_GREEN = "green"
    const val COLOR_BLUE = "blue"
    const val COLOR_RED = "red"
    const val COLOR_DEFAULT = "default"

    const val COMMAND_LEDS = 0x09
    const val SUBCOMMAND_LEDS_SET_PLAYER = 0x07
    const val LED_PLAYER_1 = 0x01

    const val COMMAND_FEATURE = 0x0c
    const val SUBCOMMAND_FEATURE_INIT = 0x02
    const val SUBCOMMAND_FEATURE_ENABLE = 0x04
    const val FEATURE_FLAGS = 0x04 or 0x10 or 0x80

    const val COMMAND_PAIR = 0x15
    const val SUBCOMMAND_PAIR_SET_MAC = 0x01
    const val SUBCOMMAND_PAIR_LTK1 = 0x04
    const val SUBCOMMAND_PAIR_LTK2 = 0x02
    const val SUBCOMMAND_PAIR_FINISH = 0x03

    // Vibration / HD Rumble constants
    const val DEFAULT_LOW_FREQUENCY = 0x0e1
    const val DEFAULT_HIGH_FREQUENCY = 0x1e1
    const val MAX_SWITCH_RUMBLE_AMPLITUDE = 800

    fun isSupportedProductId(productId: Int): Boolean {
        return productId == PRODUCT_PRO_CONTROLLER_2 ||
            productId == PRODUCT_JOYCON_2_LEFT ||
            productId == PRODUCT_JOYCON_2_RIGHT ||
            productId == PRODUCT_JOYCON_L ||
            productId == PRODUCT_JOYCON_R ||
            productId == PRODUCT_NSO_GAMECUBE_CONTROLLER
    }

    fun isJoyConLeft(productId: Int): Boolean =
        productId == PRODUCT_JOYCON_2_LEFT || productId == PRODUCT_JOYCON_L

    fun isJoyConRight(productId: Int): Boolean =
        productId == PRODUCT_JOYCON_2_RIGHT || productId == PRODUCT_JOYCON_R

    fun isJoyCon(productId: Int): Boolean =
        isJoyConLeft(productId) || isJoyConRight(productId)

    fun isProController(productId: Int): Boolean =
        productId == PRODUCT_PRO_CONTROLLER_2 || productId == PRODUCT_NSO_GAMECUBE_CONTROLLER

    fun controllerNameForProduct(productId: Int): String {
        return when (productId) {
            PRODUCT_JOYCON_2_LEFT -> "Joy-Con 2 (Left)"
            PRODUCT_JOYCON_2_RIGHT -> "Joy-Con 2 (Right)"
            PRODUCT_JOYCON_L -> "Joy-Con (L)"
            PRODUCT_JOYCON_R -> "Joy-Con (R)"
            PRODUCT_NSO_GAMECUBE_CONTROLLER -> "NSO GameCube Controller"
            PRODUCT_PRO_CONTROLLER_2 -> "Pro Controller 2"
            else -> "Switch 2 Controller"
        }
    }
}
