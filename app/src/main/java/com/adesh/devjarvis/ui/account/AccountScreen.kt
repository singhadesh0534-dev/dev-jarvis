package com.adesh.devjarvis.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.ui.theme.HudShapes

/**
 * Login / signup: Google in one tap (browser), or email + password. First run shows "Baad mein" too; logged in it shows
 * the account, "Abhi save karo" and Logout.
 */
@Composable
fun AccountScreen(viewModel: AccountViewModel, firstRun: Boolean, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var signup by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    // First run: once logged in and the data is back, go on by itself.
    var wasLoggedIn by remember { mutableStateOf(state.loggedIn) }
    LaunchedEffect(state.loggedIn, state.busy) {
        if (firstRun && state.loggedIn && !state.busy && !wasLoggedIn) onDone()
        if (!state.loggedIn) wasLoggedIn = false
    }

    Box(Modifier.fillMaxSize().background(Hud.Background)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!firstRun) Row(Modifier.fillMaxWidth()) { TextButton(onClick = onDone) { Text("‹ Wapas", color = Hud.Cyan) } }
            Spacer(Modifier.height(if (firstRun) 40.dp else 8.dp))
            Text(
                "DEV JARVIS",
                style = MaterialTheme.typography.headlineMedium.copy(
                    letterSpacing = 6.sp, fontWeight = FontWeight.Bold,
                    brush = Brush.linearGradient(listOf(Hud.White, Hud.Ice, Hud.Cyan)),
                ),
            )
            Text(
                if (state.loggedIn) "Aapka account" else if (signup) "Naya account banao" else "Login karo — aapka sab kuch wapas",
                style = MaterialTheme.typography.bodyMedium, color = Hud.Dim, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
            )

            if (!viewModel.available) {
                Text("Account server abhi is build mein set nahi hai.", color = Hud.Dim, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Aage badho") { viewModel.skip(); onDone() }
                return@Column
            }

            if (state.loggedIn) {
                Text(state.email.ifEmpty { "Logged in" }, style = MaterialTheme.typography.titleMedium, color = Hud.White)
                Text("Yaadein, notes, skills, routines har baat-cheet ke baad apne aap is account mein save hote hain. API keys kabhi nahi jaati.",
                    style = MaterialTheme.typography.bodySmall, color = Hud.Dim, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
                PrimaryButton("Abhi save karo", enabled = !state.busy) { viewModel.syncNow() }
                Spacer(Modifier.height(10.dp))
                GhostButton("Logout") { viewModel.logOut() }
                if (firstRun) { Spacer(Modifier.height(10.dp)); GhostButton("Aage badho") { onDone() } }
            } else {
                PrimaryButton("G   Google se login", enabled = !state.busy) { viewModel.google() }
                Text("ya email se", style = MaterialTheme.typography.labelMedium, color = Hud.Dim, modifier = Modifier.padding(vertical = 14.dp))
                Field("Email", email, KeyboardType.Email) { email = it.trim() }
                Spacer(Modifier.height(8.dp))
                Field("Password (kam se kam 6)", password, KeyboardType.Password, secret = true) { password = it }
                Spacer(Modifier.height(14.dp))
                if (signup) PrimaryButton("Signup", enabled = !state.busy) { viewModel.signUp(email, password) }
                else PrimaryButton("Login", enabled = !state.busy) { viewModel.logIn(email, password) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { signup = !signup }) {
                        Text(if (signup) "Account hai? Login" else "Naya ho? Signup", color = Hud.Cyan)
                    }
                    if (!signup) TextButton(onClick = { viewModel.forgot(email) }) { Text("Password bhool gaye?", color = Hud.Dim) }
                }
                if (firstRun) { Spacer(Modifier.height(6.dp)); GhostButton("Baad mein") { viewModel.skip(); onDone() } }
            }

            if (state.busy) CircularProgressIndicator(color = Hud.Cyan, modifier = Modifier.padding(top = 18.dp).size(28.dp))
            if (state.message.isNotBlank()) {
                Text(state.message, style = MaterialTheme.typography.bodySmall, color = Hud.Ice, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp))
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, shape = HudShapes.Chip,
        colors = ButtonDefaults.buttonColors(containerColor = Hud.Cyan, contentColor = Hud.Background),
        modifier = Modifier.fillMaxWidth().height(50.dp),
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, shape = HudShapes.Chip, border = BorderStroke(1.dp, Hud.Line),
        modifier = Modifier.fillMaxWidth().height(46.dp),
    ) { Text(text, color = Hud.Ice) }
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType, secret: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = type),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Hud.Cyan, unfocusedBorderColor = Hud.Line, cursorColor = Hud.Cyan,
            focusedLabelColor = Hud.Cyan, unfocusedLabelColor = Hud.Dim, focusedTextColor = Hud.White, unfocusedTextColor = Hud.Ice,
        ),
        shape = HudShapes.Chip, modifier = Modifier.fillMaxWidth(),
    )
}
