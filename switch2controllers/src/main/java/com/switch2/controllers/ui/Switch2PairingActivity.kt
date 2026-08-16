package com.switch2.controllers.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.switch2.controllers.core.Switch2Constants
import com.switch2.controllers.core.Switch2Log
import com.switch2.controllers.mappings.Switch2ControllerMappings
import com.switch2.controllers.service.Switch2BleDriverService

class Switch2PairingActivity : ComponentActivity() {
    private var uiMessage by mutableStateOf("Initializing scanner...")
    private var scanning by mutableStateOf(false)
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private lateinit var handler: Handler

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            startScan()
        } else {
            Toast.makeText(this, "Bluetooth permissions are required to scan for controllers", Toast.LENGTH_LONG).show()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_FOUND == intent.action) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                } ?: return

                val deviceName = device.name
                Switch2Log.i("Classic Bluetooth Scanner found device: ${device.address} Name: $deviceName")

                val productId = productIdFromDeviceName(deviceName)
                if (productId != null) {
                    val controllerName = Switch2Constants.controllerNameForProduct(productId)
                    Switch2Log.i("Found Classic BT Controller: ${device.address} product=0x${productId.toString(16)}")
                    stopScan()

                    try {
                        if (device.bondState == BluetoothDevice.BOND_NONE) {
                            device.createBond()
                            Toast.makeText(this@Switch2PairingActivity, "Pairing with $controllerName...", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: SecurityException) {
                        Switch2Log.w("SecurityException creating bond: ${e.message}")
                    }

                    Switch2ControllerMappings.addPairedController(
                        this@Switch2PairingActivity,
                        device.address,
                        deviceName ?: controllerName,
                        productId
                    )
                    startService(Intent(this@Switch2PairingActivity, Switch2BleDriverService::class.java))
                    Toast.makeText(this@Switch2PairingActivity, "Paired successfully with $controllerName!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handler = Handler(Looper.getMainLooper())

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)

        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter?.isEnabled != true) {
            Toast.makeText(this, "Bluetooth must be enabled to pair controllers", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            com.switch2.controllers.ui.theme.Switch2Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Pair Switch 2 Controller", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Hold the SYNC button on your controller until the LEDs start cycling.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(32.dp))

                        if (scanning) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(uiMessage, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = { stopScan() }) {
                                Text("Stop Scanning")
                            }
                        } else {
                            Button(onClick = {
                                if (checkPermissions()) {
                                    startScan()
                                } else {
                                    requestPermissions()
                                }
                            }) {
                                Text("Start Scanning")
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(onClick = { finish() }) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }

        if (checkPermissions()) {
            startScan()
        } else {
            requestPermissions()
        }
    }

    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        permissionLauncher.launch(permissions)
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        val adapter = bluetoothAdapter ?: return
        val scanner = adapter.bluetoothLeScanner
        bluetoothLeScanner = scanner

        if (scanner == null) {
            Toast.makeText(this, "BLE scanning is not supported on this device", Toast.LENGTH_LONG).show()
            return
        }

        uiMessage = "Scanning for Switch 2 and Joy-Con controllers..."
        scanning = true

        handler.postDelayed({
            if (scanning) {
                stopScan()
                Toast.makeText(this, "Could not find controller. Make sure it is in pairing mode.", Toast.LENGTH_LONG).show()
            }
        }, 30000L)

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, leScanCallback)
            adapter.startDiscovery()
        } catch (e: SecurityException) {
            Switch2Log.e("Permission error starting scan", e)
            scanning = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (scanning) {
            scanning = false
            try {
                bluetoothLeScanner?.stopScan(leScanCallback)
                bluetoothAdapter?.cancelDiscovery()
            } catch (e: SecurityException) {
                Switch2Log.w("SecurityException stopping scan: ${e.message}")
            }
        }
    }

    private val leScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!scanning) return

            var deviceName = result.device.name ?: result.scanRecord?.deviceName
            val productId = supportedControllerProductId(result) ?: productIdFromDeviceName(deviceName)

            if (productId != null) {
                val controllerName = Switch2Constants.controllerNameForProduct(productId)
                Switch2Log.i("Found Nintendo BLE Controller: ${result.device.address} product=0x${productId.toString(16)} name=$deviceName")
                stopScan()

                Switch2ControllerMappings.addPairedController(
                    this@Switch2PairingActivity,
                    result.device.address,
                    deviceName ?: controllerName,
                    productId
                )

                Toast.makeText(this@Switch2PairingActivity, "Paired successfully with $controllerName!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Switch2Log.w("BLE Scan failed with code $errorCode")
            if (scanning) {
                scanning = false
                Toast.makeText(this@Switch2PairingActivity, "Scan failed: $errorCode", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun supportedControllerProductId(result: ScanResult): Int? {
        val manufacturerData = result.scanRecord?.manufacturerSpecificData ?: return null
        val data = manufacturerData[Switch2Constants.NINTENDO_BLUETOOTH_MANUFACTURER_ID] ?: return null
        if (data.size < 7) return null

        val vendorId = (data[3].toInt() and 0xff) or ((data[4].toInt() and 0xff) shl 8)
        val productId = (data[5].toInt() and 0xff) or ((data[6].toInt() and 0xff) shl 8)
        if (vendorId == Switch2Constants.NINTENDO_VENDOR_ID && Switch2Constants.isSupportedProductId(productId)) {
            return productId
        }
        return null
    }

    private fun productIdFromDeviceName(deviceName: String?): Int? {
        val name = deviceName?.lowercase() ?: return null
        return when {
            "joy-con 2" in name && ("left" in name || "(l)" in name) -> Switch2Constants.PRODUCT_JOYCON_2_LEFT
            "joy-con 2" in name && ("right" in name || "(r)" in name) -> Switch2Constants.PRODUCT_JOYCON_2_RIGHT
            "joy-con" in name && ("left" in name || "(l)" in name) -> Switch2Constants.PRODUCT_JOYCON_L
            "joy-con" in name && ("right" in name || "(r)" in name) -> Switch2Constants.PRODUCT_JOYCON_R
            "gamecube" in name -> Switch2Constants.PRODUCT_NSO_GAMECUBE_CONTROLLER
            "pro controller" in name -> Switch2Constants.PRODUCT_PRO_CONTROLLER_2
            else -> null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScan()
        try {
            unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {}
    }
}
