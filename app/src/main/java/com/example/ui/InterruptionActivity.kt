package com.example.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.data.PreferenceHelper
import com.example.data.TriggerLog
import com.example.data.api.GeminiService
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InterruptionActivity : ComponentActivity() {
    private lateinit var prefs: PreferenceHelper
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = PreferenceHelper(this)
        db = AppDatabase.getDatabase(this)

        val targetPackageName = intent.getStringExtra("TARGET_PACKAGE_NAME") ?: "com.example"
        val targetAppName = intent.getStringExtra("TARGET_APP_NAME") ?: "Social App"

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    InterruptionScreen(
                        targetAppName = targetAppName,
                        targetPackageName = targetPackageName,
                        activeFocusMode = prefs.activeFocusMode,
                        modifier = Modifier.padding(innerPadding),
                        onActionCompleted = { isContinued ->
                            logActionAndExit(targetPackageName, targetAppName, isContinued)
                        }
                    )
                }
            }
        }
    }

    private fun logActionAndExit(packageName: String, appName: String, isContinued: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            db.triggerLogDao().insertLog(
                TriggerLog(
                    packageName = packageName,
                    appName = appName,
                    focusMode = prefs.activeFocusMode,
                    actionTaken = if (isContinued) "CONTINUED" else "AVOIDED"
                )
            )

            if (isContinued) {
                // Bypass for 10 minutes
                prefs.setTemporaryBypass(packageName, 10)
                withContext(Dispatchers.Main) {
                    finish() // Close overlay, allowing user to access target app
                }
            } else {
                // Send back to home screen
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }
    }
}

enum class BreathingPhase(val label: String, val scale: Float, val color: Color) {
    IN("Breathe In", 1.4f, Color(0xFF6366F1)),   // Indigo
    HOLD("Hold", 1.4f, Color(0xFF8B5CF6)),      // Purple
    OUT("Breathe Out", 0.9f, Color(0xFF10B981))  // Emerald
}

@Composable
fun InterruptionScreen(
    targetAppName: String,
    targetPackageName: String,
    activeFocusMode: String,
    modifier: Modifier = Modifier,
    onActionCompleted: (Boolean) -> Unit
) {
    val timerDuration = remember(activeFocusMode) {
        when (activeFocusMode) {
            "Standard" -> 4
            "Study Mode" -> 5
            "Deep Work", "Deep Work Mode" -> 6
            "Sleep", "Sleep Mode" -> 6
            "Dopamine Detox", "Dopamine Detox Mode" -> 8
            else -> 4
        }
    }
    var timerRemaining by remember(activeFocusMode) { mutableStateOf(timerDuration) }
    var countdownFinished by remember { mutableStateOf(false) }
    var reflectionPrompt by remember { mutableStateOf("Take a quiet breath before continuing...") }
    var promptLoading by remember { mutableStateOf(true) }

    // Breathing Animation States
    var currentPhase by remember { mutableStateOf(BreathingPhase.IN) }
    val animatedScale by animateFloatAsState(
        targetValue = currentPhase.scale,
        animationSpec = tween(
            durationMillis = if (currentPhase == BreathingPhase.HOLD) 1500 else 2500,
            easing = LinearOutSlowInEasing
        ),
        label = "BreathingScale"
    )

    val animatedColor by animateColorAsState(
        targetValue = currentPhase.color,
        animationSpec = tween(1000),
        label = "BreathingColor"
    )

    // Load AI Prompt
    LaunchedEffect(targetAppName, activeFocusMode) {
        promptLoading = true
        reflectionPrompt = GeminiService.generateReflectionPrompt(targetAppName, activeFocusMode)
        promptLoading = false
    }

    // Timer Countdown
    LaunchedEffect(activeFocusMode) {
        countdownFinished = false
        while (timerRemaining > 0) {
            delay(1000)
            timerRemaining--
        }
        countdownFinished = true
    }

    // Breathing Phase Cycle Loop
    LaunchedEffect(Unit) {
        while (true) {
            currentPhase = BreathingPhase.IN
            delay(2500)
            currentPhase = BreathingPhase.HOLD
            delay(1500)
            currentPhase = BreathingPhase.OUT
            delay(2500)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C10)),
        contentAlignment = Alignment.Center
    ) {
        // Center background glowing aura that adapts to current breathing color accent
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-40).dp)
                .size(380.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            animatedColor.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = CircleShape,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Mindful Mode",
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$activeFocusMode Mode Active",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF818CF8)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "OneSec Pause",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Interrupting access to $targetAppName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8)
                )
            }

            // Middle Breathing Animation Circle & Prompt
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(240.dp)
                ) {
                    // Outer translucent halo 1 with adaptive scale and color
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .scale(animatedScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        animatedColor.copy(alpha = 0.12f),
                                        animatedColor.copy(alpha = 0.02f)
                                    )
                                )
                            )
                            .border(androidx.compose.foundation.BorderStroke(1.dp, animatedColor.copy(alpha = 0.20f)), CircleShape)
                    )
                    // Secondary glass halo
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .scale(animatedScale * 0.92f)
                            .clip(CircleShape)
                            .background(animatedColor.copy(alpha = 0.18f))
                            .border(androidx.compose.foundation.BorderStroke(1.dp, animatedColor.copy(alpha = 0.30f)), CircleShape)
                    )
                    // Core breathing circle with Countdown
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .scale(if (currentPhase == BreathingPhase.HOLD) 1f else animatedScale * 0.75f)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        animatedColor,
                                        animatedColor.copy(alpha = 0.80f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (!countdownFinished) {
                                Text(
                                    text = "$timerRemaining",
                                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    text = "Done",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = currentPhase.label,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = animatedColor,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // AI Prompt card with glass boundaries
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "AI REFLECTION PROMPT",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF818CF8),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (promptLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF6366F1),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = reflectionPrompt,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.testTag("reflection_prompt")
                            )
                        }
                    }
                }
            }

            // Bottom Buttons Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Primary Action Button (Turn Back is highly encouraged for digital health, so we highlight it first!)
                Button(
                    onClick = { onActionCompleted(false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("turn_back_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I'll Turn Back",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Soft alternative action enabled after interval
                TextButton(
                    onClick = { if (countdownFinished) onActionCompleted(true) },
                    enabled = countdownFinished,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("continue_button"),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF94A3B8),
                        disabledContentColor = Color.White.copy(alpha = 0.15f)
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Continue to $targetAppName mindfully",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (countdownFinished) Color(0xFF94A3B8) else Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Next",
                            modifier = Modifier.size(20.dp),
                            tint = if (countdownFinished) Color(0xFF94A3B8) else Color(0xFF475569)
                        )
                    }
                }
            }
        }
    }
}
