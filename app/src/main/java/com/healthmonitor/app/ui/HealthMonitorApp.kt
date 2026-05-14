package com.healthmonitor.app.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.healthmonitor.app.ui.screens.*
import com.healthmonitor.app.ui.theme.HealthMonitorTheme
import com.healthmonitor.app.ui.viewmodel.CaseViewModel
import com.healthmonitor.app.ui.viewmodel.PatientViewModel
import com.healthmonitor.app.util.ActiveCaseManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.healthmonitor.app.ui.design.*

@Composable
fun HealthMonitorApp(context: Context) {
    createNotificationChannel(context)

    HealthMonitorTheme {
        val patientViewModel: PatientViewModel = hiltViewModel()
        val caseViewModel: CaseViewModel = hiltViewModel()

        val patients by patientViewModel.getAllPatients().collectAsState(initial = null)
        val activePatientId by patientViewModel.activePatientIdFlow.collectAsState()

        androidx.compose.animation.AnimatedContent(
            targetState = patients,
            transitionSpec = {
                androidx.compose.animation.fadeIn(
                    tween(300)
                ) togetherWith androidx.compose.animation.fadeOut(
                    tween(200)
                )
            },
            label = "app_root"
        ) { patientList ->
            when {
                // Still loading from DB
                patientList == null -> AppLoadingScreen()
                // First launch: no patients yet
                patientList.isEmpty() -> PatientRegistrationScreen(
                    onPatientRegistered = { name, age, gender ->
                        patientViewModel.addPatient(name, age, gender)
                    }
                )
                // Normal app flow
                else -> MainAppScaffold(patientViewModel, caseViewModel)
            }
        }
    }
}

