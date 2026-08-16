package com.switch2.controllers.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.switch2.controllers.R
import com.switch2.controllers.core.Switch2Constants
import com.switch2.controllers.mappings.Switch2ControllerMappings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Switch2ControllersScreen(
    pairedControllers: List<String>,
    connectedAddresses: Set<String>,
    combineJoyCons: Boolean,
    isJoyConPairActive: Boolean,
    onCombineJoyConsChanged: (Boolean) -> Unit,
    onPairNewClicked: () -> Unit,
    onSettingsClicked: (address: String) -> Unit,
    onForgetClicked: (address: String) -> Unit,
    onCloseClicked: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Switch 2 Controllers") },
                actions = {
                    if (onCloseClicked != null) {
                        TextButton(onClick = onCloseClicked) {
                            Text("Done")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Button(
                onClick = onPairNewClicked,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Gamepad,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pair New Controller")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Joy-Con Pair Setting Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Use Joy-Cons as a pair", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Left and right Joy-Con 2 stream as one unified controller.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Switch(
                            checked = combineJoyCons,
                            onCheckedChange = onCombineJoyConsChanged
                        )
                    }

                    if (combineJoyCons) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isJoyConPairActive)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isJoyConPairActive) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_joycon_2_pair),
                                        contentDescription = "Joy-Con Pair",
                                        modifier = Modifier.size(width = 32.dp, height = 26.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isJoyConPairActive)
                                        "Joy-Con Pair Active: Left + Right Joy-Cons merged"
                                    else
                                        "Joy-Con Pair: Waiting for both Left and Right to connect",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isJoyConPairActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isJoyConPairActive)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (pairedControllers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No paired Switch 2 controllers found.\nPut your controller in pairing mode and tap Pair New Controller.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Text(
                    text = "Paired Controllers (${pairedControllers.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(pairedControllers) { address ->
                        val name = Switch2ControllerMappings.controllerName(context, address)
                        val productId = Switch2ControllerMappings.controllerProductId(context, address)
                        val isConnected = connectedAddresses.contains(address)
                        val isJoyCon = Switch2Constants.isJoyCon(productId)

                        ControllerItemRow(
                            address = address,
                            name = name,
                            productId = productId,
                            isConnected = isConnected,
                            isJoyCon = isJoyCon,
                            combineJoyCons = combineJoyCons,
                            isJoyConPairActive = isJoyConPairActive,
                            onSettings = { onSettingsClicked(address) },
                            onForget = { onForgetClicked(address) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ControllerItemRow(
    address: String,
    name: String,
    productId: Int,
    isConnected: Boolean,
    isJoyCon: Boolean,
    combineJoyCons: Boolean,
    isJoyConPairActive: Boolean,
    onSettings: () -> Unit,
    onForget: () -> Unit
) {
    val context = LocalContext.current
    val iconRes = Switch2ControllerMappings.getControllerDrawableRes(context, address, productId)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Controller Icon placed on the left side of Name & ID
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(width = 54.dp, height = 54.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = iconRes),
                            contentDescription = name,
                            modifier = Modifier
                                .padding(4.dp)
                                .fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Connection Status Badge
                val isDark = com.switch2.controllers.ui.theme.isAppInDarkTheme(context)
                val badgeBg = if (isConnected) {
                    if (isDark) Color(0xFF143818) else Color(0xFFE8F5E9)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
                val badgeText = if (isConnected) {
                    if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                val badgeDot = if (isConnected) {
                    if (isDark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
                } else {
                    Color.Gray
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = badgeBg,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(color = badgeDot, shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isConnected) "Connected" else "Disconnected",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeText
                        )
                    }
                }
            }

            // Joy-Con Pairing Badge
            if (isJoyCon) {
                Spacer(modifier = Modifier.height(8.dp))
                if (combineJoyCons) {
                    if (isConnected && isJoyConPairActive) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "🎮 Paired as Joy-Con (L+R)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else if (isConnected) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Single Joy-Con (Pair mode on, waiting for pair)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Joy-Con Pair member (Offline)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Individual Joy-Con (Standalone)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSettings,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Settings")
                }
                OutlinedButton(
                    onClick = onForget,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Forget")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Switch2SettingsScreen(
    address: String,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val controllerName = remember { Switch2ControllerMappings.controllerName(context, address) }
    val productId = remember { Switch2ControllerMappings.controllerProductId(context, address) }
    val sources = remember { Switch2ControllerMappings.sourceButtons(context, address) }
    var selectedColor by remember {
        mutableStateOf(Switch2ControllerMappings.getControllerColor(context, address, productId))
    }
    val iconRes = remember(selectedColor) {
        Switch2ControllerMappings.getControllerDrawableRes(context, address, productId)
    }
    var stickSensitivity by remember {
        mutableStateOf(Switch2ControllerMappings.stickSensitivity(context, address))
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Controller Settings") })
        },
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(width = 54.dp, height = 54.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = iconRes),
                            contentDescription = controllerName,
                            modifier = Modifier
                                .padding(4.dp)
                                .fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(text = controllerName, style = MaterialTheme.typography.titleLarge)
                    Text(text = address, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (Switch2Constants.isJoyCon(productId)) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "Joy-Con Color", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                val availableColors = listOf(
                    Triple(Switch2Constants.COLOR_BLUE, "Blue", androidx.compose.ui.graphics.Color(0xFF0AB9E6)),
                    Triple(Switch2Constants.COLOR_RED, "Red", androidx.compose.ui.graphics.Color(0xFFFF3C28)),
                    Triple(Switch2Constants.COLOR_PURPLE, "Purple", androidx.compose.ui.graphics.Color(0xFFA366CC)),
                    Triple(Switch2Constants.COLOR_GREEN, "Green", androidx.compose.ui.graphics.Color(0xFF74D162)),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableColors.forEach { (colorKey, label, colorVal) ->
                        val isSelected = selectedColor == colorKey
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedColor = colorKey
                                Switch2ControllerMappings.setControllerColor(context, address, colorKey)
                            },
                            label = { Text(label) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(colorVal, shape = CircleShape)
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(text = "Joystick Sensitivity", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = stickSensitivity,
                    onValueChange = {
                        stickSensitivity = it
                        Switch2ControllerMappings.setStickSensitivity(context, address, it)
                    },
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(stickSensitivity * 100).toInt()}%",
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .width(48.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text(text = "Button Mapping", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            sources.forEach { source ->
                MappingItemRow(address = address, source = source)
                if (source.editableRawMask) {
                    RawMaskItemRow(address = address, source = source)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
fun MappingItemRow(address: String, source: Switch2ControllerMappings.SourceButton) {
    val context = LocalContext.current
    val targets = Switch2ControllerMappings.targetButtons
    var expanded by remember { mutableStateOf(false) }

    val initialTargetFlag = remember { Switch2ControllerMappings.targetFor(context, address, source) }
    val initialTargetIndex = targets.indexOfFirst { it.flag == initialTargetFlag }.coerceAtLeast(0)
    var selectedTarget by remember { mutableStateOf(targets[initialTargetIndex]) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = source.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )

        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(selectedTarget.label)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                targets.forEach { target ->
                    DropdownMenuItem(
                        text = { Text(target.label) },
                        onClick = {
                            selectedTarget = target
                            Switch2ControllerMappings.setTarget(context, address, source.id, target.flag)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RawMaskItemRow(address: String, source: Switch2ControllerMappings.SourceButton) {
    val context = LocalContext.current
    var maskText by remember { mutableStateOf(Switch2ControllerMappings.rawMaskText(context, address, source.id)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${source.label} raw mask",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        OutlinedTextField(
            value = maskText,
            onValueChange = { maskText = it },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )

        Button(
            onClick = {
                try {
                    val mask = Switch2ControllerMappings.parseRawMask(maskText)
                    Switch2ControllerMappings.setRawMask(context, address, source.id, mask)
                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                } catch (_: NumberFormatException) {
                    Toast.makeText(context, "Use a hex value like 0x4000", Toast.LENGTH_LONG).show()
                }
            }
        ) {
            Text("Save")
        }
    }
}
