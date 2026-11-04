package com.aegissoft.qcybutmod.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegissoft.qcybutmod.core.bluetooth.BleConnectionManager
import com.aegissoft.qcybutmod.core.models.ProductDatabase
import com.aegissoft.qcybutmod.ui.components.AegisCard
import com.aegissoft.qcybutmod.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class FindPattern(val onMs: Long, val offMs: Long, val bursts: Int) {
    CONTINUOUS(0, 0, 1),      // 0=kesintisiz açık
    DOUBLE(400, 300, 2),
    TRIPLE(300, 250, 3)
}

@Composable
fun FindDeviceScreen(
    bleManager: BleConnectionManager,
    onBack: () -> Unit
) {
    val state by bleManager.deviceState.collectAsState()
    val theme = LocalAppThemeColor.current

    var isLeftBeeping by remember { mutableStateOf(false) }
    var isRightBeeping by remember { mutableStateOf(false) }
    var pattern by remember { mutableStateOf(FindPattern.CONTINUOUS) }
    var patternJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val currentGain by com.aegissoft.qcybutmod.core.audio.EarbudAudioLocator.currentVolumeGain.collectAsState()
    val capabilities = remember(state.vendorId, state.modelUiVersion) { ProductDatabase.getCapabilities(state.vendorId) }

    DisposableEffect(Unit) {
        bleManager.startRssiPolling()
        onDispose {
            patternJob?.cancel()
            bleManager.stopRssiPolling()
            bleManager.triggerFindBeep(false, false, sendFirmware = capabilities.hasFind)
        }
    }

    // High-sensitivity RSSI mapping (-95 dBm = 0% to -25 dBm = 100%) so every 1 dBm change updates percentage dynamically
    val displayRssi = if (state.isConnected && state.rssi in -104..-11) state.rssi else -100
    val isRssiValid = state.isConnected && displayRssi in -104..-11
    val rssiIntPercent = if (isRssiValid) {
        (((displayRssi + 95) * 100f) / 70f).toInt().coerceIn(1, 100)
    } else 0
    val rssiProgress = (rssiIntPercent / 100f).coerceIn(0f, 1f)

    // Radar scan animation
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val radarAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarAngle"
    )

    fun runPattern(pattern: FindPattern, left: Boolean, right: Boolean) {
        patternJob?.cancel()
        patternJob = null
        // hasFind yoksa firmware paterni de yok: yalnız yerel zil aç/kapa, 0x05/0x3F gönderilmez
        if (!capabilities.hasFind || pattern == FindPattern.CONTINUOUS) {
            bleManager.triggerFindBeep(left, right, sendFirmware = capabilities.hasFind)
            return
        }
        // Kesintisiz zil yok: patern her zaman sonunda stop ile biter
        patternJob = scope.launch {
            repeat(pattern.bursts) {
                bleManager.triggerFindBeep(left, right, sendFirmware = capabilities.hasFind)
                delay(pattern.onMs)
                bleManager.triggerFindBeep(false, false, sendFirmware = capabilities.hasFind)
                delay(pattern.offMs)
            }
            isLeftBeeping = false
            isRightBeeping = false
        }
    }

    fun toggleBeep(left: Boolean, right: Boolean) {
        isLeftBeeping = left
        isRightBeeping = right
        if (left || right) {
            runPattern(pattern, left, right)
        } else {
            patternJob?.cancel()
            patternJob = null
            bleManager.triggerFindBeep(false, false, sendFirmware = capabilities.hasFind)
        }
    }

    fun selectPattern(newPattern: FindPattern) {
        if (newPattern == pattern) return
        pattern = newPattern
        // Aktifken patern değişimi: eski job iptal edilir, yeni paternle hemen yeniden başlar
        if (isLeftBeeping || isRightBeeping) {
            runPattern(newPattern, isLeftBeeping, isRightBeeping)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
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
                    text = "Kulaklıklarımı Bul",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "BLE RSSI Yakınlık Radarı & Bip Alarmı",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Radar Canvas
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                val center = this.center

                // Concentric circles
                listOf(0.33f, 0.66f, 1.0f).forEach { fraction ->
                    drawCircle(
                        color = DarkBorder,
                        radius = radius * fraction,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                // Rotating radar sweep
                drawArc(
                    color = theme.primary.copy(alpha = 0.25f),
                    startAngle = radarAngle,
                    sweepAngle = 45f,
                    useCenter = true,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                    size = size
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when {
                        !isRssiValid -> "Bağlı Değil"
                        else -> "%$rssiIntPercent ($displayRssi dBm)"
                    },
                    color = if (isRssiValid) theme.primary else DangerRed,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        !isRssiValid -> "Menzil Dışı"
                        displayRssi >= -50 -> "Çok Yakın (< 1 metre)"
                        displayRssi >= -70 -> "Aynı Odada (1 - 3 metre)"
                        else -> "Uzak Sinyal (3+ metre)"
                    },
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Proximity Signal Bar
        AegisCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.SignalCellularAlt,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sinyal Şiddeti (Yakınlık • $displayRssi dBm)",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = "%$rssiIntPercent",
                    color = theme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { rssiProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = theme.primary,
                trackColor = DarkSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Beep Trigger Buttons: hasFind false iken yalnız yerel zil (EarbudAudioLocator) kalır, firmware (0x05/0x3F) gönderilmez
        AegisCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.VolumeUp,
                    contentDescription = null,
                    tint = theme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Yüksek Sesli Bip Alarmı",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Kulaklık takılıyken alarmı açmayın, işitmeye zarar verebilir.",
                color = WarningAmber,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Bulma paterni seçici (firmware zil + LED) yalnızca hasFind cihazlarda görünür
            if (capabilities.hasFind) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FindPattern.values().forEach { p ->
                        FilterChip(
                            selected = pattern == p,
                            onClick = { selectPattern(p) },
                            label = {
                                Text(
                                    text = when (p) {
                                        FindPattern.CONTINUOUS -> "Sürekli"
                                        FindPattern.DOUBLE -> "İkili"
                                        FindPattern.TRIPLE -> "Üçlü"
                                    },
                                    fontSize = 11.sp
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { toggleBeep(!isLeftBeeping, isRightBeeping) },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLeftBeeping) DangerRed else DarkSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isLeftBeeping) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = if (isLeftBeeping) Color.White else TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isLeftBeeping) "Sol Durdur" else "Sol Kulaklık",
                        color = if (isLeftBeeping) Color.White else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = { toggleBeep(isLeftBeeping, !isRightBeeping) },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRightBeeping) DangerRed else DarkSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isRightBeeping) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = if (isRightBeeping) Color.White else TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRightBeeping) "Sağ Durdur" else "Sağ Kulaklık",
                        color = if (isRightBeeping) Color.White else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (isLeftBeeping || isRightBeeping) {
                val gainPct = (currentGain * 100).toInt()
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = theme.primary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VolumeUp,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Kademeli Ses: %$gainPct ${if (gainPct >= 100) "(Maksimum Düzey)" else "(Kademeli Yükseliyor...)"}",
                            color = theme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { toggleBeep(false, false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Tüm Alarmları Sustur", color = DangerRed, fontSize = 12.sp)
                }
            }
        }
    }
}
