package com.switch2.controllers.mappings

import android.content.Context
import android.content.SharedPreferences
import com.switch2.controllers.R
import com.switch2.controllers.core.Switch2ButtonFlags
import com.switch2.controllers.core.Switch2Constants

object Switch2ControllerMappings {
    const val PREFS_NAME = "switch2_controller_prefs"
    const val PREF_PAIRED_CONTROLLERS = "paired_ble_controller_macs"
    const val LEGACY_PREF_PAIRED_CONTROLLER = "paired_ble_controller_mac"
    const val PREF_COMBINE_JOYCONS = "combine_joycons"

    const val TARGET_DEFAULT = -1
    const val TARGET_NONE = 0

    const val SOURCE_GL = "gl"
    const val SOURCE_GR = "gr"

    data class SourceButton(
        val id: String,
        val label: String,
        val rawMask: Int,
        val defaultTarget: Int,
        val editableRawMask: Boolean = false,
    )

    data class TargetButton(
        val label: String,
        val flag: Int,
    )

    val targetButtons = listOf(
        TargetButton("Default", TARGET_DEFAULT),
        TargetButton("Unmapped", TARGET_NONE),
        TargetButton("A", Switch2ButtonFlags.A_FLAG),
        TargetButton("B", Switch2ButtonFlags.B_FLAG),
        TargetButton("X", Switch2ButtonFlags.X_FLAG),
        TargetButton("Y", Switch2ButtonFlags.Y_FLAG),
        TargetButton("D-pad Up", Switch2ButtonFlags.UP_FLAG),
        TargetButton("D-pad Down", Switch2ButtonFlags.DOWN_FLAG),
        TargetButton("D-pad Left", Switch2ButtonFlags.LEFT_FLAG),
        TargetButton("D-pad Right", Switch2ButtonFlags.RIGHT_FLAG),
        TargetButton("L", Switch2ButtonFlags.LB_FLAG),
        TargetButton("R", Switch2ButtonFlags.RB_FLAG),
        TargetButton("L3 (Left Stick Click)", Switch2ButtonFlags.LS_CLK_FLAG),
        TargetButton("R3 (Right Stick Click)", Switch2ButtonFlags.RS_CLK_FLAG),
        TargetButton("Minus / Back / Select", Switch2ButtonFlags.BACK_FLAG),
        TargetButton("Plus / Start", Switch2ButtonFlags.PLAY_FLAG),
        TargetButton("Home / Xbox / PS", Switch2ButtonFlags.SPECIAL_BUTTON_FLAG),
        TargetButton("Touchpad", Switch2ButtonFlags.TOUCHPAD_FLAG),
        TargetButton("Capture / Share / Misc", Switch2ButtonFlags.MISC_FLAG),
        TargetButton("Paddle 1", Switch2ButtonFlags.PADDLE1_FLAG),
        TargetButton("Paddle 2", Switch2ButtonFlags.PADDLE2_FLAG),
        TargetButton("Paddle 3", Switch2ButtonFlags.PADDLE3_FLAG),
        TargetButton("Paddle 4", Switch2ButtonFlags.PADDLE4_FLAG),
    )

