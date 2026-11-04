package com.aegissoft.qcybutmod.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aegissoft.qcybutmod.core.bluetooth.BleConnectionManager
import com.aegissoft.qcybutmod.core.service.QcyBackgroundService
import com.aegissoft.qcybutmod.ui.screens.*
import com.aegissoft.qcybutmod.ui.theme.AppThemeColor
import com.aegissoft.qcybutmod.ui.theme.OledBlack
import com.aegissoft.qcybutmod.ui.theme.QcyTheme

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BleConnectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        bleManager = BleConnectionManager.getInstance(applicationContext)

        // Load saved theme preference
        val prefs = getSharedPreferences("qcy_settings", Context.MODE_PRIVATE)
        val savedThemeId = prefs.getString("selected_theme", AppThemeColor.CRIMSON.id) ?: AppThemeColor.CRIMSON.id

        setContent {
            var currentTheme by remember { mutableStateOf(AppThemeColor.fromId(savedThemeId)) }

            // BT-required izinler: bunlar olmadan bağlantı kurulamaz
            val btPermissions = remember {
                val list = mutableListOf<String>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    list.add(Manifest.permission.BLUETOOTH_SCAN)
                    list.add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                list.add(Manifest.permission.ACCESS_FINE_LOCATION)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                list.toTypedArray()
            }
            // Opsiyonel izinler: BT özelliklerini KİLİTLEMEZ (fit test / bildirim)
            val optionalPermissions = remember {
                val list = mutableListOf<String>()
                list.add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    list.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                list.toTypedArray()
            }

            fun hasBtPermissions(): Boolean = btPermissions.all {
                ContextCompat.checkSelfPermission(this@MainActivity, it) == PackageManager.PERMISSION_GRANTED
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { perms ->
                // Yalnız BT izinlerine bakar; RECORD_AUDIO/POST_NOTIFICATIONS reddini tolere eder
                val btOk = btPermissions.all { perms[it] == true } || hasBtPermissions()
                if (btOk) {
                    QcyBackgroundService.startService(applicationContext)
                    bleManager.startScanning(autoConnect = true)
                }
            }

            LaunchedEffect(Unit) {
                val all = btPermissions + optionalPermissions
                val needRequest = all.any {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                }
                if (needRequest) {
                    permissionLauncher.launch(all)
                } else {
                    QcyBackgroundService.startService(applicationContext)
                    bleManager.startScanning(autoConnect = true)
                }
            }

            QcyTheme(selectedTheme = currentTheme) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    color = OledBlack
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        enterTransition = { fadeIn() + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
                        exitTransition = { fadeOut() + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
                        popEnterTransition = { fadeIn() + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) },
                        popExitTransition = { fadeOut() + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) }
                    ) {
                        composable("home") {
                            HomeScreen(
                                bleManager = bleManager,
                                onNavigate = { route -> navController.navigate(route) }
                            )
                        }
                        composable("equalizer") {
                            EqualizerScreen(
                                bleManager = bleManager,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("gestures") {
                            GestureScreen(
                                bleManager = bleManager,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("fittest") {
                            FitTestScreen(
                                bleManager = bleManager,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("find") {
                            FindDeviceScreen(
                                bleManager = bleManager,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("multipoint") {
                            MultipointScreen(
                                bleManager = bleManager,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("translate") {
                            LiveTranslateScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                bleManager = bleManager,
                                currentTheme = currentTheme,
                                onThemeSelected = { newTheme ->
                                    currentTheme = newTheme
                                    prefs.edit().putString("selected_theme", newTheme.id).apply()
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
