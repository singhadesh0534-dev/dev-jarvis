package com.adesh.devjarvis.ui.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.ui.theme.HudShapes
import com.adesh.devjarvis.ui.theme.glass
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The JARVIS sign-in: a 3D holographic globe boots up, JARVIS introduces himself, then one tap with Google (or email).
 * Logged in, the same screen is the account page: who is signed in, cloud sync, save now, logout.
 */
@Composable
fun AccountScreen(viewModel: AccountViewModel, firstRun: Boolean, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val googleOn by viewModel.googleOn.collectAsStateWithLifecycle()
    val time by rememberClock()

    var started by remember { mutableStateOf(false) }
    var introDone by remember { mutableStateOf(!firstRun) }
    LaunchedEffect(Unit) { started = true; delay(if (firstRun) 1700 else 350); introDone = true }
    val appear by animateFloatAsState(if (started) 1f else 0f, tween(1400, easing = FastOutSlowInEasing), label = "appear")

    var emailOpen by rememberSaveable { mutableStateOf(false) }
    var signup by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    // A login that happened on this screen: celebrate, and on first run carry on by itself once the data is back.
    var sawLoggedOut by remember { mutableStateOf(!state.loggedIn) }
    if (!state.loggedIn) sawLoggedOut = true
    val justLoggedIn = state.loggedIn && sawLoggedOut
    LaunchedEffect(justLoggedIn, state.busy) {
        if (firstRun && justLoggedIn && !state.busy) { delay(2200); onDone() }
    }

    val accent = if (state.loggedIn) Hud.Green else Hud.Cyan
    val holoSize by animateDpAsState(if ((emailOpen || !googleOn) && !state.loggedIn) 190.dp else 260.dp, tween(500), label = "holo")
    val speed by animateFloatAsState(if (state.busy) 3.5f else 1f, tween(600), label = "speed")

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Hud.Abyss, Hud.Background, Color(0xFF041322))))) {
        HoloBackdrop(time, accent, Modifier.fillMaxSize())

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().imePadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!firstRun) IconButton(onClick = onDone) { Icon(Icons.Filled.ArrowBack, contentDescription = "Wapas", tint = Hud.Ice) }
                Spacer(Modifier.weight(1f))
                BlinkDot(time, accent)
                Text(
                    if (state.loggedIn) "  SECURE LINK · ONLINE" else "  SECURE LINK",
                    style = TextStyle(fontSize = 10.sp, letterSpacing = 2.5.sp, fontWeight = FontWeight.SemiBold), color = Hud.Dim,
                )
            }

            JarvisHologram(
                time = time, speed = speed, color = accent, busy = state.busy,
                modifier = Modifier.size(holoSize).graphicsLayer { alpha = appear; scaleX = 0.55f + 0.45f * appear; scaleY = scaleX },
            )

            Text(
                "J.A.R.V.I.S",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold, letterSpacing = 8.sp,
                    brush = Brush.linearGradient(listOf(Hud.White, Hud.Ice, accent)),
                ),
                modifier = Modifier.graphicsLayer { alpha = appear },
            )
            Spacer(Modifier.height(6.dp))
            val greeting = when {
                justLoggedIn && state.busy -> "Pehchaan confirm. Aapki yaadein la raha hoon…"
                state.loggedIn -> "Welcome back, ${displayName(state.email)}."
                signup && (emailOpen || !googleOn) -> "Naya account. Do minute, aur JARVIS hamesha aapka."
                else -> "Namaste. Main JARVIS hoon. Login kijiye — aapka sab kuch, har phone pe."
            }
            Typewriter(greeting, Modifier.fillMaxWidth().height(44.dp), if (started) 500 else 0)

            AnimatedVisibility(introDone, enter = fadeIn(tween(500)) + slideInVertically(tween(600)) { it / 3 }) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp)
                        .background(Hud.Background.copy(alpha = 0.78f), HudShapes.Panel)
                        .glass(accent, HudShapes.Panel, tint = 0.9f, edge = 0.8f)
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!viewModel.available) {
                        Text("Login server is build mein chalu nahi hai.", color = Hud.Dim, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        GlowButton("Aage badho", accent, enabled = true) { viewModel.skip(); onDone() }
                    } else if (state.loggedIn) {
                        ProfilePanel(state.email, time, accent, state.busy, firstRun,
                            onSave = { viewModel.syncNow() }, onLogout = { viewModel.logOut() }, onContinue = onDone)
                    } else {
                        if (state.busy) ScanLine(time, accent)
                        // Google only when the server has it switched on; otherwise email is the way in, already open.
                        if (googleOn) {
                            GoogleButton(enabled = !state.busy) { viewModel.google() }
                            OrDivider()
                        }
                        AnimatedVisibility(googleOn && !emailOpen, enter = fadeIn(), exit = fadeOut() + shrinkVertically()) {
                            GhostButton("Email se login / signup", Hud.Ice) { emailOpen = true }
                        }
                        AnimatedVisibility(emailOpen || !googleOn, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Segmented(signup, accent) { signup = it }
                                Spacer(Modifier.height(12.dp))
                                HoloField("Email", email, Icons.Filled.Email, KeyboardType.Email, ImeAction.Next) { email = it.trim() }
                                Spacer(Modifier.height(10.dp))
                                HoloField(if (signup) "Naya password (6+ akshar)" else "Password", password, Icons.Filled.Lock,
                                    KeyboardType.Password, ImeAction.Done, secret = true) { password = it }
                                Spacer(Modifier.height(16.dp))
                                GlowButton(if (signup) "ACCOUNT BANAO" else "LOGIN", accent, enabled = !state.busy) {
                                    if (signup) viewModel.signUp(email, password) else viewModel.logIn(email, password)
                                }
                                if (!signup) TextButton(onClick = { viewModel.forgot(email) }) {
                                    Text("Password bhool gaye?", color = Hud.Dim, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = Hud.Dim, modifier = Modifier.size(12.dp))
                            Text("  Aapki API keys kabhi phone se bahar nahi jaati", style = MaterialTheme.typography.labelSmall, color = Hud.Dim)
                        }
                    }
                    if (state.message.isNotBlank() && !(state.loggedIn && !state.busy && state.message.startsWith("Login ho gaya"))) {
                        StatusChip(state.message, time, accent)
                    }
                }
            }
            if (firstRun && !state.loggedIn && introDone) {
                TextButton(onClick = { viewModel.skip(); onDone() }, modifier = Modifier.padding(top = 6.dp)) {
                    Text("Abhi nahi, baad mein  →", color = Hud.Dim)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------ panels

@Composable
private fun ProfilePanel(
    email: String, time: Float, accent: Color, busy: Boolean, firstRun: Boolean,
    onSave: () -> Unit, onLogout: () -> Unit, onContinue: () -> Unit,
) {
    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            rotateRing(time * 60f, accent)
        }
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Hud.Panel))),
            contentAlignment = Alignment.Center,
        ) {
            Text(displayName(email).take(1).uppercase(), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Hud.White)
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(displayName(email), style = MaterialTheme.typography.titleMedium, color = Hud.White)
    Text(email, style = MaterialTheme.typography.bodySmall, color = Hud.Dim)
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.border(1.dp, accent.copy(alpha = 0.5f), HudShapes.Pill).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BlinkDot(time, accent)
        Text(if (busy) "  SYNC HO RAHA HAI…" else "  CLOUD SYNC · ON", style = TextStyle(fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold), color = accent)
    }
    Spacer(Modifier.height(14.dp))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Yaadein aur notes", "Skills aur routines", "Haal ki baat-cheet aur roop").forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Text("  $line — har baat-cheet ke baad khud save", style = MaterialTheme.typography.bodySmall, color = Hud.Ice)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    if (firstRun) {
        GlowButton("JARVIS KHOLO  →", accent, enabled = !busy, onClick = onContinue)
        Spacer(Modifier.height(10.dp))
    }
    GhostButton(if (busy) "Save ho raha hai…" else "Abhi save karo", Hud.Ice, enabled = !busy, onClick = onSave)
    Spacer(Modifier.height(10.dp))
    GhostButton("Logout", Hud.Red, enabled = !busy, onClick = onLogout)
}

