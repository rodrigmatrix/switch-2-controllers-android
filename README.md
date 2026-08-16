# Switch 2 Controllers Android Library

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green.svg)](https://developer.android.com)

A standalone, high-performance Android library that brings full native support for **Nintendo Switch 2 Controllers** (Switch Pro Controller 2, Joy-Con 2 Left & Right, and NSO GameCube controllers) to any Android application or web game streaming client.

---

## ✨ Features

- 🎮 **Full Controller Support**: Pro Controller 2, Joy-Con 2 (Left & Right), Legacy Joy-Con (L/R), and NSO GameCube controller.
- ⚡ **Low-Latency Bluetooth LE (BLE) GATT Driver**: Automatic MTU 512 negotiation, CCCD descriptor configuration, and high-frequency input report processing.
- 🎯 **Stick Calibration**: Reads factory analog stick calibration from controller memory (`0x0130A8` / `0x0130E8`) for precision centering, deadzone elimination, and configurable sensitivity multiplier (50% - 200%).
- 🔄 **Joy-Con Pair Merging**: Seamlessly merge physical Left and Right Joy-Con 2 controllers into a single unified virtual controller (`Switch2VirtualJoyConPair`).
- 🌊 **HD Rumble & Trigger Haptics**: Full HD rumble packet synthesis with custom frequency and amplitude modulation.
- 🧭 **6-Axis Motion Sensing (IMU)**: Real-time Accelerometer and Gyroscope sensor reports.
- 🎨 **Jetpack Compose UI**: Turnkey pairing, controller list, and button mapping screens (available as Activities and embeddable Composables).
- 🌐 **HTML5 Gamepad API & WebView Bridge**: `Switch2WebViewBridge` injects standard W3C Gamepad API (`navigator.getGamepads()`, `gamepadconnected`, Gamepad vibration) into Android WebViews for cloud gaming services like Better xCloud, GeForce NOW, and web emulators.
- 🕹️ **Android Gamepad Bridge**: `Switch2GamepadBridge` to generate synthetic `MotionEvent` and `KeyEvent` for native Android game engines.

---

## 📦 Installation

### Gradle (Module Dependency or Composite Build)

Include the module in your `settings.gradle`:

```groovy
includeBuild('/path/to/switch-2-controllers-android')
```

Or add as a subproject module:

```groovy
include ':switch2controllers'
project(':switch2controllers').projectDir = new File('/path/to/switch-2-controllers-android/switch2controllers')
```

In your app's `build.gradle`:

```groovy
dependencies {
    implementation 'io.github.rodrigo:switch-2-controllers-android:1.0.0'
    // or
    implementation project(':switch2controllers')
}
```

---

## 🚀 Quick Start

### 1. Initialize `Switch2Manager`

```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var switch2Manager: Switch2Manager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        switch2Manager = Switch2Manager.getInstance(this)
        
        // Listen for controller state updates
        switch2Manager.addListener(object : Switch2ControllerListener {
            override fun onControllerAdded(controller: Switch2Controller) {
                Log.i("Switch2", "Controller connected: ${controller.name} (id: ${controller.controllerId})")
            }

            override fun onControllerRemoved(controller: Switch2Controller) {
                Log.i("Switch2", "Controller disconnected: ${controller.name}")
            }

            override fun onControllerStateReported(state: Switch2ControllerState) {
                // Button bitflags
                val isAPressed = (state.buttonFlags and Switch2ButtonFlags.A_FLAG) != 0
                val isBPressed = (state.buttonFlags and Switch2ButtonFlags.B_FLAG) != 0

                // Analog stick axes (-1.0f to 1.0f)
                val lsX = state.leftStickX
                val lsY = state.leftStickY
                val rsX = state.rightStickX
                val rsY = state.rightStickY

                // Triggers (0.0f to 1.0f)
                val lt = state.leftTrigger
                val rt = state.rightTrigger
            }

            override fun onControllerMotionReported(motion: Switch2MotionState) {
                // IMU data (Accelerometer: m/s^2, Gyroscope: deg/s)
                val x = motion.x
                val y = motion.y
                val z = motion.z
            }
        })
    }

    override fun onStart() {
        super.onStart()
        switch2Manager.start()
    }

    override fun onStop() {
        super.onStop()
        switch2Manager.stop()
    }
}
```

---

### 2. Launch UI for Pairing & Settings

```kotlin
// Open the paired controller manager screen
switch2Manager.openControllersActivity(this)

// Or open directly the BLE pairing screen
switch2Manager.openPairingActivity(this)
```

---

### 3. WebView & Cloud Gaming (e.g. Better xCloud)

```kotlin
val webView = findViewById<WebView>(R.id.webview)
val webViewBridge = Switch2WebViewBridge(switch2Manager)

// 1. Attach native JavascriptInterface
webViewBridge.attachToWebView(webView)

// 2. Inject W3C Gamepad API polyfill on page load
webView.webViewClient = object : WebViewClient() {
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.let { webViewBridge.injectPolyfill(it) }
    }
}
```

---

## 📱 Example Applications

Check out real-world applications powered by this library:

- **[Mass Fusion Android](https://github.com/rodrigmatrix/mass-fusion-android)**: A high-performance Moonlight game streaming client fork of Artemis featuring native low-latency BLE drivers for Switch 2 Pro Controllers and Joy-Con 2 pairs.
- **[Better xCloud Android](https://github.com/rodrigmatrix/better-xcloud-android)**: Android application for Better xCloud featuring direct WebView Gamepad API injection for seamless Xbox Cloud Gaming with Switch 2 controllers.

---

## 🛠️ Testing & Building

To run unit tests:
```bash
./gradlew test
```

To build release AAR:
```bash
./gradlew assembleRelease
```
