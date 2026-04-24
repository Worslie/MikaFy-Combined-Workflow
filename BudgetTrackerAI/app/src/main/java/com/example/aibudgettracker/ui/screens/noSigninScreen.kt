package com.example.aibudgettracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A hard-gate login screen for users who aren't signed in.
 * Simplified to remove friction - completes immediately after login.
 */
@Composable
fun NoSigninScreen(
    onGoogleSignIn: () -> Unit,
    onEmailSignIn: () -> Unit,
    isLoggedIn: Boolean = false,
    onComplete: (isBusiness: Boolean, bizName: String?, industry: String?, description: String?) -> Unit
) {
    // Advance immediately once logged in
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            // Default to personal view on initial signup to reduce friction
            onComplete(false, null, null, null)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LoginView(onGoogleSignIn, onEmailSignIn)
        }
    }
}

@Composable
fun LoginView(onGoogleSignIn: () -> Unit, onEmailSignIn: () -> Unit) {
    // App Title
    Text(
        text = "MikaFy",
        fontSize = 48.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Text(
        text = "Clear. Simple. MikaFy",
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(bottom = 48.dp)
    )

    // Google Sign-In Button
    Button(
        onClick = { onGoogleSignIn() },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Text(
            text = "Sign in with Google",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(Modifier.height(16.dp))

    // Email Sign-In Button
    Button(
        onClick = { onEmailSignIn() },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Text(
            text = "Sign in with Email",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
