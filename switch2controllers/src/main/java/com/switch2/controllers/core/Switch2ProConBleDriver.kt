package com.switch2.controllers.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.switch2.controllers.mappings.Switch2ControllerMappings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedList
import java.util.Queue
import java.util.UUID

class Switch2ProConBleDriver(
    private val context: Context,
    private val device: BluetoothDevice?,
    controllerId: Int,
    listener: Switch2ControllerListener? = null,
    productId: Int = Switch2Constants.PRODUCT_PRO_CONTROLLER_2,
) : Switch2Controller(
    controllerId = controllerId,
    vendorId = Switch2Constants.NINTENDO_VENDOR_ID,
    productId = productId,
    name = Switch2Constants.controllerNameForProduct(productId),
    listener = listener
) {
    val address: String = device?.address ?: ""
    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var vibrationCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var responseCharacteristic: BluetoothGattCharacteristic? = null
    private var stopped = false
    private val commandQueue: Queue<ByteArray> = LinkedList()
    private var awaitingResponse = false
    private var descriptorsSetup = 0
    private var commandSequence = 0
    private var vibrationPacketId = 0
    private var inputReportLogCount = 0
    private var decodedInputLogCount = 0
    private var lastDecodedButtons = 0
    private var lastReportedButtonFlags = 0
    private var lastRawButtons = 0
    private var lastInputReport: ByteArray? = null
    private var rawReportDeltaLogCount = 0
    private var lastLoggedLeftTrigger = -1f
    private var lastLoggedRightTrigger = -1f
    private var lastLoggedLeftStickX = Float.NaN
    private var lastLoggedLeftStickY = Float.NaN
    private var lastLoggedRightStickX = Float.NaN
    private var lastLoggedRightStickY = Float.NaN

    private var leftCenter = intArrayOf(2048, 2048)
    private var leftMax = intArrayOf(1500, 1500)
    private var leftMin = intArrayOf(1500, 1500)
    private var rightCenter = intArrayOf(2048, 2048)
    private var rightMax = intArrayOf(1500, 1500)
    private var rightMin = intArrayOf(1500, 1500)
    private var sensitivityMultiplier = 1.30f

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        sensitivityMultiplier = Switch2ControllerMappings.stickSensitivity(context, address)
        type = Switch2ControllerType.NINTENDO
        capabilities = (
            Switch2Capabilities.GYROSCOPE.toInt() or
                Switch2Capabilities.ACCELEROMETER.toInt() or
                Switch2Capabilities.RUMBLE.toInt()
        ).toShort()
        supportedButtonFlags = Switch2ControllerMappings.supportedButtonFlags()
    }

    @SuppressLint("MissingPermission")
    override fun start(): Boolean {
        val targetDevice = device ?: return false
        Switch2Log.i("${driverName()}: Connecting to GATT server...")
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            targetDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            targetDevice.connectGatt(context, false, gattCallback)
        }
        return gatt != null
    }

    @SuppressLint("MissingPermission")
    override fun stop() {
        if (stopped) return
        stopped = true
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        notifyDeviceRemoved()
    }

    @SuppressLint("MissingPermission")
    override fun rumble(lowFreqMotor: Short, highFreqMotor: Short) {
        val activeGatt = gatt ?: return
        val characteristic = vibrationCharacteristic ?: run {
            Switch2Log.w("${driverName()}: Rumble requested but vibration characteristic is unavailable")
            return
        }

        val low = lowFreqMotor.toInt() and 0xffff
        val high = highFreqMotor.toInt() and 0xffff
        val lowAmplitude = (low ushr 8) * Switch2Constants.MAX_SWITCH_RUMBLE_AMPLITUDE / 0xff
        val highAmplitude = (high ushr 8) * Switch2Constants.MAX_SWITCH_RUMBLE_AMPLITUDE / 0xff

        val payload = if (isJoyCon()) {
            val activeAmplitude = if (isJoyConLeft()) lowAmplitude else highAmplitude
            val frame = buildVibrationFrame(activeAmplitude, activeAmplitude)
            val motorVibrations = ByteArray(1 + frame.size * 3)
            motorVibrations[0] = (0x50 + (vibrationPacketId and 0x0f)).toByte()
            frame.copyInto(motorVibrations, destinationOffset = 1)
            frame.copyInto(motorVibrations, destinationOffset = 1 + frame.size)
            frame.copyInto(motorVibrations, destinationOffset = 1 + frame.size * 2)

            // JoyCons expect 1 byte prefix (0x00) + 16 byte motor vibrations
            ByteArray(1 + motorVibrations.size).also {
                it[0] = 0x00
                motorVibrations.copyInto(it, destinationOffset = 1)
            }
        } else {
            val leftFrame = buildVibrationFrame(lowAmplitude, lowAmplitude)
            val rightFrame = buildVibrationFrame(highAmplitude, highAmplitude)

            val leftVibrations = ByteArray(1 + leftFrame.size * 3)
            leftVibrations[0] = (0x50 + (vibrationPacketId and 0x0f)).toByte()
            leftFrame.copyInto(leftVibrations, destinationOffset = 1)
            leftFrame.copyInto(leftVibrations, destinationOffset = 1 + leftFrame.size)
            leftFrame.copyInto(leftVibrations, destinationOffset = 1 + leftFrame.size * 2)

            val rightVibrations = ByteArray(1 + rightFrame.size * 3)
            rightVibrations[0] = (0x50 + (vibrationPacketId and 0x0f)).toByte()
            rightFrame.copyInto(rightVibrations, destinationOffset = 1)
            rightFrame.copyInto(rightVibrations, destinationOffset = 1 + rightFrame.size)
            rightFrame.copyInto(rightVibrations, destinationOffset = 1 + rightFrame.size * 2)

            // Pro Controller expects 1 byte prefix + Left motor (16 bytes) + Right motor (16 bytes)
            ByteArray(1 + leftVibrations.size + rightVibrations.size).also {
                it[0] = 0x00
                leftVibrations.copyInto(it, destinationOffset = 1)
                rightVibrations.copyInto(it, destinationOffset = 1 + leftVibrations.size)
            }
        }

        characteristic.writeType =
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
        characteristic.value = payload
        if (!activeGatt.writeCharacteristic(characteristic)) {
            Switch2Log.w("${driverName()}: Rumble write failed to start")
        }
        vibrationPacketId = (vibrationPacketId + 1) and 0x0f
    }

    fun isJoyCon(): Boolean = Switch2Constants.isJoyCon(productId)
    fun isJoyConLeft(): Boolean = Switch2Constants.isJoyConLeft(productId)
    fun isJoyConRight(): Boolean = Switch2Constants.isJoyConRight(productId)

    private fun buildCommand(commandId: Int, subcommandId: Int, payload: ByteArray): ByteArray {
        return ByteArray(8 + payload.size).also { data ->
            data[0] = commandId.toByte()
            data[1] = 0x91.toByte()
            data[2] = 0x01
            data[3] = subcommandId.toByte()
            data[4] = 0x00
            data[5] = payload.size.toByte()
            data[6] = 0x00
            data[7] = 0x00
            payload.copyInto(data, destinationOffset = 8)
        }
    }

    @Synchronized
    private fun enqueueCommand(commandId: Int, subcommandId: Int, payload: ByteArray) {
        commandQueue.add(buildCommand(commandId, subcommandId, payload))
        if (!awaitingResponse) {
            sendNextCommand()
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun sendNextCommand() {
        val characteristic = writeCharacteristic
        val activeGatt = gatt
        if (commandQueue.isEmpty() || characteristic == null || activeGatt == null) {
            awaitingResponse = false
            return
        }

        val next = commandQueue.poll() ?: return
        awaitingResponse = true
        val sequence = ++commandSequence
        characteristic.writeType =
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
        characteristic.value = next
        activeGatt.writeCharacteristic(characteristic)
        mainHandler.postDelayed({ onCommandTimeout(sequence) }, 1000L)
    }

    @Synchronized
    private fun onCommandResponse(response: ByteArray) {
        Switch2Log.i("${driverName()}: Command response received (${response.size} bytes)")

        if (response.size >= 25 &&
            response[0] == Switch2Constants.COMMAND_MEMORY.toByte() &&
            response[8] == 0x0b.toByte()
        ) {
            val buf = ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN)
            val calAddress = buf.getInt(12)

            val c = intArrayOf(readStick24(buf, 16) and 0xfff, readStick24(buf, 16) ushr 12)
            val max = intArrayOf(readStick24(buf, 19) and 0xfff, readStick24(buf, 19) ushr 12)
            val min = intArrayOf(readStick24(buf, 22) and 0xfff, readStick24(buf, 22) ushr 12)

            if (c[0] != 0 && c[0] != 0xfff && max[0] != 0 && max[0] != 0xfff) {
                if (calAddress == Switch2Constants.CALIBRATION_JOYSTICK_L) {
                    leftCenter = c
                    leftMax = max
                    leftMin = min
                    Switch2Log.i("${driverName()}: Left stick calibration loaded: center=(${c[0]},${c[1]}) max=(${max[0]},${max[1]}) min=(${min[0]},${min[1]})")
                } else if (calAddress == Switch2Constants.CALIBRATION_JOYSTICK_R) {
                    rightCenter = c
                    rightMax = max
                    rightMin = min
                    Switch2Log.i("${driverName()}: Right stick calibration loaded: center=(${c[0]},${c[1]}) max=(${max[0]},${max[1]}) min=(${min[0]},${min[1]})")
                }
            }

            Switch2Log.i("${driverName()}: Memory read 0x${calAddress.toString(16)} (size=${response.size}): ${response.toHexPreview(response.size)}")

            if (calAddress == Switch2Constants.CALIBRATION_COLOR_FACTORY ||
                calAddress == Switch2Constants.CALIBRATION_COLOR_SWITCH2 ||
                calAddress == Switch2Constants.CALIBRATION_COLOR_USER
            ) {
                if (response.size >= 19) {
                    val r = response[16].toInt() and 0xff
                    val g = response[17].toInt() and 0xff
                    val b = response[18].toInt() and 0xff
                    if ((r != 0 || g != 0 || b != 0) && (r != 0xff || g != 0xff || b != 0xff)) {
                        val rgbHex = String.format("#%02X%02X%02X", r, g, b)
                        val colorName = Switch2ControllerMappings.colorNameFromRgb(r, g, b, isJoyConLeft())
                        Switch2Log.i("${driverName()}: Detected controller body color: $colorName ($rgbHex, R=$r, G=$g, B=$b)")
                        if (!Switch2ControllerMappings.isUserColorOverride(context, address)) {
                            Switch2ControllerMappings.setControllerColor(context, address, colorName, rgbHex)
                        }
                    }
                }
            }
        }

        awaitingResponse = false
        if (commandQueue.isNotEmpty()) {
            mainHandler.postDelayed({ sendNextCommand() }, 50L)
        }
    }

    @Synchronized
    private fun onCommandTimeout(sequence: Int) {
        if (awaitingResponse && sequence == commandSequence) {
            Switch2Log.w("${driverName()}: No command response received, advancing queue...")
            awaitingResponse = false
            sendNextCommand()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Switch2Log.i("${driverName()}: onConnectionStateChange status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Switch2Log.i("${driverName()}: Connected. Requesting MTU 512...")
                    if (!gatt.requestMtu(512)) {
                        Switch2Log.w("${driverName()}: requestMtu failed, discovering services anyway...")
                        gatt.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Switch2Log.i("${driverName()}: Disconnected.")
                    stop()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Switch2Log.i("${driverName()}: MTU changed to $mtu (status $status). Discovering services...")
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            Switch2Log.i("${driverName()}: Services discovered.")
            for (service in gatt.services) {
                for (characteristic in service.characteristics) {
                    when (characteristic.uuid) {
                        Switch2Constants.COMMAND_WRITE_UUID -> writeCharacteristic = characteristic
                        Switch2Constants.INPUT_REPORT_UUID -> notifyCharacteristic = characteristic
                        Switch2Constants.COMMAND_RESPONSE_UUID -> responseCharacteristic = characteristic
                        Switch2Constants.VIBRATION_WRITE_PRO_CONTROLLER_UUID,
                        Switch2Constants.VIBRATION_WRITE_JOYCON_L_UUID,
                        Switch2Constants.VIBRATION_WRITE_JOYCON_R_UUID -> vibrationCharacteristic = characteristic
                    }
                }
            }

            if (writeCharacteristic == null || notifyCharacteristic == null || responseCharacteristic == null) {
                Switch2Log.w("${driverName()}: Missing required characteristics!")
                return
            }
            if (vibrationCharacteristic == null) {
                Switch2Log.w("${driverName()}: Missing vibration characteristic; rumble will be unavailable")
            }

            mainHandler.postDelayed({ startNotificationSetup(gatt) }, 2000L)
        }

        @SuppressLint("MissingPermission")
        private fun startNotificationSetup(gatt: BluetoothGatt) {
            descriptorsSetup = 0

            val response = responseCharacteristic ?: return
            gatt.setCharacteristicNotification(response, true)
            val descriptor = response.getDescriptor(Switch2Constants.CCCD_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
                Switch2Log.i("${driverName()}: Enabling COMMAND_RESPONSE notifications...")
            } else {
                enableInputReportNotification(gatt)
            }
        }

        @SuppressLint("MissingPermission")
        private fun enableInputReportNotification(gatt: BluetoothGatt) {
            val input = notifyCharacteristic ?: return
            gatt.setCharacteristicNotification(input, true)
            val descriptor = input.getDescriptor(Switch2Constants.CCCD_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
                Switch2Log.i("${driverName()}: Enabling INPUT_REPORT notifications...")
            } else {
                onAllNotificationsEnabled()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Switch2Log.i("${driverName()}: Descriptor written for ${descriptor.characteristic.uuid} status=$status")
            descriptorsSetup++
            if (descriptorsSetup == 1) {
                enableInputReportNotification(gatt)
            } else if (descriptorsSetup >= 2) {
                onAllNotificationsEnabled()
            }
        }

        private fun onAllNotificationsEnabled() {
            Switch2Log.i("${driverName()}: All notifications enabled. Sending init sequence...")
            notifyDeviceAdded()
            sendInitSequence()
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Switch2Log.i("${driverName()}: Write complete for ${characteristic.uuid} status=$status")
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleCharacteristicChanged(characteristic.uuid, characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicChanged(characteristic.uuid, value)
        }

        private fun handleCharacteristicChanged(uuid: UUID, data: ByteArray) {
            when (uuid) {
                Switch2Constants.COMMAND_RESPONSE_UUID -> onCommandResponse(data)
                Switch2Constants.INPUT_REPORT_UUID -> {
                    if (inputReportLogCount < 5) {
                        Switch2Log.i("${driverName()}: Input report received (${data.size} bytes): ${data.toHexPreview()}")
                        inputReportLogCount++
                    }
                    logRawReportDelta(data)
                    handleRead(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN))
                }
            }
        }
    }

    private fun sendInitSequence() {
        enqueueCommand(
            Switch2Constants.COMMAND_MEMORY,
            Switch2Constants.SUBCOMMAND_MEMORY_READ,
            byteArrayOf(
                0x0b, 0x7e, 0x00, 0x00,
                (Switch2Constants.CALIBRATION_JOYSTICK_L and 0xff).toByte(),
                ((Switch2Constants.CALIBRATION_JOYSTICK_L shr 8) and 0xff).toByte(),
                ((Switch2Constants.CALIBRATION_JOYSTICK_L shr 16) and 0xff).toByte(),
                ((Switch2Constants.CALIBRATION_JOYSTICK_L shr 24) and 0xff).toByte(),
            )
        )
        enqueueCommand(
            Switch2Constants.COMMAND_MEMORY,
            Switch2Constants.SUBCOMMAND_MEMORY_READ,
            byteArrayOf(
                0x0b, 0x7e, 0x00, 0x00,
                (Switch2Constants.CALIBRATION_JOYSTICK_R and 0xff).toByte(),
                ((Switch2Constants.CALIBRATION_JOYSTICK_R shr 8) and 0xff).toByte(),
                ((Switch2Constants.CALIBRATION_JOYSTICK_R shr 16) and 0xff).toByte(),
                ((Switch2Constants.CALIBRATION_JOYSTICK_R shr 24) and 0xff).toByte(),
            )
        )
        enqueueCommand(
            Switch2Constants.COMMAND_MEMORY,
            Switch2Constants.SUBCOMMAND_MEMORY_READ,
            byteArrayOf(
                0x0b, 0x7e, 0x00, 0x00,
                (Switch2Constants.CALIBRATION_COLOR_FACTORY and 0xff).toByte(),
                ((Switch2Constants.CALIBRATION_COLOR_FACTORY shr 8) and 0xff).toByte(),
                ((Switch2Constants.CALIBRATION_COLOR_FACTORY shr 16) and 0xff).toByte(),
                ((Switch2Constants.CALIBRATION_COLOR_FACTORY shr 24) and 0xff).toByte(),
            )
        )
        enqueueCommand(
            Switch2Constants.COMMAND_MEMORY,
            Switch2Constants.SUBCOMMAND_MEMORY_READ,
            byteArrayOf(
                0x0b, 0x7e, 0x00, 0x00,
                (Switch2Constants.CALIBRATION_COLOR_SWITCH2 and 0xff).toByte(),
                ((Switch2Constants.CALIBRATION_COLOR_SWITCH2 shr 8) and 0xff).toByte(),
                ((Switch2Constants.CALIBRATION_COLOR_SWITCH2 shr 16) and 0xff).toByte(),
                ((Switch2Constants.CALIBRATION_COLOR_SWITCH2 shr 24) and 0xff).toByte(),
            )
        )

        enqueueCommand(
            Switch2Constants.COMMAND_MEMORY,
            Switch2Constants.SUBCOMMAND_MEMORY_READ,
            byteArrayOf(
                0x0b, 0x7e, 0x00, 0x00,
                (Switch2Constants.CALIBRATION_COLOR_USER and 0xff).toByte(),
                ((Switch2Constants.CALIBRATION_COLOR_USER shr 8) and 0xff).toByte(),
                ((Switch2Constants.CALIBRATION_COLOR_USER shr 16) and 0xff).toByte(),
                ((Switch2Constants.CALIBRATION_COLOR_USER shr 24) and 0xff).toByte(),
            )
        )

        val setMacPayload = byteArrayOf(
            0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        enqueueCommand(Switch2Constants.COMMAND_PAIR, Switch2Constants.SUBCOMMAND_PAIR_SET_MAC, setMacPayload)

        val ltk1 = byteArrayOf(
            0x00, 0xea.toByte(), 0xbd.toByte(), 0x47, 0x13, 0x89.toByte(), 0x35, 0x42,
            0xc6.toByte(), 0x79, 0xee.toByte(), 0x07, 0xf2.toByte(), 0x53, 0x2c, 0x6c, 0x31,
        )
        enqueueCommand(Switch2Constants.COMMAND_PAIR, Switch2Constants.SUBCOMMAND_PAIR_LTK1, ltk1)

        val ltk2 = byteArrayOf(
            0x00, 0x40, 0xb0.toByte(), 0x8a.toByte(), 0x5f, 0xcd.toByte(), 0x1f, 0x9b.toByte(),
            0x41, 0x12, 0x5c, 0xac.toByte(), 0xc6.toByte(), 0x3f, 0x38, 0xa0.toByte(), 0x73,
        )
        enqueueCommand(Switch2Constants.COMMAND_PAIR, Switch2Constants.SUBCOMMAND_PAIR_LTK2, ltk2)

        enqueueCommand(Switch2Constants.COMMAND_PAIR, Switch2Constants.SUBCOMMAND_PAIR_FINISH, byteArrayOf(0x00))

        val featureFlags = byteArrayOf((Switch2Constants.FEATURE_FLAGS and 0xff).toByte(), 0x00, 0x00, 0x00)
        enqueueCommand(Switch2Constants.COMMAND_FEATURE, Switch2Constants.SUBCOMMAND_FEATURE_INIT, featureFlags)
        enqueueCommand(Switch2Constants.COMMAND_FEATURE, Switch2Constants.SUBCOMMAND_FEATURE_ENABLE, featureFlags)

        enqueueCommand(
            Switch2Constants.COMMAND_LEDS,
            Switch2Constants.SUBCOMMAND_LEDS_SET_PLAYER,
            byteArrayOf(Switch2Constants.LED_PLAYER_1.toByte(), 0x00, 0x00, 0x00)
        )
    }

    private fun handleRead(buf: ByteBuffer): Boolean {
        if (buf.limit() < 16) return false

        val buttons = buf.getInt(4)
        val buttonFlags = Switch2ControllerMappings.mapButtons(context, address, buttons)

        val leftTrigger = if ((buttons and 0x00800000) != 0) 1f else 0f
        val rightTrigger = if ((buttons and 0x00000080) != 0) 1f else 0f

        val lsRaw = readStick24(buf, 10)
        val rsRaw = readStick24(buf, 13)

        val leftStickX: Float
        val leftStickY: Float
        val rightStickX: Float
        val rightStickY: Float

        if (Switch2Constants.isJoyConRight(productId)) {
            leftStickX = 0f
            leftStickY = 0f
            rightStickX = stickAxis(rsRaw and 0xfff, rightCenter[0], rightMax[0], rightMin[0], false)
            rightStickY = stickAxis((rsRaw ushr 12) and 0xfff, rightCenter[1], rightMax[1], rightMin[1], true)
        } else if (Switch2Constants.isJoyConLeft(productId)) {
            leftStickX = stickAxis(lsRaw and 0xfff, leftCenter[0], leftMax[0], leftMin[0], false)
            leftStickY = stickAxis((lsRaw ushr 12) and 0xfff, leftCenter[1], leftMax[1], leftMin[1], true)
            rightStickX = 0f
            rightStickY = 0f
        } else {
            leftStickX = stickAxis(lsRaw and 0xfff, leftCenter[0], leftMax[0], leftMin[0], false)
            leftStickY = stickAxis((lsRaw ushr 12) and 0xfff, leftCenter[1], leftMax[1], leftMin[1], true)
            rightStickX = stickAxis(rsRaw and 0xfff, rightCenter[0], rightMax[0], rightMin[0], false)
            rightStickY = stickAxis((rsRaw ushr 12) and 0xfff, rightCenter[1], rightMax[1], rightMin[1], true)
        }

        logRawButtonChanges(buttons)
        logButtonChanges(buttons, buttonFlags)
        logAnalogChanges(leftTrigger, rightTrigger, leftStickX, leftStickY, rightStickX, rightStickY)

        val state = Switch2ControllerState(
            controllerId = controllerId,
            buttonFlags = buttonFlags,
            leftStickX = leftStickX,
            leftStickY = leftStickY,
            rightStickX = rightStickX,
            rightStickY = rightStickY,
            leftTrigger = leftTrigger,
            rightTrigger = rightTrigger
        )
        reportState(state)

        if (buf.limit() >= 60) {
            val accelX = buf.getShort(48) / 4096.0f
            val accelY = buf.getShort(50) / 4096.0f
            val accelZ = buf.getShort(52) / 4096.0f
            val gyroX = buf.getShort(54) / 16.0f
            val gyroZ = buf.getShort(56) / 16.0f
            val gyroY = -buf.getShort(58) / 16.0f

            reportMotion(Switch2MotionState(controllerId, Switch2MotionType.ACCELEROMETER, accelX, accelY, accelZ))
            reportMotion(Switch2MotionState(controllerId, Switch2MotionType.GYROSCOPE, gyroX, gyroY, gyroZ))
        }

        return true
    }

    private fun logRawReportDelta(data: ByteArray) {
        val previous = lastInputReport
        lastInputReport = data.copyOf()

        if (previous == null || rawReportDeltaLogCount >= 160) return

        val maxSize = maxOf(previous.size, data.size)
        val deltas = ArrayList<String>()
        for (i in 0 until maxSize) {
            val oldValue = previous.getOrNull(i)?.toInt()?.and(0xff)
            val newValue = data.getOrNull(i)?.toInt()?.and(0xff)
            if (oldValue != newValue) {
                deltas.add("${i}:${oldValue?.toString(16)?.padStart(2, '0') ?: "--"}>${newValue?.toString(16)?.padStart(2, '0') ?: "--"}")
            }
        }

        if (deltas.isEmpty()) return

        val message = "report-delta size=${data.size} changed=${deltas.take(20).joinToString(",")}" +
            if (deltas.size > 20) ",..." else ""
        Switch2Log.i("${driverName()}: $message")
        rawReportDeltaLogCount++
    }

    private fun logRawButtonChanges(rawButtons: Int) {
        val changed = rawButtons xor lastRawButtons
        if (changed == 0) return

        Switch2Log.i("${driverName()}: raw-buttons changed=0x${changed.toUInt().toString(16)} raw=0x${rawButtons.toUInt().toString(16)}")
        lastRawButtons = rawButtons
    }

    private fun logButtonChanges(rawButtons: Int, mappedButtons: Int) {
        val changed = mappedButtons xor lastReportedButtonFlags
        if (changed == 0) return

        Switch2Log.i("${driverName()}: mapped button change raw=0x${rawButtons.toUInt().toString(16)} mapped=0x${mappedButtons.toUInt().toString(16)}")
        lastReportedButtonFlags = mappedButtons
    }

    private fun logAnalogChanges(
        lt: Float,
        rt: Float,
        lsX: Float,
        lsY: Float,
        rsX: Float,
        rsY: Float
    ) {
        val triggerChanged = lastLoggedLeftTrigger < 0f ||
            kotlin.math.abs(lt - lastLoggedLeftTrigger) >= 0.05f ||
            kotlin.math.abs(rt - lastLoggedRightTrigger) >= 0.05f
        if (triggerChanged) {
            Switch2Log.i("${driverName()}: triggers ZL=$lt ZR=$rt")
            lastLoggedLeftTrigger = lt
            lastLoggedRightTrigger = rt
        }

        val stickChanged = lastLoggedLeftStickX.isNaN() ||
            kotlin.math.abs(lsX - lastLoggedLeftStickX) >= 0.08f ||
            kotlin.math.abs(lsY - lastLoggedLeftStickY) >= 0.08f ||
            kotlin.math.abs(rsX - lastLoggedRightStickX) >= 0.08f ||
            kotlin.math.abs(rsY - lastLoggedRightStickY) >= 0.08f
        if (stickChanged) {
            Switch2Log.i("${driverName()}: sticks LS=($lsX,$lsY) RS=($rsX,$rsY)")
            lastLoggedLeftStickX = lsX
            lastLoggedLeftStickY = lsY
            lastLoggedRightStickX = rsX
            lastLoggedRightStickY = rsY
        }
    }

    private fun readStick24(buf: ByteBuffer, offset: Int): Int {
        return (buf.get(offset).toInt() and 0xff) or
            ((buf.get(offset + 1).toInt() and 0xff) shl 8) or
            ((buf.get(offset + 2).toInt() and 0xff) shl 16)
    }

    private fun stickAxis(raw: Int, center: Int, maxAbs: Int, minAbs: Int, invert: Boolean): Float {
        val signedValue = raw - center
        var value = if (signedValue > 0) {
            (signedValue.toFloat() / maxAbs).coerceAtMost(1.0f)
        } else if (signedValue < 0) {
            (signedValue.toFloat() / minAbs).coerceAtLeast(-1.0f)
        } else {
            0.0f
        }

        value = (value * sensitivityMultiplier).coerceIn(-1.0f, 1.0f)
        return if (invert) -value else value
    }

    private fun driverName(): String {
        return "Switch2ProConBleDriver(${Switch2Constants.controllerNameForProduct(productId)})"
    }

    private fun buildVibrationFrame(lowAmplitude: Int, highAmplitude: Int): ByteArray {
        var value = 0L
        value = value or (Switch2Constants.DEFAULT_LOW_FREQUENCY.toLong() and 0x1ff)
        value = value or ((lowAmplitude.coerceIn(0, 0x3ff).toLong() and 0x3ff) shl 10)
        value = value or ((Switch2Constants.DEFAULT_HIGH_FREQUENCY.toLong() and 0x1ff) shl 20)
        value = value or ((highAmplitude.coerceIn(0, 0x3ff).toLong() and 0x3ff) shl 30)

        return ByteArray(5) { index ->
            ((value shr (index * 8)) and 0xff).toByte()
        }
    }

    private fun ByteArray.toHexPreview(maxBytes: Int = 32): String {
        return take(maxBytes).joinToString(separator = " ") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }
}