@Composable
private fun GoogleButton(enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(54.dp)
            .shadow(14.dp, HudShapes.Pill, ambientColor = Hud.Cyan, spotColor = Hud.Cyan)
            .clip(HudShapes.Pill)
            .background(Color.White)
            .clickable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.55f },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GoogleLogo(Modifier.size(22.dp))
        Text("   Google se continue karein", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF1F1F1F))
    }
}

@Composable
private fun GoogleLogo(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val stroke = w * 0.2f
        val inset = stroke / 2
        val arcSize = Size(w - stroke, w - stroke)
        val tl = Offset(inset, inset)
        fun arc(color: Color, start: Float, sweep: Float) = drawArc(color, start, sweep, false, tl, arcSize, style = Stroke(stroke))
        arc(Color(0xFFEA4335), 205f, 115f)  // red, top
        arc(Color(0xFFFBBC05), 145f, 62f)   // yellow, left
        arc(Color(0xFF34A853), 35f, 112f)   // green, bottom
        arc(Color(0xFF4285F4), -8f, 45f)    // blue, right
        drawLine(Color(0xFF4285F4), Offset(w * 0.5f, w * 0.5f), Offset(w - inset * 0.3f, w * 0.5f), stroke)
    }
}

@Composable
private fun OrDivider() {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Hud.Line))))
        Text("  YA EMAIL SE  ", style = TextStyle(fontSize = 10.sp, letterSpacing = 2.5.sp), color = Hud.Dim)
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(Hud.Line, Color.Transparent))))
    }
}

