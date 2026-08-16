package com.switch2.controllers.manager

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.switch2.controllers.core.Switch2Controller
import com.switch2.controllers.core.Switch2ControllerListener
import com.switch2.controllers.core.Switch2ControllerState
import com.switch2.controllers.core.Switch2Log
import com.switch2.controllers.core.Switch2MotionState
import com.switch2.controllers.mappings.Switch2ControllerMappings
import com.switch2.controllers.service.Switch2BleDriverService
import com.switch2.controllers.ui.Switch2ControllersActivity
import com.switch2.controllers.ui.Switch2PairingActivity
import java.util.concurrent.CopyOnWriteArrayList

class Switch2Manager private constructor(private val context: Context) {
    private var binder: Switch2BleDriverService.BleDriverBinder? = null
    private var isBound = false
    private val listeners = CopyOnWriteArrayList<Switch2ControllerListener>()

    private val serviceListener = object : Switch2ControllerListener {
        override fun onControllerAdded(controller: Switch2Controller) {
            listeners.forEach { it.onControllerAdded(controller) }
        }

        override fun onControllerRemoved(controller: Switch2Controller) {
            listeners.forEach { it.onControllerRemoved(controller) }
        }

        override fun onControllerStateReported(state: Switch2ControllerState) {
            listeners.forEach { it.onControllerStateReported(state) }
        }

        override fun onControllerMotionReported(motion: Switch2MotionState) {
            listeners.forEach { it.onControllerMotionReported(motion) }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Switch2Log.i("Switch2Manager: Connected to Switch2BleDriverService")
            val b = service as? Switch2BleDriverService.BleDriverBinder
            binder = b
            b?.addListener(serviceListener)
            b?.start()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Switch2Log.i("Switch2Manager: Disconnected from Switch2BleDriverService")
            binder = null
            isBound = false
        }
    }

    fun start() {
        val intent = Intent(context, Switch2BleDriverService::class.java)
        context.startService(intent)
        if (!isBound) {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun stop() {
        if (isBound) {
            binder?.removeListener(serviceListener)
            context.unbindService(connection)
            isBound = false
        }
        binder = null
        val intent = Intent(context, Switch2BleDriverService::class.java)
        context.stopService(intent)
    }

    fun addListener(listener: Switch2ControllerListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
        binder?.addListener(listener)
    }

    fun removeListener(listener: Switch2ControllerListener) {
        listeners.remove(listener)
        binder?.removeListener(listener)
    }

    fun rumble(controllerId: Int, lowFreq: Short, highFreq: Short) {
        binder?.rumble(controllerId, lowFreq, highFreq)
    }

    fun getConnectedControllers(): List<Switch2Controller> {
        return binder?.getConnectedControllers() ?: emptyList()
    }

    fun getConnectedAddresses(): Set<String> {
        return binder?.getConnectedAddresses() ?: emptySet()
    }

    fun isAddressConnected(address: String): Boolean {
        return getConnectedAddresses().contains(address)
    }

    fun isJoyConPairActive(): Boolean {
        return binder?.isJoyConPairActive() ?: false
    }

    fun isJoyConLeftConnected(): Boolean {
        return binder?.isJoyConLeftConnected() ?: false
    }

    fun isJoyConRightConnected(): Boolean {
        return binder?.isJoyConRightConnected() ?: false
    }

    fun getPairedControllerAddresses(): List<String> {
        return Switch2ControllerMappings.getPairedControllers(context)
    }

    fun openControllersActivity(context: Context) {
        val intent = Intent(context, Switch2ControllersActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openPairingActivity(context: Context) {
        val intent = Intent(context, Switch2PairingActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    companion object {
        @Volatile
        private var instance: Switch2Manager? = null

        fun getInstance(context: Context): Switch2Manager {
            return instance ?: synchronized(this) {
                instance ?: Switch2Manager(context.applicationContext).also { instance = it }
            }
        }
    }
}
