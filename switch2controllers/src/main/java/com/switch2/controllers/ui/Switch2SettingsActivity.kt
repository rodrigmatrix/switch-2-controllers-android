package com.switch2.controllers.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme

class Switch2SettingsActivity : ComponentActivity() {
    private lateinit var address: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        address = intent.getStringExtra(EXTRA_ADDRESS) ?: run {
            finish()
            return
        }

        setContent {
            com.switch2.controllers.ui.theme.Switch2Theme {
                Switch2SettingsScreen(
                    address = address,
                    onDone = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_ADDRESS = "address"
    }
}
