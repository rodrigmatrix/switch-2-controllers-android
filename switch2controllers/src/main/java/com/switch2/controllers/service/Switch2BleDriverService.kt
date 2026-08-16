package com.switch2.controllers.service

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.switch2.controllers.core.Switch2Constants
import com.switch2.controllers.core.Switch2Controller
import com.switch2.controllers.core.Switch2ControllerListener
import com.switch2.controllers.core.Switch2ControllerState
import com.switch2.controllers.core.Switch2Log
import com.switch2.controllers.core.Switch2MotionState
import com.switch2.controllers.core.Switch2ProConBleDriver
import com.switch2.controllers.core.Switch2VirtualJoyConPair
import com.switch2.controllers.mappings.Switch2ControllerMappings
import java.util.concurrent.CopyOnWriteArrayList

class Switch2BleDriverService : Service(), Switch2ControllerListener {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var started = false
    private val binder = BleDriverBinder()
    private val controllers = ArrayList<Switch2Controller>()
    private val physicalBleControllersById = HashMap<Int, Switch2ProConBleDriver>()
    private val latestStatesById = HashMap<Int, Switch2ControllerState>()
    private val listeners = CopyOnWriteArrayList<Switch2ControllerListener>()
    private var nextDeviceId = 100 // Start at 100 to avoid conflict with standard USB devices
    private val connectedAddresses = HashSet<String>()
    private var combineJoyCons = true
    private var virtualJoyConPair: Switch2VirtualJoyConPair? = null

    override fun onControllerStateReported(state: Switch2ControllerState) {
        val physical = physicalBleControllersById[state.controllerId]
        if (combineJoyCons && physical != null && physical.isJoyCon()) {
            latestStatesById[state.controllerId] = state
            reportCombinedJoyConState()
            return
        }

        listeners.forEach { it.onControllerStateReported(state) }
    }

    override fun onControllerMotionReported(motion: Switch2MotionState) {
        val physical = physicalBleControllersById[motion.controllerId]
        val pair = virtualJoyConPair
        if (combineJoyCons && physical != null && physical.isJoyCon() && pair != null) {
            val shouldUseMotion = physical.isJoyConRight() || !hasConnectedJoyConRight()
            if (shouldUseMotion) {
                val pairMotion = motion.copy(controllerId = pair.controllerId)
                listeners.forEach { it.onControllerMotionReported(pairMotion) }
            }
            return
        }

        listeners.forEach { it.onControllerMotionReported(motion) }
    }

    override fun onControllerRemoved(controller: Switch2Controller) {
        controllers.remove(controller)
        if (controller is Switch2ProConBleDriver) {
            connectedAddresses.remove(controller.address)
            physicalBleControllersById.remove(controller.controllerId)
            latestStatesById.remove(controller.controllerId)
            if (combineJoyCons && controller.isJoyCon()) {
                removeVirtualJoyConPair()
                if (physicalBleControllersById.values.any { it.isJoyCon() }) {
                    ensureVirtualJoyConPair()
                }
                if (controllers.isEmpty()) {
                    started = false
                }
                return
            }
        }
        if (controllers.isEmpty()) {
            started = false
        }
        listeners.forEach { it.onControllerRemoved(controller) }
    }

    override fun onControllerAdded(controller: Switch2Controller) {
        if (controller is Switch2ProConBleDriver) {
            physicalBleControllersById[controller.controllerId] = controller
            if (combineJoyCons && controller.isJoyCon()) {
                ensureVirtualJoyConPair()
                return
            }
        }
        listeners.forEach { it.onControllerAdded(controller) }
    }

    inner class BleDriverBinder : Binder() {
        fun getService(): Switch2BleDriverService = this@Switch2BleDriverService

        fun addListener(listener: Switch2ControllerListener) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
            // Notify existing controllers
            if (combineJoyCons && virtualJoyConPair != null) {
                listener.onControllerAdded(virtualJoyConPair!!)
                controllers
                    .filterNot { it is Switch2ProConBleDriver && it.isJoyCon() }
                    .forEach(listener::onControllerAdded)
            } else {
                controllers.forEach(listener::onControllerAdded)
            }
        }

