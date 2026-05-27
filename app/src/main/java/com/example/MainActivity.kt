package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.service.HabitInterruptionService
import com.example.ui.HabitViewModel
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(this)
        val repository = HabitRepository(database.monitoredAppDao(), database.triggerLogDao())
        val prefs = PreferenceHelper(this)

        val factory = HabitViewModel.Factory(application, repository, prefs)

        setContent {
            MyApplicationTheme {
                val viewModel: HabitViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
                MainScreen(viewModel)
            }
        }
    }
}

enum class NavigationTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    STATS("Stats", Icons.Default.Star),
    APPS("Blocked", Icons.Default.List),
    MODES("Focus Modes", Icons.Default.Build),
    COACH("AI Coach", Icons.Default.Favorite)
}

@Composable
fun MeshBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C10))
    ) {
        // Top-left glowing dot (Indigo)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-80).dp, y = (-80).dp)
                .size(340.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF6366F1).copy(alpha = 0.20f),
                            Color.Transparent
                        )
                    )
                )
        )
        // Bottom-right glowing dot (Emerald)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 80.dp, y = 80.dp)
                .size(340.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF10B981).copy(alpha = 0.16f),
                            Color.Transparent
                        )
                    )
                )
        )
        // Mid-right purple glowing dot
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 100.dp, y = (-60).dp)
                .size(280.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF8B5CF6).copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            content = content
        )
    }
}

@Composable
fun MainScreen(viewModel: HabitViewModel) {
    var activeTab by remember { mutableStateOf(NavigationTab.STATS) }
    val activeFocusMode by viewModel.activeFocusMode.collectAsStateWithLifecycle()
    val isHabitBreakerEnabled by viewModel.isHabitBreakerEnabled.collectAsStateWithLifecycle()

    MeshBackground {
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier,
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF161B22).copy(alpha = 0.75f),
                    tonalElevation = 0.dp,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    NavigationTab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF6366F1),
                                selectedTextColor = Color(0xFF818CF8),
                                unselectedIconColor = Color(0xFF94A3B8).copy(alpha = 0.7f),
                                unselectedTextColor = Color(0xFF94A3B8).copy(alpha = 0.7f),
                                indicatorColor = Color(0xFF6366F1).copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (activeTab) {
                    NavigationTab.STATS -> StatsTab(viewModel, activeFocusMode, isHabitBreakerEnabled)
                    NavigationTab.APPS -> AppsTab(viewModel)
                    NavigationTab.MODES -> FocusModesTab(viewModel, activeFocusMode)
                    NavigationTab.COACH -> AICoachTab(viewModel)
                }
            }
        }
    }
}

