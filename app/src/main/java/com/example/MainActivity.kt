package com.example

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.*
import com.example.ui.translation.*
import com.example.ui.viewmodel.ShieldViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ShieldViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MyApplicationTheme {
                ShieldApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkSecurityPerms(this)
    }
}

data class NavigationItem(val title: String, val icon: ImageVector, val tag: String)
data class PresetItem(val label: String, val h: Int, val m: Int, val s: Int)
data class DayPresetItem(val label: String, val days: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldApp(viewModel: ShieldViewModel) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val strings by viewModel.activeStrings.collectAsState()
    val settings by viewModel.settingsFlow.collectAsState()
    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsState()
    val isDeviceAdminEnabled by viewModel.isDeviceAdminEnabled.collectAsState()
    val remainingMs by viewModel.timerRemainingMs.collectAsState()
    val adminRemainingMs by viewModel.adminLockRemainingMs.collectAsState()
    val currentLang by viewModel.currentLanguage.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var userKeywordInput by remember { mutableStateOf("") }

    // Content Block duration states
    var selectedHours by remember { mutableStateOf(0) }
    var selectedMinutes by remember { mutableStateOf(10) }
    var selectedSeconds by remember { mutableStateOf(0) }

    // Admin Lock days count
    var selectedDays by remember { mutableStateOf(1) }

    // Sync keywords state
    LaunchedEffect(settings) {
        settings?.let {
            userKeywordInput = it.customKeywords
        }
    }

    // Direct loop verification of permissions on resumes
    LaunchedEffect(Unit) {
        viewModel.checkSecurityPerms(context)
    }

    CompositionLocalProvider(LocalLayoutDirection provides strings.textDirection) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ElegantDarkBg,
            topBar = {
                LargeTopAppBar(
                    title = {
                        Column {
                            Text(
                                text = strings.title,
                                fontWeight = FontWeight.Black,
                                fontSize = 32.sp,
                                style = androidx.compose.ui.text.TextStyle(
                                    brush = Brush.linearGradient(
                                        colors = listOf(ElegantAccentPurple, Color(0xFF00FF7F))
                                    )
                                ),
                                modifier = Modifier.testTag("app_title")
                            )
                            Text(
                                text = strings.subtitle,
                                fontSize = 12.sp,
                                color = ElegantTextMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        // Translation Language Switch button
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ElegantCardBg)
                                .border(1.dp, ElegantBorder, RoundedCornerShape(12.dp))
                                .size(48.dp)
                                .clickable { viewModel.toggleLanguage() }
                                .testTag("language_toggle_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (currentLang == "ar") "EN" else "عربي",
                                color = ElegantAccentPurple,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = ElegantDarkBg,
                        titleContentColor = ElegantTextLight
                    )
                )
            },
            bottomBar = {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth()
                        .height(72.dp)
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = ElegantCardBg.copy(alpha = 0.95f)),
                    border = BorderStroke(1.dp, ElegantBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val items = listOf(
                            NavigationItem(strings.tabGuard, Icons.Default.Lock, "tab_guard_button"),
                            NavigationItem(strings.tabUninstall, Icons.Default.Settings, "tab_uninstall_button")
                        )

                        items.forEachIndexed { index, item ->
                            val isSelected = selectedTab == index
                            val iconColor = if (isSelected) ElegantAccentPurple else ElegantTextMuted
                            val labelColor = if (isSelected) ElegantAccentPurple else ElegantTextMuted
                            val itemScale by animateFloatAsState(if (isSelected) 1.05f else 0.95f, label = "tab_scale")

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { selectedTab = index }
                                    .scale(itemScale),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = iconColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.title,
                                    color = labelColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut()
                            )
                        }.using(
                            SizeTransform(clip = false)
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "tab_content_shift"
                ) { tabIndex ->
                    when (tabIndex) {
                        0 -> GuardSectionScreen(
                            viewModel = viewModel,
                            strings = strings,
                            isAccessibilityEnabled = isAccessibilityEnabled,
                            remainingMs = remainingMs,
                            isShieldActive = settings?.isShieldActive == true && remainingMs > 0,
                            selectedHours = selectedHours,
                            selectedMinutes = selectedMinutes,
                            selectedSeconds = selectedSeconds,
                            onHoursChange = { selectedHours = it },
                            onMinutesChange = { selectedMinutes = it },
                            onSecondsChange = { selectedSeconds = it },
                            userKeywordInput = userKeywordInput,
                            onKeywordInputChange = {
                                userKeywordInput = it
                                viewModel.updateCustomKeywords(it)
                            },
                            focusManager = focusManager
                        )
                        1 -> UninstallSectionScreen(
                            viewModel = viewModel,
                            strings = strings,
                            isDeviceAdminEnabled = isDeviceAdminEnabled,
                            adminRemainingMs = adminRemainingMs,
                            isAdminLockActive = settings?.isAdminLockActive == true && adminRemainingMs > 0,
                            selectedDays = selectedDays,
                            onDaysChange = { selectedDays = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GuardSectionScreen(
    viewModel: ShieldViewModel,
    strings: AppStrings,
    isAccessibilityEnabled: Boolean,
    remainingMs: Long,
    isShieldActive: Boolean,
    selectedHours: Int,
    selectedMinutes: Int,
    selectedSeconds: Int,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit,
    onSecondsChange: (Int) -> Unit,
    userKeywordInput: String,
    onKeywordInputChange: (String) -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
    ) {
        // PANEL 1: Content Shield Main Controller
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (isShieldActive) ElegantAccentPurple else ElegantBorder,
                        shape = RoundedCornerShape(32.dp)
                    )
                    .background(if (isShieldActive) ElegantAccentPurple else ElegantCardBg, RoundedCornerShape(32.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(
                            if (isShieldActive) ElegantDarkPurple.copy(alpha = 0.12f) else ElegantDarkBg,
                            shape = CircleShape
                        )
                        .border(1.dp, if (isShieldActive) ElegantDarkPurple.copy(alpha = 0.4f) else ElegantBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Shield secured status icon",
                        tint = if (isShieldActive) ElegantDarkPurple else ShieldCrimson,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isShieldActive) strings.statusActive else strings.statusInactive,
                    color = if (isShieldActive) ElegantDarkPurple else ElegantTextLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isShieldActive) {
                    val hrs = (remainingMs / 3600000).toInt()
                    val mns = ((remainingMs % 3600000) / 60000).toInt()
                    val scs = ((remainingMs % 60000) / 1000).toInt()
                    val timerText = String.format(java.util.Locale.US, "%02d:%02d:%02d", hrs, mns, scs)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .border(2.dp, ElegantDarkPurple.copy(alpha = 0.4f), CircleShape)
                                .background(ElegantDarkPurple.copy(alpha = 0.08f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = timerText,
                                    fontSize = 28.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    color = ElegantDarkPurple
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "SECURED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElegantDarkPurple.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = strings.durationSelector,
                            color = ElegantTextLight,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ElegantDarkBg.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .padding(vertical = 12.dp, horizontal = 4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(strings.hoursLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElegantTextMuted)
                                Spacer(modifier = Modifier.height(4.dp))
                                WheelPicker(
                                    count = 24,
                                    isShieldActive = false,
                                    selectedIndex = selectedHours,
                                    onIndexSelected = onHoursChange
                                )
                            }

                            Text(":", fontSize = 28.sp, fontWeight = FontWeight.Black, color = ElegantAccentPurple, modifier = Modifier.padding(top = 16.dp))

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(strings.minutesLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElegantTextMuted)
                                Spacer(modifier = Modifier.height(4.dp))
                                WheelPicker(
                                    count = 60,
                                    isShieldActive = false,
                                    selectedIndex = selectedMinutes,
                                    onIndexSelected = onMinutesChange
                                )
                            }

                            Text(":", fontSize = 28.sp, fontWeight = FontWeight.Black, color = ElegantAccentPurple, modifier = Modifier.padding(top = 16.dp))

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(strings.secondsLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElegantTextMuted)
                                Spacer(modifier = Modifier.height(4.dp))
                                WheelPicker(
                                    count = 60,
                                    isShieldActive = false,
                                    selectedIndex = selectedSeconds,
                                    onIndexSelected = onSecondsChange
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = strings.quickPresetsLabel,
                            color = ElegantTextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val presets = listOf(
                                PresetItem("5s", 0, 0, 5),
                                PresetItem("1m", 0, 1, 0),
                                PresetItem("10m", 0, 10, 0),
                                PresetItem("30m", 0, 30, 0),
                                PresetItem("1h", 1, 0, 0)
                            )
                            presets.forEach { preset ->
                                val isCurrent = selectedHours == preset.h && selectedMinutes == preset.m && selectedSeconds == preset.s
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isCurrent) ElegantAccentPurple else ElegantBorder)
                                        .clickable {
                                            onHoursChange(preset.h)
                                            onMinutesChange(preset.m)
                                            onSecondsChange(preset.s)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = preset.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) ElegantDarkPurple else ElegantTextLight
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (isShieldActive) {
                            viewModel.deactivateShield()
                        } else {
                            val totalMs = (selectedHours * 3600L + selectedMinutes * 60L + selectedSeconds) * 1000L
                            viewModel.activateShield(maxOf(1000L, totalMs))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("primary_action_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isShieldActive) ElegantDarkBg else ElegantButtonPurple,
                        contentColor = if (isShieldActive) ElegantTextLight else ElegantButtonText
                    ),
                    shape = RoundedCornerShape(28.dp),
                    border = if (isShieldActive) BorderStroke(1.dp, ElegantBorder) else null
                ) {
                    Text(
                        text = if (isShieldActive) strings.btnDeactivate else strings.btnActivate,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // PANEL 2: Keywords configuration list
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ElegantCardBg, RoundedCornerShape(24.dp))
                    .border(1.dp, ElegantBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Text(
                    text = strings.customKeywordsTitle,
                    color = ElegantTextLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = strings.customKeywordsDesc,
                    color = ElegantTextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                OutlinedTextField(
                    value = userKeywordInput,
                    onValueChange = onKeywordInputChange,
                    placeholder = { Text("secret_word, badsite, etc.", color = ElegantTextMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_keywords_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElegantAccentPurple,
                        unfocusedBorderColor = ElegantBorder,
                        focusedTextColor = ElegantTextLight,
                        unfocusedTextColor = ElegantTextLight,
                        cursorColor = ElegantAccentPurple
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // PANEL 3: System Access Service Status
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ElegantCardBg, RoundedCornerShape(24.dp))
                    .border(1.dp, ElegantBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isAccessibilityEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Accessibility connection status icon",
                        tint = if (isAccessibilityEnabled) ElegantSuccessGreen else ShieldCrimson,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isAccessibilityEnabled) strings.statusGranted else strings.statusNotGranted,
                        fontWeight = FontWeight.Bold,
                        color = if (isAccessibilityEnabled) ElegantSuccessGreen else ShieldCrimson,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = strings.permissionDesc,
                    color = ElegantTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("link_service_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElegantBorder,
                        contentColor = ElegantTextLight
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ShieldCrimson.copy(alpha = 0.5f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Launch permissions accessibility page",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.btnGrantPermission,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UninstallSectionScreen(
    viewModel: ShieldViewModel,
    strings: AppStrings,
    isDeviceAdminEnabled: Boolean,
    adminRemainingMs: Long,
    isAdminLockActive: Boolean,
    selectedDays: Int,
    onDaysChange: (Int) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
    ) {
        // CARD 1: Device Administrator Settings Controller
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ElegantCardBg, RoundedCornerShape(24.dp))
                    .border(1.dp, ElegantBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Text(
                    text = strings.deviceAdminTitle,
                    color = ElegantAccentPurple,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = strings.adminPermissionDesc,
                    color = ElegantTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ElegantDarkBg.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = if (isDeviceAdminEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Device administrator active status icon",
                        tint = if (isDeviceAdminEnabled) ElegantSuccessGreen else ShieldCrimson,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isDeviceAdminEnabled) strings.statusAdminGranted else strings.statusAdminNotGranted,
                        fontWeight = FontWeight.Bold,
                        color = if (isDeviceAdminEnabled) ElegantSuccessGreen else ShieldCrimson,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        try {
                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, ShieldDeviceAdminReceiver::class.java))
                                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, strings.adminPermissionDesc)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("grant_admin_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElegantBorder,
                        contentColor = ElegantTextLight
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ElegantAccentPurple.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = strings.btnGrantAdmin,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // CARD 2: Secure Uninstall Protections Timer Locking Panel
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (isAdminLockActive) ElegantAccentPurple else ElegantBorder,
                        shape = RoundedCornerShape(32.dp)
                    )
                    .background(if (isAdminLockActive) ElegantAccentPurple else ElegantCardBg, RoundedCornerShape(32.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Glow Shield Safety Lock status icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            if (isAdminLockActive) ElegantDarkPurple.copy(alpha = 0.12f) else ElegantDarkBg,
                            shape = CircleShape
                        )
                        .border(1.dp, if (isAdminLockActive) ElegantDarkPurple.copy(alpha = 0.4f) else ElegantBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Uninstall state padlock icon",
                        tint = if (isAdminLockActive) ElegantDarkPurple else ShieldCrimson,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isAdminLockActive) strings.statusAdminLockActive else strings.statusAdminLockInactive,
                    color = if (isAdminLockActive) ElegantDarkPurple else ElegantTextLight,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isAdminLockActive) {
                    // Remaining duration in details: days, hours, minutes, seconds
                    val totalSecs = adminRemainingMs / 1000
                    val days = totalSecs / 86400
                    val hours = (totalSecs % 86400) / 3600
                    val minutes = (totalSecs % 3600) / 60
                    val seconds = totalSecs % 60

                    val countdownFormatted = String.format(
                        java.util.Locale.US,
                        "%02d %s %02d:%02d:%02d",
                        days,
                        strings.daysLabel,
                        hours,
                        minutes,
                        seconds
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = strings.adminLockCountdownLabel,
                            color = ElegantDarkPurple.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ElegantDarkPurple.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                .border(1.dp, ElegantDarkPurple.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = countdownFormatted,
                                fontSize = 20.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                color = ElegantDarkPurple
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = strings.adminLockDesc,
                            color = ElegantTextMuted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = strings.daysOnlyLabel + " (" + selectedDays + " " + strings.daysLabel + ")",
                            color = ElegantTextLight,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Daily range picker (days only minimum 1, max 30)
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ElegantDarkBg.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .padding(vertical = 12.dp)
                        ) {
                            WheelPicker(
                                count = 30,
                                isShieldActive = false,
                                selectedIndex = selectedDays - 1,
                                onIndexSelected = { onDaysChange(it + 1) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.daysLabel,
                                color = ElegantAccentPurple,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quick presets row for DAYS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val dayPresets = listOf(
                                DayPresetItem("1${strings.daysLabel}", 1),
                                DayPresetItem("3${strings.daysLabel}", 3),
                                DayPresetItem("7${strings.daysLabel}", 7),
                                DayPresetItem("15${strings.daysLabel}", 15),
                                DayPresetItem("30${strings.daysLabel}", 30)
                            )
                            dayPresets.forEach { preset ->
                                val isCurrent = selectedDays == preset.days
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isCurrent) ElegantAccentPurple else ElegantBorder)
                                        .clickable {
                                            onDaysChange(preset.days)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = preset.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) ElegantDarkPurple else ElegantTextLight
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Activate countdown time lock (No stops allowed!)
                        Button(
                            onClick = {
                                viewModel.activateAdminLock(selectedDays)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("activate_admin_lock_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElegantButtonPurple,
                                contentColor = ElegantButtonText
                            ),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text(
                                text = strings.btnActivateAdminLock,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // HELP PANEL
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ElegantCardBg, RoundedCornerShape(24.dp))
                    .border(1.dp, ElegantBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Text(
                    text = strings.helpSection,
                    color = ElegantTextLight,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strings.helpContent,
                    color = ElegantTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Justify
                )
            }
        }
    }
}

@Composable
fun WheelPicker(
    count: Int,
    isShieldActive: Boolean,
    selectedIndex: Int,
    onIndexSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current
    val itemHeightDp = 44.dp
    val itemHeightPx = with(density) { itemHeightDp.roundToPx() }
    val containerHeight = itemHeightDp * 3
    val verticalPadding = itemHeightDp

    LaunchedEffect(selectedIndex) {
        if (!lazyListState.isScrollInProgress && selectedIndex in 0 until count) {
            val centerItem = lazyListState.firstVisibleItemIndex + (lazyListState.firstVisibleItemScrollOffset + itemHeightPx / 2) / maxOf(1, itemHeightPx)
            if (centerItem != selectedIndex) {
                lazyListState.scrollToItem(selectedIndex)
            }
        }
    }

    val centerIndex by remember {
        derivedStateOf {
            val firstVisible = lazyListState.firstVisibleItemIndex
            val offset = lazyListState.firstVisibleItemScrollOffset
            if (itemHeightPx <= 0) 0
            else {
                val idx = firstVisible + (offset + itemHeightPx / 2) / itemHeightPx
                idx.coerceIn(0, count - 1)
            }
        }
    }

    LaunchedEffect(centerIndex) {
        if (lazyListState.isScrollInProgress && centerIndex in 0 until count) {
            onIndexSelected(centerIndex)
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress && centerIndex in 0 until count) {
            val offset = lazyListState.firstVisibleItemScrollOffset
            if (offset != 0) {
                lazyListState.animateScrollToItem(centerIndex)
                onIndexSelected(centerIndex)
            }
        }
    }

    Box(
        modifier = modifier
            .width(74.dp)
            .height(containerHeight),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeightDp)
                .background(ElegantAccentPurple.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .border(1.dp, ElegantAccentPurple.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
        )

        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(vertical = verticalPadding),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            userScrollEnabled = !isShieldActive
        ) {
            items(count) { index ->
                val isSelected = index == centerIndex
                val fontScale by animateFloatAsState(if (isSelected) 1.25f else 0.85f, label = "layout_scale")
                val itemAlpha by animateFloatAsState(if (isSelected) 1f else 0.35f, label = "layout_alpha")
                val textColor = if (isSelected) ElegantAccentPurple else ElegantTextLight

                Box(
                    modifier = Modifier
                        .height(itemHeightDp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = String.format(java.util.Locale.US, "%02d", index),
                        color = textColor.copy(alpha = itemAlpha),
                        fontSize = 20.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.scale(fontScale)
                    )
                }
            }
        }
    }
}
