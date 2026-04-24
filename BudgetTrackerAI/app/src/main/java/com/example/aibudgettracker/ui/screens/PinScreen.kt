package com.example.aibudgettracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PinMode {
    LOGIN,
    SETUP_ENTER,
    SETUP_CONFIRM
}

@Composable
fun PinScreen(
    mode: PinMode,
    onPinComplete: (String) -> Unit,
    errorMessage: String? = null,
    onCancel: () -> Unit = {}
) {
    var pin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var currentMode by remember { mutableStateOf(mode) }
    var internalError by remember { mutableStateOf<String?>(null) }

    val displayTitle = when (currentMode) {
        PinMode.LOGIN -> "Enter PIN"
        PinMode.SETUP_ENTER -> "Set 6-Digit PIN"
        PinMode.SETUP_CONFIRM -> "Confirm PIN"
    }

    val displaySubtitle = when (currentMode) {
        PinMode.LOGIN -> "Enter your security PIN to continue"
        PinMode.SETUP_ENTER -> "Create a PIN for secure access"
        PinMode.SETUP_CONFIRM -> "Re-enter your PIN to confirm"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 48.dp)
        ) {
            Text(
                text = "MikaFy",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.height(48.dp))
            
            Text(
                text = displayTitle,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Text(
                text = displaySubtitle,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // PIN Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(6) { index ->
                    val isFilled = index < pin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isFilled) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape
                            )
                    )
                }
            }

            if (errorMessage != null || internalError != null) {
                Text(
                    text = errorMessage ?: internalError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 24.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Keypad
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "⌫")
            )

            keys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.5f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    when (key) {
                                        "⌫" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                        "C" -> pin = ""
                                        else -> {
                                            if (pin.length < 6) {
                                                pin += key
                                                if (pin.length == 6) {
                                                    when (currentMode) {
                                                        PinMode.LOGIN -> onPinComplete(pin)
                                                        PinMode.SETUP_ENTER -> {
                                                            firstPin = pin
                                                            pin = ""
                                                            currentMode = PinMode.SETUP_CONFIRM
                                                            internalError = null
                                                        }
                                                        PinMode.SETUP_CONFIRM -> {
                                                            if (pin == firstPin) {
                                                                onPinComplete(pin)
                                                            } else {
                                                                pin = ""
                                                                internalError = "PINs do not match. Try again."
                                                                currentMode = PinMode.SETUP_ENTER
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