    fun sourceButtons(context: Context, address: String): List<SourceButton> {
        val prefs = getPrefs(context)
        return listOf(
            SourceButton("a", "A", 0x00000008, Switch2ButtonFlags.A_FLAG),
            SourceButton("b", "B", 0x00000004, Switch2ButtonFlags.B_FLAG),
            SourceButton("x", "X", 0x00000002, Switch2ButtonFlags.X_FLAG),
            SourceButton("y", "Y", 0x00000001, Switch2ButtonFlags.Y_FLAG),
            SourceButton("l", "L", 0x00400000, Switch2ButtonFlags.LB_FLAG),
            SourceButton("r", "R", 0x00000040, Switch2ButtonFlags.RB_FLAG),
            SourceButton("zl", "ZL", 0x00800000, TARGET_NONE),
            SourceButton("zr", "ZR", 0x00000080, TARGET_NONE),
            SourceButton("minus", "Minus", 0x00000100, Switch2ButtonFlags.BACK_FLAG),
            SourceButton("plus", "Plus", 0x00000200, Switch2ButtonFlags.PLAY_FLAG),
            SourceButton("left_stick", "Left stick click", 0x00000800, Switch2ButtonFlags.LS_CLK_FLAG),
            SourceButton("right_stick", "Right stick click", 0x00000400, Switch2ButtonFlags.RS_CLK_FLAG),
            SourceButton("home", "Home", 0x00001000, Switch2ButtonFlags.SPECIAL_BUTTON_FLAG),
            SourceButton("capture", "Capture", 0x00002000, Switch2ButtonFlags.MISC_FLAG),
            SourceButton("gamechat", "GameChat", 0x00004000, Switch2ButtonFlags.MISC_FLAG),
            SourceButton("dpad_up", "D-pad Up", 0x00020000, Switch2ButtonFlags.UP_FLAG),
            SourceButton("dpad_down", "D-pad Down", 0x00010000, Switch2ButtonFlags.DOWN_FLAG),
            SourceButton("dpad_left", "D-pad Left", 0x00080000, Switch2ButtonFlags.LEFT_FLAG),
            SourceButton("dpad_right", "D-pad Right", 0x00040000, Switch2ButtonFlags.RIGHT_FLAG),
            SourceButton(SOURCE_GL, "GL (Rear Left)", rawMaskFor(prefs, address, SOURCE_GL), TARGET_NONE, editableRawMask = true),
            SourceButton(SOURCE_GR, "GR (Rear Right)", rawMaskFor(prefs, address, SOURCE_GR), TARGET_NONE, editableRawMask = true),
        )
    }

    fun mapButtons(context: Context, address: String, rawButtons: Int): Int {
        val prefs = getPrefs(context)
        var mappedFlags = 0
        for (source in sourceButtons(context, address)) {
            if (source.rawMask != 0 && (rawButtons and source.rawMask) != 0) {
                val value = prefs.getInt(mappingKey(address, source.id), TARGET_DEFAULT)
                mappedFlags = mappedFlags or if (value == TARGET_DEFAULT) source.defaultTarget else value
            }
        }
        return mappedFlags
    }

    fun supportedButtonFlags(): Int {
        return targetButtons
            .filter { it.flag != TARGET_DEFAULT && it.flag != TARGET_NONE }
            .fold(0) { acc, target -> acc or target.flag }
    }

    fun targetFor(context: Context, address: String, source: SourceButton): Int {
        val value = getPrefs(context).getInt(mappingKey(address, source.id), TARGET_DEFAULT)
        return if (value == TARGET_DEFAULT) source.defaultTarget else value
    }

    fun setTarget(context: Context, address: String, sourceId: String, targetFlag: Int) {
        getPrefs(context)
            .edit()
            .putInt(mappingKey(address, sourceId), targetFlag)
            .apply()
    }

    fun setRawMask(context: Context, address: String, sourceId: String, mask: Int) {
        getPrefs(context)
            .edit()
            .putInt(rawMaskKey(address, sourceId), mask)
            .apply()
    }

    fun getPairedControllers(context: Context): List<String> {
        val prefs = getPrefs(context)
        try {
            val serialized = prefs.getString(PREF_PAIRED_CONTROLLERS, null)
            if (serialized != null) {
                return serialized.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
        } catch (_: ClassCastException) {
            try {
                val set = prefs.getStringSet(PREF_PAIRED_CONTROLLERS, null)
                if (set != null) {
                    val list = set.toList()
                    prefs.edit().putString(PREF_PAIRED_CONTROLLERS, list.joinToString(",")).apply()
                    return list
                }
            } catch (_: Exception) {}
        }

        try {
            val legacy = prefs.getString(LEGACY_PREF_PAIRED_CONTROLLER, null)
                ?: context.getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)
                    .getString(LEGACY_PREF_PAIRED_CONTROLLER, null)

            if (!legacy.isNullOrEmpty()) return listOf(legacy)
        } catch (_: Exception) {}

        try {
            val blePrefs = context.getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)
            val bleSet = blePrefs.getStringSet(PREF_PAIRED_CONTROLLERS, null)
            if (bleSet != null) return bleSet.toList()
        } catch (_: Exception) {}