@Composable
private fun Segmented(signup: Boolean, accent: Color, onPick: (Boolean) -> Unit) {
    val x by animateFloatAsState(if (signup) 1f else 0f, tween(300), label = "seg")
    Box(Modifier.fillMaxWidth().height(42.dp).clip(HudShapes.Pill).background(Hud.Abyss.copy(alpha = 0.7f)).border(1.dp, Hud.Line, HudShapes.Pill)) {
        // sliding highlight
        Box(
            Modifier.fillMaxWidth(0.5f).height(42.dp)
                .graphicsLayer { translationX = x * size.width }
                .padding(3.dp)
                .clip(HudShapes.Pill)
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.85f), Hud.Teal.copy(alpha = 0.85f)))),
        )
        Row(Modifier.fillMaxSize()) {
            listOf(false to "LOGIN", true to "SIGNUP").forEach { (value, label) ->
                Box(
                    Modifier.weight(1f).fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { onPick(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = TextStyle(fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                        color = if (value == signup) Hud.Background else Hud.Dim)
                }
            }
        }
    }
}

@Composable
private fun HoloField(
    label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, type: KeyboardType, ime: ImeAction,
    secret: Boolean = false, onChange: (String) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = Hud.Dim) },
        trailingIcon = if (secret) { { TextButton(onClick = { visible = !visible }) { Text(if (visible) "HIDE" else "SHOW", color = Hud.Cyan, fontSize = 11.sp) } } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ime),
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Hud.Cyan, unfocusedBorderColor = Hud.Line, cursorColor = Hud.Cyan,
            focusedLabelColor = Hud.Cyan, unfocusedLabelColor = Hud.Dim, focusedTextColor = Hud.White, unfocusedTextColor = Hud.Ice,
            focusedContainerColor = Hud.Abyss.copy(alpha = 0.6f), unfocusedContainerColor = Hud.Abyss.copy(alpha = 0.4f),
        ),
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun GlowButton(text: String, accent: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp)
            .shadow(if (enabled) 18.dp else 0.dp, HudShapes.Pill, ambientColor = accent, spotColor = accent)
            .clip(HudShapes.Pill)
            .background(Brush.horizontalGradient(listOf(accent, Hud.Teal)))
            .clickable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(fontSize = 14.sp, letterSpacing = 2.5.sp, fontWeight = FontWeight.Bold), color = Hud.Background)
    }
}

@Composable
private fun GhostButton(text: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(48.dp).clip(HudShapes.Pill)
            .border(BorderStroke(1.dp, color.copy(alpha = 0.45f)), HudShapes.Pill)
            .background(color.copy(alpha = 0.05f))
            .clickable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f },
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = color) }
}

@Composable
private fun StatusChip(message: String, time: Float, accent: Color) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(12.dp))
            .background(Hud.Panel.copy(alpha = 0.8f)).border(1.dp, Hud.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BlinkDot(time, accent)
        Text("  $message", style = MaterialTheme.typography.bodySmall, color = Hud.Ice)
    }
}

@Composable
private fun BlinkDot(time: Float, color: Color) {
    val a = 0.35f + 0.65f * (0.5f + 0.5f * sin(time * 4f))
    Box(Modifier.size(7.dp).clip(CircleShape).background(color.copy(alpha = a)))
}

@Composable
private fun ScanLine(time: Float, accent: Color) {
    Canvas(Modifier.fillMaxWidth().height(3.dp).padding(bottom = 1.dp)) {
        val p = (time * 0.8f) % 1f
        val x = size.width * p
        drawLine(Brush.horizontalGradient(listOf(Color.Transparent, accent, Color.Transparent), startX = x - 120f, endX = x + 120f),
            Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, StrokeCap.Round)
    }
    Spacer(Modifier.height(12.dp))
}

