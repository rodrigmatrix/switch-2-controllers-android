package com.switch2.controllers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.switch2.controllers.bridge.Switch2GamepadBridge
import com.switch2.controllers.core.Switch2ButtonFlags
import com.switch2.controllers.core.Switch2Constants
import com.switch2.controllers.core.Switch2ControllerState
import com.switch2.controllers.mappings.Switch2ControllerMappings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Switch2Tests {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testProductClassification() {
        assertTrue(Switch2Constants.isSupportedProductId(Switch2Constants.PRODUCT_PRO_CONTROLLER_2))
        assertTrue(Switch2Constants.isSupportedProductId(Switch2Constants.PRODUCT_JOYCON_2_LEFT))
        assertTrue(Switch2Constants.isSupportedProductId(Switch2Constants.PRODUCT_JOYCON_2_RIGHT))
        assertTrue(Switch2Constants.isSupportedProductId(Switch2Constants.PRODUCT_JOYCON_L))
        assertTrue(Switch2Constants.isSupportedProductId(Switch2Constants.PRODUCT_JOYCON_R))
        assertTrue(Switch2Constants.isSupportedProductId(Switch2Constants.PRODUCT_NSO_GAMECUBE_CONTROLLER))
        assertFalse(Switch2Constants.isSupportedProductId(0x1234))

        assertTrue(Switch2Constants.isJoyConLeft(Switch2Constants.PRODUCT_JOYCON_2_LEFT))
        assertTrue(Switch2Constants.isJoyConRight(Switch2Constants.PRODUCT_JOYCON_2_RIGHT))
        assertTrue(Switch2Constants.isJoyCon(Switch2Constants.PRODUCT_JOYCON_2_LEFT))
        assertFalse(Switch2Constants.isJoyCon(Switch2Constants.PRODUCT_PRO_CONTROLLER_2))
    }

    @Test
    fun testRawMaskParsing() {
        assertEquals(0x4000, Switch2ControllerMappings.parseRawMask("0x4000"))
        assertEquals(0x4000, Switch2ControllerMappings.parseRawMask("4000"))
        assertEquals(0, Switch2ControllerMappings.parseRawMask(""))
        assertEquals(0x02000000, Switch2ControllerMappings.parseRawMask("0x02000000"))
    }

    @Test
    fun testDefaultButtonMapping() {
        val testAddress = "aa:bb:cc:dd:ee:ff"
        // Raw mask for A is 0x00000008, maps to Switch2ButtonFlags.A_FLAG (0x1000)
        val mappedA = Switch2ControllerMappings.mapButtons(context, testAddress, 0x00000008)
        assertEquals(Switch2ButtonFlags.A_FLAG, mappedA)

        // Raw mask for B is 0x00000004, maps to Switch2ButtonFlags.B_FLAG (0x2000)
        val mappedB = Switch2ControllerMappings.mapButtons(context, testAddress, 0x00000004)
        assertEquals(Switch2ButtonFlags.B_FLAG, mappedB)

        // Simultaneous A + B
        val mappedAB = Switch2ControllerMappings.mapButtons(context, testAddress, 0x00000008 or 0x00000004)
        assertEquals(Switch2ButtonFlags.A_FLAG or Switch2ButtonFlags.B_FLAG, mappedAB)
    }

    @Test
    fun testCustomButtonMapping() {
        val testAddress = "11:22:33:44:55:66"
        // Remap A button to X_FLAG
        Switch2ControllerMappings.setTarget(context, testAddress, "a", Switch2ButtonFlags.X_FLAG)

        val mapped = Switch2ControllerMappings.mapButtons(context, testAddress, 0x00000008)
        assertEquals(Switch2ButtonFlags.X_FLAG, mapped)
    }

    @Test
    fun testPairedControllerManagement() {
        val mac = "12:34:56:78:9A:BC"
        Switch2ControllerMappings.addPairedController(context, mac, "Test Pro Controller 2", Switch2Constants.PRODUCT_PRO_CONTROLLER_2)

        val list = Switch2ControllerMappings.getPairedControllers(context)
        assertTrue(list.contains(mac))
        assertEquals("Test Pro Controller 2", Switch2ControllerMappings.controllerName(context, mac))
        assertEquals(Switch2Constants.PRODUCT_PRO_CONTROLLER_2, Switch2ControllerMappings.controllerProductId(context, mac))

        Switch2ControllerMappings.removePairedController(context, mac)
        val updatedList = Switch2ControllerMappings.getPairedControllers(context)
        assertFalse(updatedList.contains(mac))
    }

    @Test
    fun testGamepadBridgeEventGeneration() {
        val bridge = Switch2GamepadBridge()
        val state = Switch2ControllerState(
            controllerId = 101,
            buttonFlags = Switch2ButtonFlags.A_FLAG or Switch2ButtonFlags.UP_FLAG,
            leftStickX = 0.75f,
            leftStickY = -0.5f,
            leftTrigger = 1.0f,
            rightTrigger = 0.0f
        )

        val keyEvents = bridge.buildKeyEvents(state, previousFlags = 0)
        assertEquals(2, keyEvents.size)

        val motionEvent = bridge.buildMotionEvent(state)
        assertTrue(motionEvent != null)
        assertEquals(0.75f, motionEvent!!.x, 0.001f)
        assertEquals(-0.5f, motionEvent.y, 0.001f)
    }
}