@Composable
fun StatsTab(viewModel: HabitViewModel, activeMode: String, isEnabled: Boolean) {
    val context = LocalContext.current
    val analytics by viewModel.analyticsData.collectAsStateWithLifecycle()
    val monitoredApps by viewModel.allMonitoredApps.collectAsStateWithLifecycle()
    
    // Check if accessibility service is active
    var isServiceActive by remember { mutableStateOf(false) }

    // Poll service state
    LaunchedEffect(Unit) {
        while (true) {
            isServiceActive = HabitInterruptionService.isServiceRunning
            delay(1500)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hello Mindful Friend 👋",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = "OneSec Balance",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = Color.White
                    )
                }
                
                // Quick global toggle
                Card(
                    modifier = Modifier.testTag("global_breaker_switch_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isEnabled) Color(0xFF6366F1).copy(alpha = 0.15f)
                        else Color(0xFFEF4444).copy(alpha = 0.15f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isEnabled) Color(0xFF6366F1).copy(alpha = 0.3f)
                        else Color(0xFFEF4444).copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnabled) "Active" else "Paused",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isEnabled) Color(0xFF818CF8) else Color(0xFFF87171),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { viewModel.toggleHabitBreaker(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF6366F1),
                                checkedTrackColor = Color(0xFF6366F1).copy(alpha = 0.3f),
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFF334155)
                            )
                        )
                    }
                }
            }
        }

        // Accessibility Warning Card
        if (!isServiceActive) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Alert",
                                tint = Color(0xFFF87171)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Action Required",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFF87171)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "To monitor app launches and introduce breathing pauses, please enable OneSec under Settings > Accessibility.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFCBD5E1)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open Accessibility Settings", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEF4444),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Activate OneSec Service", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.10f)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF34D399))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Real-time background monitoring active",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF34D399)
                        )
                    }
                }
            }
        }

        // Statistics Block
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Habit Summary",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Avoided Urges",
                        value = "${analytics?.avoidedTriggers ?: 0}",
                        subLabel = "Total pauses: ${analytics?.totalTriggers ?: 0}",
                        icon = Icons.Default.CheckCircle,
                        color = Color(0xFF81C784),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Time Restored",
                        value = "${analytics?.screenTimeSavedMinutes ?: 0}m",
                        subLabel = "approx 10m / rescue",
                        icon = Icons.Default.Refresh,
                        color = Color(0xFF64B5F6),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Mindful Streak",
                        value = "${analytics?.streakDays ?: 0} Days",
                        subLabel = "Consecutive avoids",
                        icon = Icons.Default.Star,
                        color = Color(0xFFFFB74D),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Active Mode",
                        value = activeMode,
                        subLabel = "Adjust count lengths",
                        icon = Icons.Default.Settings,
                        color = Color(0xFF4DB6AC),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Custom Visual Graph
        item {
            val db = AppDatabase.getDatabase(context)
            var logs by remember { mutableStateOf<List<TriggerLog>>(emptyList()) }
            LaunchedEffect(monitoredApps) {
                db.triggerLogDao().getAllLogsFlow().collect {
                    logs = it
                }
            }
            DistractionTrendChart(logs = logs)
        }

        // Settings / Resets
        item {
            OutlinedButton(
                onClick = { viewModel.clearHistory() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().testTag("reset_stats_button")
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset All Saved Stats")
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF94A3B8)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
fun DistractionTrendChart(logs: List<TriggerLog>, modifier: Modifier = Modifier) {
    val sdf = SimpleDateFormat("EEE", Locale.getDefault())
    val last7Days = (0..6).map { i ->
        val cal = Calendar.getInstance()
        cal.add(Calendar.DATE, -i)
        cal.time
    }.reversed()

    val dailyStats = last7Days.map { date ->
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
        val logsOnDay = logs.filter {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.timestamp)) == dateStr
        }
        val avoided = logsOnDay.count { it.actionTaken == "AVOIDED" }
        val continued = logsOnDay.count { it.actionTaken == "CONTINUED" }
        Pair(avoided, continued)
    }

    val maxVal = dailyStats.maxOfOrNull { it.first + it.second }?.coerceAtLeast(5) ?: 5

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Pause Success Rate (Last 7 Days)",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                .border(androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), RoundedCornerShape(24.dp))
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            dailyStats.forEachIndexed { index, (avoided, continued) ->
                val dayLabel = sdf.format(last7Days[index])
                val total = avoided + continued

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.82f)
                            .width(18.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // Background track
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        )

                        // Stacked bars representation
                        val avoidedHeightFrac = if (total > 0) avoided.toFloat() / maxVal else 0f
                        val continuedHeightFrac = if (total > 0) continued.toFloat() / maxVal else 0f

                        Column(
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            if (continued > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight(continuedHeightFrac)
                                        .width(18.dp)
                                        .background(Color.White.copy(alpha = 0.22f))
                                )
                            }
                            if (avoided > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight(avoidedHeightFrac / (avoidedHeightFrac + continuedHeightFrac).coerceAtLeast(1f))
                                        .width(18.dp)
                                        .background(Color(0xFF10B981), CircleShape)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Avoided (" + logs.count { it.actionTaken == "AVOIDED" } + ")", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Continued (" + logs.count { it.actionTaken == "CONTINUED" } + ")", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
            }
        }
    }
}