        return emptyList()
    }

    fun addPairedController(context: Context, address: String, name: String, productId: Int) {
        val current = getPairedControllers(context).toMutableList()
        if (!current.contains(address)) {
            current.add(address)
            getPrefs(context)
                .edit()
                .putString(PREF_PAIRED_CONTROLLERS, current.joinToString(","))
                .putString(nameKey(address), name)
                .putInt(productIdKey(address), productId)
                .apply()
        }
    }

    fun removePairedController(context: Context, address: String) {
        val current = getPairedControllers(context).toMutableList()
        current.remove(address)
        getPrefs(context)
            .edit()
            .putString(PREF_PAIRED_CONTROLLERS, current.joinToString(","))
            .remove(nameKey(address))
            .remove(productIdKey(address))
            .remove(sensitivityKey(address))
            .remove(colorKey(address))
            .remove(colorHexKey(address))
            .apply()
    }

    fun controllerName(context: Context, address: String): String {
        return getPrefs(context).getString(nameKey(address), null) ?: "Switch 2 Controller"
    }

    fun controllerProductId(context: Context, address: String): Int {
        return getPrefs(context).getInt(productIdKey(address), Switch2Constants.PRODUCT_PRO_CONTROLLER_2)
    }

    fun setControllerColor(
        context: Context,
        address: String,
        colorName: String,
        rgbHex: String? = null,
        isUserManual: Boolean = false,
    ) {
        val editor = getPrefs(context).edit().putString(colorKey(address), colorName)
        if (rgbHex != null) {
            editor.putString(colorHexKey(address), rgbHex)
        }
        if (isUserManual) {
            editor.putBoolean(userColorOverrideKey(address), true)
        }
        editor.apply()
    }

    fun isUserColorOverride(context: Context, address: String): Boolean {
        return getPrefs(context).getBoolean(userColorOverrideKey(address), false)
    }

    fun getControllerColor(context: Context, address: String, productId: Int): String {
        val prefs = getPrefs(context)
        val saved = prefs.getString(colorKey(address), null)
        if (!saved.isNullOrEmpty()) {
            return saved
        }
        return when {
            Switch2Constants.isJoyConLeft(productId) -> Switch2Constants.COLOR_PURPLE
            Switch2Constants.isJoyConRight(productId) -> Switch2Constants.COLOR_GREEN
            else -> Switch2Constants.COLOR_DEFAULT
        }
    }

    fun colorNameFromRgb(r: Int, g: Int, b: Int, isLeft: Boolean): String {
        // Blue / Neon Cyan (e.g. #0AB9E6, #00B3E6)
        if (b > 150 && b > r + 50) return Switch2Constants.COLOR_BLUE
        // Green / Neon Green (e.g. #80CD57, #74D162, #00E676)
        if (g > 130 && g > r + 15 && g > b + 15) return Switch2Constants.COLOR_GREEN
        // Purple / Plum / Magenta (e.g. #B3042F, #8B004F, #8B6EB8, #A366CC)
        if ((r > 120 && g < 30 && b >= 20) || (r > 90 && b > 120 && g < b - 15)) return Switch2Constants.COLOR_PURPLE
        // Red / Neon Red (e.g. #FF3C28, #E60012, #FF4500)
        if (r > 160 && (g >= 30 || r > 210) && r > g + 40 && r > b + 40) return Switch2Constants.COLOR_RED
        return if (isLeft) Switch2Constants.COLOR_PURPLE else Switch2Constants.COLOR_GREEN
    }

    fun getDrawableResForColor(color: String, productId: Int): Int {
        return when {
            Switch2Constants.isProController(productId) -> R.drawable.ic_controller_pro_2
            Switch2Constants.isJoyConLeft(productId) -> {
                when (color) {
                    Switch2Constants.COLOR_PURPLE -> R.drawable.ic_joycon_2_left_purple
                    Switch2Constants.COLOR_BLUE -> R.drawable.ic_joycon_2_left_blue
                    Switch2Constants.COLOR_RED -> R.drawable.ic_joycon_2_left_red
                    Switch2Constants.COLOR_GREEN -> R.drawable.ic_joycon_2_left_green
                    else -> R.drawable.ic_joycon_2_left_purple
                }
            }
            Switch2Constants.isJoyConRight(productId) -> {
                when (color) {
                    Switch2Constants.COLOR_GREEN -> R.drawable.ic_joycon_2_right_green
                    Switch2Constants.COLOR_RED -> R.drawable.ic_joycon_2_right_red
                    Switch2Constants.COLOR_BLUE -> R.drawable.ic_joycon_2_right_blue
                    Switch2Constants.COLOR_PURPLE -> R.drawable.ic_joycon_2_right_purple
                    else -> R.drawable.ic_joycon_2_right_green
                }
            }
            else -> R.drawable.ic_controller_pro_2
        }
    }

    fun getControllerDrawableRes(context: Context, address: String, productId: Int): Int {
        val color = getControllerColor(context, address, productId)
        return getDrawableResForColor(color, productId)
    }

    fun combineJoyCons(context: Context): Boolean {
        val prefs = getPrefs(context)
        try {
            if (prefs.contains(PREF_COMBINE_JOYCONS)) {
                return prefs.getBoolean(PREF_COMBINE_JOYCONS, true)
            }
        } catch (_: Exception) {}
        return try {
            context.getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)
                .getBoolean("combine_joycons", true)
        } catch (_: Exception) {
            true
        }
    }

    fun setCombineJoyCons(context: Context, combine: Boolean) {
        getPrefs(context)
            .edit()
            .putBoolean(PREF_COMBINE_JOYCONS, combine)
            .apply()
    }

    fun stickSensitivity(context: Context, address: String): Float {
        val prefs = getPrefs(context)
        try {
            if (prefs.contains(sensitivityKey(address))) {
                return prefs.getFloat(sensitivityKey(address), 1.30f)
            }
        } catch (_: Exception) {}
        return try {
            context.getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)
                .getFloat("switch2_${address.safeKey()}_stick_sensitivity", 1.30f)
        } catch (_: Exception) {
            1.30f
        }
    }

    fun setStickSensitivity(context: Context, address: String, sensitivity: Float) {
        getPrefs(context)
            .edit()
            .putFloat(sensitivityKey(address), sensitivity)
            .apply()
    }

    fun rawMaskText(context: Context, address: String, sourceId: String): String {
        val value = getPrefs(context).getInt(rawMaskKey(address, sourceId), 0)
        return if (value == 0) "" else "0x${value.toUInt().toString(16)}"
    }

    fun parseRawMask(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        return if (trimmed.startsWith("0x", ignoreCase = true)) {
            trimmed.substring(2).toUInt(16).toInt()
        } else {
            trimmed.toUInt(16).toInt()
        }
    }

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun rawMaskFor(prefs: SharedPreferences, address: String, sourceId: String): Int {
        return prefs.getInt(rawMaskKey(address, sourceId), 0)
    }

    private fun mappingKey(address: String, sourceId: String): String {
        return "switch2_${address.safeKey()}_${sourceId}_mapping"
    }

    private fun rawMaskKey(address: String, sourceId: String): String {
        return "switch2_${address.safeKey()}_${sourceId}_raw_mask"
    }

    private fun nameKey(address: String): String {
        return "switch2_${address.safeKey()}_name"
    }

    private fun productIdKey(address: String): String {
        return "switch2_${address.safeKey()}_product_id"
    }

    private fun sensitivityKey(address: String): String {
        return "switch2_${address.safeKey()}_stick_sensitivity"
    }

    private fun colorKey(address: String): String = "switch2_${address.safeKey()}_color"
    private fun colorHexKey(address: String): String = "switch2_${address.safeKey()}_color_hex"
    private fun userColorOverrideKey(address: String): String = "switch2_${address.safeKey()}_user_color_override"

    private fun String.safeKey(): String = replace(":", "").lowercase()
}