/** JARVIS writes his line letter by letter, with a blinking cursor. */
@Composable
private fun Typewriter(text: String, modifier: Modifier, startDelayMs: Int) {
    var shown by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        delay(startDelayMs.toLong())
        for (i in 1..text.length) { shown = i; delay(24) }
    }
    val cursor = if (shown < text.length) "▌" else ""
    Text(text.take(shown) + cursor, modifier = modifier, style = MaterialTheme.typography.bodyMedium, color = Hud.Ice, textAlign = TextAlign.Center)
}

// ------------------------------------------------------------------ 3D hologram

/** Seconds since the screen opened, ticking every frame. */
@Composable
private fun rememberClock(): State<Float> = produceState(0f) {
    val start = withFrameNanos { it }
    while (true) withFrameNanos { value = (it - start) / 1_000_000_000f }
}

/**
 * A wireframe globe turning in 3D (latitude + longitude rings, projected with perspective so the near side is bright and
 * the far side faint), a scanning band, three tilted orbit rings with satellites, a pulsing core and a rotating HUD dial.
 */
@Composable
private fun JarvisHologram(time: Float, speed: Float, color: Color, busy: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension * 0.26f
        val yaw = time * 0.45f * speed
        val pitch = 0.42f
        val cp = cos(pitch); val sp = sin(pitch)
        val out = FloatArray(3)
        fun proj(x: Float, y: Float, z: Float, a: Float) {
            val ca = cos(a); val sa = sin(a)
            val x1 = x * ca + z * sa
            val z1 = -x * sa + z * ca
            val y2 = y * cp - z1 * sp
            val z2 = y * sp + z1 * cp
            val f = 3.4f / (3.4f + z2 / r)
            out[0] = c.x + x1 * f; out[1] = c.y + y2 * f; out[2] = (z2 / r).coerceIn(-1.6f, 1.6f)
        }
        fun ring(points: Int, width: Float, glow: Float, dashed: Boolean = false, a: Float = yaw, point: (Double) -> Triple<Float, Float, Float>) {
            var px = 0f; var py = 0f; var pz = 0f
            for (i in 0..points) {
                val t = i * 2.0 * PI / points
                val (x, y, z) = point(t)
                proj(x, y, z, a)
                if (i > 0 && !(dashed && i % 2 == 0)) {
                    val depth = (pz + out[2]) / 2f
                    val alpha = ((0.62f - depth * 0.42f) * glow).coerceIn(0.04f, 1f)
                    drawLine(color.copy(alpha = alpha), Offset(px, py), Offset(out[0], out[1]), width, StrokeCap.Round)
                }
                px = out[0]; py = out[1]; pz = out[2]
            }
        }

        // core glow
        val pulse = 0.85f + 0.15f * sin(time * (if (busy) 7f else 2.4f))
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0.08f), Color.Transparent), c, r * 1.25f * pulse), r * 1.25f * pulse, c)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f), color.copy(alpha = 0.6f), Color.Transparent), c, r * 0.26f), r * 0.26f * pulse, c)

        // globe: latitude rings
        for (latDeg in listOf(-60, -30, 0, 30, 60)) {
            val lat = Math.toRadians(latDeg.toDouble())
            val rr = (r * cos(lat)).toFloat(); val y = (r * sin(lat)).toFloat()
            ring(56, 1.4f, 0.9f) { t -> Triple((rr * cos(t)).toFloat(), y, (rr * sin(t)).toFloat()) }
        }
        // globe: longitude rings
        for (k in 0 until 6) {
            val lon = k * PI / 6
            ring(56, 1.2f, 0.75f) { t -> Triple((r * cos(t) * cos(lon)).toFloat(), (r * sin(t)).toFloat(), (r * cos(t) * sin(lon)).toFloat()) }
        }
        // scanning band
        val scanLat = sin(time * 0.9) * 1.2
        val sr = (r * cos(scanLat)).toFloat(); val sy = (r * sin(scanLat)).toFloat()
        ring(64, 3f, 1.4f) { t -> Triple((sr * cos(t)).toFloat(), sy, (sr * sin(t)).toFloat()) }

        // orbit rings with satellites
        val orbits = listOf(Triple(1.45f, 0.9f, 0.35f), Triple(1.65f, -0.5f, -0.6f), Triple(1.85f, 1.35f, 0.15f))
        orbits.forEachIndexed { i, (k, tiltX, tiltZ) ->
            val spin = yaw * (0.6f + i * 0.35f) * (if (i % 2 == 0) 1 else -1)
            val rad = r * k
            val ctx = cos(tiltX); val stx = sin(tiltX); val ctz = cos(tiltZ); val stz = sin(tiltZ)
            val orbitPoint: (Double) -> Triple<Float, Float, Float> = { t ->
                val x0 = (rad * cos(t)).toFloat(); val z0 = (rad * sin(t)).toFloat()
                val x1 = x0 * ctz; val y1 = x0 * stz
                Triple(x1, y1 * ctx - z0 * stx, y1 * stx + z0 * ctx)
            }
            ring(90, 1.1f, 0.65f, dashed = i == 1, a = spin, point = orbitPoint)
            val (sx, syy, sz) = orbitPoint(time * (0.9 + i * 0.4) * speed)
            proj(sx, syy, sz, spin)
            val near = (0.75f - out[2] * 0.4f).coerceIn(0.2f, 1f)
            drawCircle(color.copy(alpha = near * 0.35f), 9f * near, Offset(out[0], out[1]))
            drawCircle(Color.White.copy(alpha = near), 3.4f * near, Offset(out[0], out[1]))
        }

        // HUD dial
        hudDial(c, r * 2.05f, time, color, busy)
    }
}