@Composable
private fun MainAppScaffold(
    patientViewModel: PatientViewModel,
    caseViewModel: CaseViewModel
) {
    val navController = rememberNavController()
    val ctx = LocalContext.current
    Scaffold(

        topBar = { TopAppBarWithSelectors(patientViewModel, caseViewModel, ctx) },
        bottomBar = { BottomNavigationBar(navController) },
        containerColor = Color(0xFF0F0F0F)
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("dashboard")            { DashboardScreen(navController) }
            composable("medications")           { MedicationsScreen(navController) }
            composable("health")                { HealthScreen(navController) }
            composable("blood_pressure")        { HealthScreen(navController) }
            composable("symptoms")              { HealthScreen(navController) }
            composable("ai_tools")              { AiToolsScreen(navController) }
            composable("settings")              { SettingsScreen(navController) }
            composable("cases")                 { CasesScreen(navController) }
            composable("patients")              { PatientsScreen(navController) }
            composable("medication_reminder")   { MedicationReminderScreen(navController) }
            composable("medication_history")    { MedicationHistoryScreen(navController) }
            composable("patient_profile/{id}")  { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id") ?: return@composable
                PatientProfileScreen(patientId = id, navController = navController)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading screen — shown while Room DB resolves the patient list on cold start
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppLoadingScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier          = Modifier
            .fillMaxSize()
            .background(HMColor.BgBase),
        contentAlignment  = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HMSpacing.lg)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(HMRadius.xl))
                    .background(HMColor.GreenBright.copy(alpha = 0.12f))
                    .border(1.dp, HMColor.GreenBorder, RoundedCornerShape(HMRadius.xl)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.HealthAndSafety,
                    contentDescription = null,
                    tint     = HMColor.GreenBright.copy(alpha = alpha),
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                "Health Monitor",
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                color      = HMColor.TextPrimary.copy(alpha = alpha)
            )
            CircularProgressIndicator(
                color       = HMColor.GreenBright.copy(alpha = alpha),
                strokeWidth = 2.dp,
                modifier    = Modifier.size(24.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Patient registration (first-launch)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PatientRegistrationScreen(
    onPatientRegistered: (String, Int, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("ذكر") }

    val genderOptions = listOf("ذكر", "أنثى", "غير محدد")
    val isValid = name.isNotBlank()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F0F0F)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.PersonAdd,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "مرحباً بك",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = Color(0xFFE8E8E8),
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "سجّل أول مريض للبدء في استخدام التطبيق",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF888888))
            )
            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("الاسم *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = registrationFieldColors()
                    )
                    OutlinedTextField(
                        value = age,
                        onValueChange = { age = it.filter(Char::isDigit) },
                        label = { Text("العمر") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = registrationFieldColors()
                    )
                    Text("الجنس", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        genderOptions.forEach { option ->
                            FilterChip(
                                selected = gender == option,
                                onClick = { gender = option },
                                label = { Text(option) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF4CAF50),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onPatientRegistered(name.trim(), age.toIntOrNull() ?: 0, gender) },
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),
                    disabledContainerColor = Color(0xFF2A2A2A)
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ابدأ الاستخدام", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun registrationFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color(0xFFE8E8E8),
    unfocusedTextColor = Color(0xFFAAAAAA),
    focusedBorderColor = Color(0xFF4CAF50),
    unfocusedBorderColor = Color(0xFF2A2A2A),
    focusedLabelColor = Color(0xFF4CAF50),
    unfocusedLabelColor = Color(0xFF666666),
    cursorColor = Color(0xFF4CAF50)
)

// ── Top bar ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBarWithSelectors(
    patientViewModel: PatientViewModel,
    caseViewModel: CaseViewModel,
    context: Context
) {
    val activePatientId by patientViewModel.activePatientIdFlow.collectAsState()
    val activeCaseId by ActiveCaseManager.activeCaseIdFlow.collectAsState()
    val patients by patientViewModel.getAllPatients().collectAsState(initial = emptyList())

    val activePatientName = remember(patients, activePatientId) {
        patients.firstOrNull { it.id == activePatientId }?.name ?: "اختر مريض"
    }

    val cases: List<com.healthmonitor.app.data.local.entities.CaseEntity> =
        activePatientId?.let { pid ->
            caseViewModel.getCasesForPatient(pid).collectAsState(initial = emptyList()).value
        } ?: emptyList()

    val activeCaseTitle = remember(cases, activeCaseId) {
        cases.firstOrNull { it.id == activeCaseId }?.title ?: "اختر حالة"
    }

    TopAppBar(
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var patientExpanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { patientExpanded = true }) {
                        Text(activePatientName, color = Color.White)
                    }
                    DropdownMenu(expanded = patientExpanded, onDismissRequest = { patientExpanded = false }) {
                        patients.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name) },
                                onClick = { patientViewModel.setActivePatientId(p.id); patientExpanded = false },
                                trailingIcon = if (p.id == activePatientId) ({
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }) else null
                            )
                        }
                    }
                }

                // AFTER
                if (cases.isNotEmpty()) {
                    var caseExpanded by remember { mutableStateOf(false) }
                    val caseSelected = activeCaseId != null && cases.any { it.id == activeCaseId }
                    Box {
                        TextButton(onClick = { caseExpanded = true }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Red dot when no case selected, green dot when selected
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (caseSelected) Color(0xFF4CAF50) else Color(0xFFEF5350)
                                        )
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (caseSelected) activeCaseTitle else "اختر حالة",
                                    color = if (caseSelected) Color(0xFFBBBBBB) else Color(0xFFEF5350)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = caseExpanded,
                            onDismissRequest = { caseExpanded = false }
                        ) {
                            cases.filter { !it.isClosed }.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.title) },
                                    onClick = { caseViewModel.setActiveCase(c.id); caseExpanded = false },
                                    trailingIcon = if (c.id == activeCaseId) ({
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            null,
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }) else null
                                )
                            }
                            // Option to deselect case
                            if (caseSelected) {
                                DropdownMenuItem(
                                    text = { Text("إلغاء تحديد الحالة", color = Color(0xFFEF5350)) },
                                    onClick = {
                                        ActiveCaseManager.clearActiveCase(context)
                                        caseExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
    )
}

// ── Bottom nav ────────────────────────────────────────────────────────────────

private data class NavItem(
    val route: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val label: String,
    val accentColor: Color
)

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "dashboard"

    val items = listOf(
        NavItem("dashboard",   Icons.Outlined.Home,           Icons.Filled.Home,          "الرئيسية", HMColor.GreenBright),
        NavItem("medications", Icons.Outlined.LocalPharmacy,  Icons.Filled.LocalPharmacy, "الأدوية",  HMColor.BlueBright),
        NavItem("cases",       Icons.Outlined.FolderOpen,     Icons.Filled.Folder,        "الحالات",  HMColor.CyanBright),
        NavItem("health",      Icons.Outlined.Favorite,       Icons.Filled.Favorite,      "الصحة",    HMColor.RedBright),
        NavItem("ai_tools",    Icons.Outlined.AutoAwesome,    Icons.Filled.AutoAwesome,   "AI",       HMColor.AmberBright),
        NavItem("settings",    Icons.Outlined.Settings,       Icons.Filled.Settings,      "الإعدادات", HMColor.TextSecondary)
    )

    // resolve legacy routes to their canonical equivalent
    val resolvedRoute = when (currentRoute) {
        "blood_pressure", "symptoms" -> "health"
        else -> currentRoute
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation       = 12.dp,
                    shape           = RoundedCornerShape(HMRadius.xl),
                    ambientColor    = Color.Black.copy(alpha = 0.4f),
                    spotColor       = Color.Black.copy(alpha = 0.4f)
                )
                .clip(RoundedCornerShape(HMRadius.xl))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(HMColor.BgElevated, HMColor.BgSurface)
                    )
                )
                .border(1.dp, HMColor.BorderDefault, RoundedCornerShape(HMRadius.xl))
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { item ->
                val isSelected = resolvedRoute == item.route
                FloatingNavItem(
                    item       = item,
                    isSelected = isSelected,
                    onClick    = {
                        if (resolvedRoute != item.route) {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FloatingNavItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = if (isSelected) item.accentColor else HMColor.TextDisabled
    HMPressable(onClick = onClick) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .widthIn(min = 44.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (isSelected) 38.dp else 32.dp)
                    .clip(RoundedCornerShape(if (isSelected) HMRadius.sm else HMRadius.xs))
                    .background(
                        if (isSelected) item.accentColor.copy(alpha = 0.15f)
                        else Color.Transparent
                    )
                    .then(
                        if (isSelected) Modifier.border(
                            1.dp,
                            item.accentColor.copy(alpha = 0.3f),
                            RoundedCornerShape(HMRadius.sm)
                        ) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) item.selectedIcon else item.icon,
                    contentDescription = item.label,
                    tint = color,
                    modifier = Modifier.size(if (isSelected) 18.dp else 17.dp)
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text      = item.label,
                fontSize  = if (isSelected) 10.sp else 9.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color     = color
            )
        }
    }
}

// ── Notification channel ──────────────────────────────────────────────────────

private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Channel ID must match MedicationAlarmReceiver.CHANNEL_ID exactly.
        // "medication_reminders_v2" forces recreation at IMPORTANCE_HIGH on existing installs.
        val channel = NotificationChannel(
            "medication_reminders_v2",
            "تنبيهات الأدوية",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description         = "تنبيهات بمواعيد الأدوية"
            enableVibration(true)
            enableLights(true)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }
}