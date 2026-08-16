package com.switch2.controllers.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.switch2.controllers.core.Switch2Controller
import com.switch2.controllers.core.Switch2ControllerListener
import com.switch2.controllers.core.Switch2ControllerState
import com.switch2.controllers.core.Switch2MotionState
import com.switch2.controllers.manager.Switch2Manager
import com.switch2.controllers.mappings.Switch2ControllerMappings
import com.switch2.controllers.service.Switch2BleDriverService

class Switch2ControllersActivity : ComponentActivity() {
    private lateinit var switch2Manager: Switch2Manager
    private var pairedControllers by mutableStateOf<List<String>>(emptyList())
    private var connectedAddresses by mutableStateOf<Set<String>>(emptySet())
    private var combineJoyCons by mutableStateOf(true)
    private var isJoyConPairActive by mutableStateOf(false)

    private val controllerListener = object : Switch2ControllerListener {
        override fun onControllerAdded(controller: Switch2Controller) {
            runOnUiThread { refreshState() }
        }

        override fun onControllerRemoved(controller: Switch2Controller) {
            runOnUiThread { refreshState() }
        }

        override fun onControllerStateReported(state: Switch2ControllerState) = Unit
        override fun onControllerMotionReported(motion: Switch2MotionState) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        switch2Manager = Switch2Manager.getInstance(this)

        setContent {
            com.switch2.controllers.ui.theme.Switch2Theme {
                Switch2ControllersScreen(
                    pairedControllers = pairedControllers,
                    connectedAddresses = connectedAddresses,
                    combineJoyCons = combineJoyCons,
                    isJoyConPairActive = isJoyConPairActive,
                    onCombineJoyConsChanged = { checked ->
                        Switch2ControllerMappings.setCombineJoyCons(this, checked)
                        combineJoyCons = checked
                        restartService()
                    },
                    onPairNewClicked = {
                        startActivity(Intent(this, Switch2PairingActivity::class.java))
                    },
                    onSettingsClicked = { address ->
                        startActivity(
                            Intent(this, Switch2SettingsActivity::class.java)
                                .putExtra(Switch2SettingsActivity.EXTRA_ADDRESS, address)
                        )
                    },
                    onForgetClicked = { address ->
                        Switch2ControllerMappings.removePairedController(this, address)
                        refreshState()
                        restartService()
                    },
                    onCloseClicked = { finish() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        switch2Manager.start()
        switch2Manager.addListener(controllerListener)
    }

    override fun onResume() {
        super.onResume()
        refreshState()
        if (pairedControllers.isNotEmpty()) {
            startService(Intent(this, Switch2BleDriverService::class.java))
        }
    }

    override fun onStop() {
        super.onStop()
        switch2Manager.removeListener(controllerListener)
    }

    private fun refreshState() {
        pairedControllers = Switch2ControllerMappings.getPairedControllers(this)
        combineJoyCons = Switch2ControllerMappings.combineJoyCons(this)
        connectedAddresses = switch2Manager.getConnectedAddresses()
        isJoyConPairActive = switch2Manager.isJoyConPairActive()
    }

    private fun restartService() {
        val intent = Intent(this, Switch2BleDriverService::class.java)
        stopService(intent)
        if (Switch2ControllerMappings.getPairedControllers(this).isNotEmpty()) {
            startService(intent)
        }
        refreshState()
    }
}