        fun removeListener(listener: Switch2ControllerListener) {
            listeners.remove(listener)
        }

        fun start() {
            this@Switch2BleDriverService.start()
        }

        fun stop() {
            this@Switch2BleDriverService.stop()
        }

        fun rumble(controllerId: Int, lowFreq: Short, highFreq: Short) {
            val pair = virtualJoyConPair
            if (pair != null && pair.controllerId == controllerId) {
                rumbleConnectedJoyCons(lowFreq, highFreq)
            } else {
                physicalBleControllersById[controllerId]?.rumble(lowFreq, highFreq)
            }
        }

        fun getConnectedControllers(): List<Switch2Controller> {
            return if (combineJoyCons && virtualJoyConPair != null) {
                val list = mutableListOf<Switch2Controller>()
                list.add(virtualJoyConPair!!)
                list.addAll(controllers.filterNot { it is Switch2ProConBleDriver && it.isJoyCon() })
                list
            } else {
                controllers.toList()
            }
        }

        fun getConnectedAddresses(): Set<String> = this@Switch2BleDriverService.getConnectedAddresses()
        fun isJoyConPairActive(): Boolean = this@Switch2BleDriverService.isJoyConPairActive()
        fun isJoyConLeftConnected(): Boolean = this@Switch2BleDriverService.isJoyConLeftConnected()
        fun isJoyConRightConnected(): Boolean = this@Switch2BleDriverService.isJoyConRightConnected()
    }

    fun getConnectedAddresses(): Set<String> {
        return physicalBleControllersById.values.map { it.address }.toSet()
    }

    fun isJoyConPairActive(): Boolean {
        return combineJoyCons && virtualJoyConPair != null &&
            physicalBleControllersById.values.any { it.isJoyConLeft() } &&
            physicalBleControllersById.values.any { it.isJoyConRight() }
    }

    fun isJoyConLeftConnected(): Boolean {
        return physicalBleControllersById.values.any { it.isJoyConLeft() }
    }

    fun isJoyConRightConnected(): Boolean {
        return physicalBleControllersById.values.any { it.isJoyConRight() }
    }

    fun start() {
        if (started && controllers.isEmpty()) {
            Switch2Log.i("Switch2BleDriverService: recovering from stale started state.")
            started = false
        }

        val pairedControllers = Switch2ControllerMappings.getPairedControllers(this)
        val adapter = bluetoothAdapter
        combineJoyCons = Switch2ControllerMappings.combineJoyCons(this)

        Switch2Log.i(
            "Switch2BleDriverService: start() - controllers=$pairedControllers " +
                "combineJoyCons=$combineJoyCons btAdapter=${if (adapter != null) "ok" else "null"}",
        )

        if (pairedControllers.isNotEmpty() && adapter != null && adapter.isEnabled) {
            for (mac in pairedControllers) {
                if (connectedAddresses.contains(mac)) {
                    continue
                }
                val device = try {
                    adapter.getRemoteDevice(mac)
                } catch (e: IllegalArgumentException) {
                    Switch2Log.e("Invalid Bluetooth address: $mac", e)
                    continue
                }

                val productId = Switch2ControllerMappings.controllerProductId(this, mac)
                val controllerName = Switch2Constants.controllerNameForProduct(productId)

                if (productId == Switch2Constants.PRODUCT_JOYCON_L || productId == Switch2Constants.PRODUCT_JOYCON_R) {
                    Switch2Log.i("Switch2BleDriverService skipping $controllerName $mac (handled natively by Android)")
                    continue
                }

                Switch2Log.i(
                    "Switch2BleDriverService attempting to connect to $controllerName $mac " +
                        "product=0x${productId.toString(16)}",
                )
                val controller = Switch2ProConBleDriver(this, device, nextDeviceId++, this, productId)
                if (controller.start()) {
                    controllers.add(controller)
                    connectedAddresses.add(mac)
                    started = true
                } else {
                    Switch2Log.w("Switch2BleDriverService: Switch2 BLE driver start() returned false for $mac")
                }
            }
        } else {
            Switch2Log.w("Switch2BleDriverService: cannot connect - controllers=$pairedControllers btEnabled=${adapter?.isEnabled == true}")
        }
    }

    fun stop() {
        if (!started) return
        started = false

        removeVirtualJoyConPair()
        while (controllers.isNotEmpty()) {
            controllers.removeAt(0).stop()
        }
        connectedAddresses.clear()
        physicalBleControllersById.clear()
        latestStatesById.clear()
    }

    private fun ensureVirtualJoyConPair() {
        if (virtualJoyConPair != null) return
        val pair = Switch2VirtualJoyConPair(nextDeviceId++, this) { lowFreq, highFreq ->
            rumbleConnectedJoyCons(lowFreq, highFreq)
        }
        virtualJoyConPair = pair
        Switch2Log.i("Switch2BleDriverService: exposing paired Joy-Con 2 virtual controller id=${pair.controllerId}")
        listeners.forEach { it.onControllerAdded(pair) }
    }

    private fun removeVirtualJoyConPair() {
        val pair = virtualJoyConPair ?: return
        Switch2Log.i("Switch2BleDriverService: removing paired Joy-Con 2 virtual controller id=${pair.controllerId}")
        listeners.forEach { it.onControllerRemoved(pair) }
        virtualJoyConPair = null
    }

    private fun reportCombinedJoyConState() {
        val pair = virtualJoyConPair ?: return
        var buttonFlags = 0
        var leftStickX = 0f
        var leftStickY = 0f
        var rightStickX = 0f
        var rightStickY = 0f
        var leftTrigger = 0f
        var rightTrigger = 0f

        for ((controllerId, state) in latestStatesById) {
            val controller = physicalBleControllersById[controllerId] ?: continue
            if (!controller.isJoyCon()) continue

            buttonFlags = buttonFlags or state.buttonFlags
            leftStickX = maxByMagnitude(leftStickX, state.leftStickX)
            leftStickY = maxByMagnitude(leftStickY, state.leftStickY)
            rightStickX = maxByMagnitude(rightStickX, state.rightStickX)
            rightStickY = maxByMagnitude(rightStickY, state.rightStickY)
            leftTrigger = maxOf(leftTrigger, state.leftTrigger)
            rightTrigger = maxOf(rightTrigger, state.rightTrigger)
        }

        val combinedState = Switch2ControllerState(
            controllerId = pair.controllerId,
            buttonFlags = buttonFlags,
            leftStickX = leftStickX,
            leftStickY = leftStickY,
            rightStickX = rightStickX,
            rightStickY = rightStickY,
            leftTrigger = leftTrigger,
            rightTrigger = rightTrigger
        )
        listeners.forEach { it.onControllerStateReported(combinedState) }
    }

    private fun maxByMagnitude(a: Float, b: Float): Float {
        return if (kotlin.math.abs(b) > kotlin.math.abs(a)) b else a
    }

    private fun hasConnectedJoyConRight(): Boolean {
        return physicalBleControllersById.values.any { it.isJoyConRight() }
    }

    private fun rumbleConnectedJoyCons(lowFreqMotor: Short, highFreqMotor: Short) {
        physicalBleControllersById.values
            .filter { it.isJoyCon() }
            .forEach { it.rumble(lowFreqMotor, highFreqMotor) }
    }

    override fun onCreate() {
        super.onCreate()
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Switch2Log.i("Switch2BleDriverService: onStartCommand executed")
        start()
        return START_STICKY
    }

    override fun onDestroy() {
        stop()
        listeners.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