@Composable
fun AppsTab(viewModel: HabitViewModel) {
    val apps by viewModel.allMonitoredApps.collectAsStateWithLifecycle()
    
    var customPackageName by remember { mutableStateOf("") }
    var customAppName by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Monitored Apps",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select which distracting apps should trigger the OneSec mindful breathing pause.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8)
            )
        }

        // Custom App Insertion Widget
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Add Custom App",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customAppName,
                        onValueChange = { customAppName = it },
                        label = { Text("App Name (e.g., Free Fire)") },
                        modifier = Modifier.fillMaxWidth().testTag("custom_app_name_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            focusedLabelColor = Color(0xFF818CF8),
                            unfocusedLabelColor = Color(0xFF64748B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customPackageName,
                        onValueChange = { customPackageName = it },
                        label = { Text("Package Name (e.g., com.dts.freefireth)") },
                        modifier = Modifier.fillMaxWidth().testTag("custom_app_pkg_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (customAppName.isNotBlank() && customPackageName.isNotBlank()) {
                                viewModel.addCustomApp(customPackageName.trim(), customAppName.trim())
                                customAppName = ""
                                customPackageName = ""
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            focusedLabelColor = Color(0xFF818CF8),
                            unfocusedLabelColor = Color(0xFF64748B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (customAppName.isNotBlank() && customPackageName.isNotBlank()) {
                                viewModel.addCustomApp(customPackageName.trim(), customAppName.trim())
                                customAppName = ""
                                customPackageName = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_custom_app_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add to Monitored List", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text(
                text = "Active Monitors (${apps.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        if (apps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No apps monitored yet. Enable some from our pre-population database or insert custom package names above!",
                        textAlign = TextAlign.Center,
                        color = Color(0xFF64748B)
                    )
                }
            }
        } else {
            items(apps) { app ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (app.isEnabled) Color(0xFF6366F1).copy(alpha = 0.08f)
                        else Color.White.copy(alpha = 0.04f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (app.isEnabled) Color(0xFF6366F1).copy(alpha = 0.22f)
                        else Color.White.copy(alpha = 0.08f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (app.isEnabled) Color.White
                                    else Color.White.copy(alpha = 0.40f)
                                )
                                if (app.isCustom) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Custom",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        color = Color(0xFF818CF8),
                                        modifier = Modifier
                                            .background(Color(0xFF6366F1).copy(alpha = 0.20f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = app.isEnabled,
                                onCheckedChange = { viewModel.toggleAppMonitoring(app.packageName, it) },
                                modifier = Modifier.testTag("toggle_switch_${app.packageName}"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF6366F1),
                                    checkedTrackColor = Color(0xFF6366F1).copy(alpha = 0.3f),
                                    uncheckedThumbColor = Color(0xFF94A3B8),
                                    uncheckedTrackColor = Color(0xFF334155)
                                )
                            )
                            if (app.isCustom) {
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { viewModel.removeCustomApp(app.packageName) },
                                    modifier = Modifier.testTag("delete_custom_${app.packageName}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = Color(0xFFEF4444)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FocusModesTab(viewModel: HabitViewModel, activeMode: String) {
    val modes = listOf(
        Triple("Standard", "Standard breathing pause before loading addictive apps. Excellent for baseline mindful control.", "4s Breathe"),
        Triple("Study Mode", "Increases countdown to 5 seconds. Perfect for students wanting to clear brain bandwidth for learning.", "5s Breathe"),
        Triple("Deep Work Mode", "Demands a forced 6-second deep breath pause. Interrupts heavy automatic escapism triggers.", "6s Breathe"),
        Triple("Sleep Mode", "A soothing 6-second deep-breath cycle. Ideal during nighttime hours to prompt screen-free relaxation.", "6s Breathe"),
        Triple("Dopamine Detox Mode", "Requires an intensive 8-second mindful breathing barrier. Drastically breaks toxic instant-gratification loops.", "8s Breathe")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Focus Modes",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Switch focus modes based on your workflow to modify the breathing countdown duration and mindful difficulty.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8)
            )
        }

        items(modes) { (name, desc, duration) ->
            val isCurrent = activeMode == name
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setFocusMode(name) }
                    .testTag("mode_card_$name"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent) Color(0xFF6366F1).copy(alpha = 0.12f)
                    else Color.White.copy(alpha = 0.04f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isCurrent) Color(0xFF6366F1).copy(alpha = 0.5f)
                    else Color.White.copy(alpha = 0.08f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isCurrent,
                        onClick = { viewModel.setFocusMode(name) },
                        modifier = Modifier.testTag("mode_radio_$name"),
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color(0xFF6366F1),
                            unselectedColor = Color(0xFF64748B)
                        )
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = duration,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF818CF8)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AICoachTab(viewModel: HabitViewModel) {
    val feedback by viewModel.aiCoachFeedback.collectAsStateWithLifecycle()
    val loading by viewModel.aiCoachLoading.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                Column {
                    Text(
                        text = "Zen Mind space 🧘",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF818CF8)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Digital Wellbeing Coach",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = Color.White
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Coach Logo",
                        tint = Color(0xFF6366F1),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Personalized AI Wellness Advice",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "I will scan your statistics (saved streaks, success bypass vs avoid logs) and output a tailored mindful advice session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.loadAICoachFeedback() },
                        enabled = !loading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF6366F1).copy(alpha = 0.40f),
                            disabledContentColor = Color.White.copy(alpha = 0.50f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("call_coach_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Favorite, contentDescription = "Coach API")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ask Coach to Analyze Trends", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (feedback != null || loading) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF6366F1).copy(alpha = 0.10f)),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.30f)),
                    modifier = Modifier.fillMaxWidth().testTag("coach_feedback_card")
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Star, contentDescription = "AI Feedback", tint = Color(0xFF818CF8))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "COACH'S DAILY ADVICE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF818CF8),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        if (loading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFF6366F1))
                            }
                        } else {
                            Text(
                                text = feedback ?: "",
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }
        }
    }
}

object RowDefaults {
    @Composable
    fun border(color: Color): androidx.compose.foundation.BorderStroke {
        return androidx.compose.foundation.BorderStroke(2.dp, color)
    }
}
