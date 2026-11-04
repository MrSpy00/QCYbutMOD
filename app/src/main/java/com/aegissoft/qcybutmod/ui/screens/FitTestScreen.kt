package com.aegissoft.qcybutmod.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aegissoft.qcybutmod.core.audio.FitTestError
import com.aegissoft.qcybutmod.core.audio.FitTestManager
import com.aegissoft.qcybutmod.core.audio.FitTestState
import com.aegissoft.qcybutmod.core.bluetooth.BleConnectionManager
import com.aegissoft.qcybutmod.ui.components.AegisCard
import com.aegissoft.qcybutmod.ui.theme.*

@Composable
fun FitTestScreen(
    bleManager: BleConnectionManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalAppThemeColor.current
    val fitTestManager = remember { FitTestManager(context, bleManager) }
    val testState by fitTestManager.testState.collectAsState()

    var micDenied by remember { mutableStateOf(false) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) {
            micDenied = false
            fitTestManager.startTest()
        } else {
            micDenied = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            fitTestManager.cancelTest()
        }
    }

    // Pulse animation for testing phase
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant)
                    .size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Geri",
                    tint = theme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Kulak Uyum & Akustik Testi",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Silikon uçların kulağa oturma kontrolü",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Center Visual Indicator
        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            when (testState) {
                is FitTestState.InProgress -> {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = theme.primary.copy(alpha = 0.25f),
                            radius = (size.minDimension / 2f) * pulseScale,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Test Yapılıyor",
                        tint = theme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
                is FitTestState.Completed -> {
                    val comp = testState as FitTestState.Completed
                    Icon(
                        imageVector = if (comp.overallPassed) Icons.Rounded.Verified else Icons.Rounded.WarningAmber,
                        contentDescription = "Sonuç",
                        tint = if (comp.overallPassed) SuccessGreen else WarningAmber,
                        modifier = Modifier.size(64.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Rounded.Headphones,
                        contentDescription = "Kulaklık",
                        tint = theme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // State info & Guidance
        when (testState) {
            is FitTestState.Idle -> {
                Text(
                    text = "Kulaklıkları takın ve sessiz bir ortamda kalın.",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Test sırasında kısa bir akustik sinyal çalınacak ve mikrofonlar sızdırmazlığı ölçecektir.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            is FitTestState.InProgress -> {
                Text(
                    text = "Akustik Analiz Yapılıyor...",
                    color = theme.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Lütfen kulaklıkları hareket ettirmeyin ve konuşmayın.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
            is FitTestState.Completed -> {
                val comp = testState as FitTestState.Completed
                AegisCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (comp.overallPassed) "Mükemmel Akustik Uyum" else "Uyum Sorunu Tespit Edildi",
                        color = if (comp.overallPassed) SuccessGreen else WarningAmber,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Sol Kulaklık", color = TextSecondary, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (comp.leftGood) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                                    contentDescription = null,
                                    tint = if (comp.leftGood) SuccessGreen else DangerRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (comp.leftGood) "İyi Uyum" else "Hava Kaçağı",
                                    color = if (comp.leftGood) SuccessGreen else DangerRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Sağ Kulaklık", color = TextSecondary, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (comp.rightGood) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                                    contentDescription = null,
                                    tint = if (comp.rightGood) SuccessGreen else DangerRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (comp.rightGood) "İyi Uyum" else "Hava Kaçağı",
                                    color = if (comp.rightGood) SuccessGreen else DangerRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    if (!comp.overallPassed) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "İpucu: Farklı boyuttaki silikon uçları deneyin veya kulaklığı hafifçe çevirerek oturtun.",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            is FitTestState.Failed -> {
                val failed = testState as FitTestState.Failed
                val title: String
                val solution: String
                val failIcon = when (failed.kind) {
                    FitTestError.NOT_CONNECTED -> {
                        title = "Kulaklık Bağlı Değil"
                        solution = "Kulaklığı bağlayıp tekrar deneyin."
                        Icons.Rounded.BluetoothDisabled
                    }
                    FitTestError.PERMISSION_DENIED -> {
                        title = "Mikrofon İznı Gerekli"
                        solution = "Akustik test, telefon mikrofonuyla ölçüm yapar."
                        Icons.Rounded.MicOff
                    }
                    FitTestError.SCO_UNAVAILABLE -> {
                        title = "Bluetooth Ses Profili Kullanılamıyor"
                        solution = "Kulaklığı yeniden bağlayın; başka bir uygulama sesi meşgul ediyor olabilir."
                        Icons.Rounded.BluetoothAudio
                    }
                    FitTestError.LOW_CONFIDENCE -> {
                        title = "Sonuç Güvenilir Değil"
                        solution = "Sessiz bir ortamda, kulaklıklar takılıyken tekrar deneyin."
                        Icons.Rounded.Info
                    }
                    FitTestError.UNKNOWN -> {
                        title = "Test Başarısız Oldu"
                        solution = "Birkaç saniye sonra tekrar deneyin."
                        Icons.Rounded.ErrorOutline
                    }
                }
                AegisCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = failIcon,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = title,
                                color = DangerRed,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = failed.reason,
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Çözüm: $solution",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                    if (failed.kind == FitTestError.PERMISSION_DENIED) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Uygulama ayarlarını aç", color = theme.primary, fontSize = 13.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tekrar denemek için aşağıdaki butonu kullanın.",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (micDenied && testState is FitTestState.Idle) {
            AegisCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.MicOff,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Mikrofon izni gerekli",
                            color = WarningAmber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Testi başlatmak için mikrofon iznini verin.",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Uygulama ayarlarını aç", color = theme.primary, fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Action Button
        Button(
            onClick = {
                if (testState is FitTestState.InProgress) {
                    fitTestManager.cancelTest()
                } else if (
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    fitTestManager.startTest()
                } else {
                    micPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (testState is FitTestState.InProgress) DangerRed else theme.primary
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = when (testState) {
                    is FitTestState.InProgress -> "Durdur"
                    is FitTestState.Completed -> "Tekrar Test Et"
                    else -> "Testi Başlat"
                },
                color = OledBlack,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}