private fun DrawScope.hudDial(c: Offset, radius: Float, time: Float, color: Color, busy: Boolean) {
    val rot = time * (if (busy) 90f else 18f)
    for (i in 0 until 72) {
        val a = Math.toRadians((i * 5 + rot).toDouble())
        val long = i % 6 == 0
        val r1 = radius - if (long) 10f else 5f
        drawLine(
            color.copy(alpha = if (long) 0.5f else 0.18f),
            Offset(c.x + (r1 * cos(a)).toFloat(), c.y + (r1 * sin(a)).toFloat()),
            Offset(c.x + (radius * cos(a)).toFloat(), c.y + (radius * sin(a)).toFloat()),
            if (long) 2f else 1.2f,
        )
    }
    val tl = Offset(c.x - radius - 10f, c.y - radius - 10f)
    val s = Size((radius + 10f) * 2, (radius + 10f) * 2)
    drawArc(color.copy(alpha = 0.55f), -rot * 1.6f, 70f, false, tl, s, style = Stroke(2.5f, cap = StrokeCap.Round))
    drawArc(color.copy(alpha = 0.35f), -rot * 1.6f + 180f, 40f, false, tl, s, style = Stroke(2.5f, cap = StrokeCap.Round))
}

private fun DrawScope.rotateRing(deg: Float, color: Color) {
    drawArc(
        Brush.sweepGradient(listOf(color, Color.Transparent, color.copy(alpha = 0.6f), Color.Transparent, color)),
        deg, 360f, false, style = Stroke(3f),
    )
}

/** Stars that twinkle, and a perspective grid floor sliding towards you. */
@Composable
private fun HoloBackdrop(time: Float, color: Color, modifier: Modifier) {
    val stars = remember { val rnd = Random(7); List(80) { Triple(rnd.nextFloat(), rnd.nextFloat() * 0.7f, rnd.nextFloat() * 6.28f) } }
    Canvas(modifier) {
        val w = size.width; val h = size.height
        stars.forEach { (x, y, ph) ->
            val a = 0.15f + 0.5f * (0.5f + 0.5f * sin(time * 1.3f + ph))
            drawCircle(Hud.Ice.copy(alpha = a), 1.6f, Offset(x * w, y * h))
        }
        val horizon = h * 0.64f
        val vx = w / 2
        for (i in -12..12) {
            drawLine(color.copy(alpha = 0.10f), Offset(vx + i * w * 0.03f, horizon), Offset(vx + i * w * 0.42f, h), 1f)
        }
        val shift = (time * 0.35f) % 1f
        for (k in 0 until 14) {
            val p = (k + shift) / 14f
            val y = horizon + (h - horizon) * p * p
            drawLine(color.copy(alpha = 0.22f * p), Offset(0f, y), Offset(w, y), 1f)
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Hud.Abyss.copy(alpha = 0.0f), Hud.Abyss.copy(alpha = 0.5f)), startY = horizon, endY = h))
    }
}

private fun displayName(email: String): String {
    val raw = email.substringBefore('@').replace('.', ' ').replace('_', ' ').filter { !it.isDigit() }.trim()
    return raw.split(' ').filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }.ifBlank { "Sir" }
}
