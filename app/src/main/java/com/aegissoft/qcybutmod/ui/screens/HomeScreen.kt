package com.aegissoft.qcybutmod.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aegissoft.qcybutmod.core.audio.NoiseMonitor
import com.aegissoft.qcybutmod.core.bluetooth.BleConnectionManager
import com.aegissoft.qcybutmod.core.models.AncMode
import com.aegissoft.qcybutmod.core.models.AncSubScene
import com.aegissoft.qcybutmod.core.models.ProductDatabase
import com.aegissoft.qcybutmod.ui.components.AegisCard
import com.aegissoft.qcybutmod.ui.components.BatteryRing
import com.aegissoft.qcybutmod.ui.components.DeviceSelectionSheet
import com.aegissoft.qcybutmod.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    bleManager: BleConnectionManager,
    onNavigate: (String) -> Unit
) {
    val state by bleManager.deviceState.collectAsState()
    val isScanning by bleManager.isScanning.collectAsState()
    val discoveredList by bleManager.discoveredDevices.collectAsState()
    val theme = LocalAppThemeColor.current
    val haptic = LocalHapticFeedback.current

    var showDeviceSheet by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    val syncRotation by animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        finishedListener = { isRefreshing = false },
        label = "syncRot"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
    ) {
        // Fixed Top Bar (Always visible, perfectly padded, zero scroll jitter)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "QCYbutMOD",
                    color = theme.primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "aegisSoft Modern Edition",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Devices switcher button (fixed 42x42dp)
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showDeviceSheet = true
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceVariant)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Headphones,
                            contentDescription = "Cihazlar",
                            tint = if (state.isConnected) theme.primary else TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        if (state.isConnected) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(SuccessGreen)
                            )
                        }
                    }
                }

                // 2. Settings button (fixed 42x42dp)
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigate("settings")
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Ayarlar",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        val context = androidx.compose.ui.platform.LocalContext.current
        val isOfficialInstalled = remember { bleManager.isOfficialQcyAppInstalled() }
        var showOfficialNotice by remember { mutableStateOf(false) }

        // Gürültü göstergesi: izin her recomposition'da taze okunur ki ilk açılışta
        // sonradan verilen mikrofon izni start() tetikleyebilsin
        val noiseMonitor = remember { NoiseMonitor() }
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        DisposableEffect(hasMicPermission) {
            if (hasMicPermission) noiseMonitor.start()
            onDispose { noiseMonitor.stop() }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)
        ) {
            if (isOfficialInstalled && showOfficialNotice) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = WarningAmber.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = null,
                                        tint = WarningAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Orijinal QCY Uygulaması Algılandı",
                                        color = WarningAmber,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(
                                    onClick = { showOfficialNotice = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Kapat",
                                        tint = TextTertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "aegis QCY doğrudan BLE GATT üzerinden çalıştığı için orijinal uygulamayı kaldırmanıza gerek yoktur. Çakışmayı önlemek için yalnızca arka planda kapalı tutmanız yeterlidir.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Hero Device & Battery Card
            item {
                AegisCard(
                    hasGlow = state.isConnected,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.modelName.ifEmpty { "QCY MeloBuds Pro" },
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val subStatus = when {
                                state.isConnected -> "Bağlı • BLE GATT + A2DP Aktif"
                                state.isConnecting -> "Kulaklığa bağlanılıyor..."
                                else -> "Bağlı Değil • L: -- | R: -- | Kutu: --"
                            }
                            Text(
                                text = subStatus,
                                color = if (state.isConnected) theme.primary else TextTertiary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (state.isConnected) {
                                IconButton(
                                    onClick = {
                                        isRefreshing = true
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        bleManager.queryStateAndBattery()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Yenile",
                                        tint = theme.primary,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .rotate(syncRotation)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (state.isConnected) theme.primary.copy(alpha = 0.15f) else DarkSurfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (state.isConnected) theme.primary.copy(alpha = 0.4f) else DarkBorder
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (state.isConnected) {
                                            bleManager.disconnect()
                                        } else {
                                            bleManager.reconnectSelectedDevice()
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (state.isConnected) SuccessGreen else TextTertiary)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (state.isConnected) "Bağlı" else if (state.isConnecting) "Bağlanıyor" else "Bağlı Değil",
                                        color = if (state.isConnected) theme.primary else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    val isHeadphone = state.modelName.contains("H4", ignoreCase = true) ||
                            state.modelName.contains("H3", ignoreCase = true) ||
                            state.modelName.contains("H2", ignoreCase = true)

                    // Strictly show -1 (--) when disconnected so stale battery never remains on screen
                    val dispLeft = if (state.isConnected) state.battery.leftLevel else -1
                    val dispRight = if (state.isConnected) state.battery.rightLevel else -1
                    val dispCase = if (state.isConnected) state.battery.caseLevel else -1
                    val dispLeftChg = state.isConnected && state.battery.isLeftCharging
                    val dispRightChg = state.isConnected && state.battery.isRightCharging
                    val dispCaseChg = state.isConnected && state.battery.isCaseCharging

                    fun announceBattery() {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (!state.isConnected) {
                            com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant.getInstance(context)
                                .speak("Kulaklık şu anda bağlı değil")
                            return
                        }
                        com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant.getInstance(context)
                            .announceBatteryStatus(dispLeft, dispRight, dispCase)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { announceBattery() }
                    ) {
                        if (isHeadphone) {
                            val headLevel = if (dispLeft >= 0) dispLeft else dispRight
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                BatteryRing(
                                    level = headLevel,
                                    isCharging = dispLeftChg || dispRightChg,
                                    label = "Kulaklık Bataryası",
                                    size = 84.dp,
                                    strokeWidth = 7.dp
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    BatteryRing(
                                        level = dispLeft,
                                        isCharging = dispLeftChg,
                                        label = "Sol Kulaklık"
                                    )
                                    BatteryRing(
                                        level = dispRight,
                                        isCharging = dispRightChg,
                                        label = "Sağ Kulaklık"
                                    )
                                    BatteryRing(
                                        level = dispCase,
                                        isCharging = dispCaseChg,
                                        label = "Şarj Kutusu"
                                    )
                                }
                                if (state.isConnected && dispCase < 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Kutu şarjı en az bir kulaklık kutudayken okunur • Sesli bildirim için halkalara dokunun",
                                        color = TextTertiary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    if (!state.isConnected) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    bleManager.reconnectSelectedDevice()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BluetoothConnected,
                                    contentDescription = null,
                                    tint = OledBlack,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Hızlı Bağlan",
                                    color = OledBlack,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showDeviceSheet = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Headphones,
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Cihaz Seç",
                                    color = theme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // Ortam gürültü seviyesi — telefon mikrofonuyla yaklaşık ölçüm
            item {
                val noiseDb by noiseMonitor.dbfs.collectAsState()
                val noiseHistory by noiseMonitor.history.collectAsState()

                AegisCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Ortam Gürültü Seviyesi",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (hasMicPermission) String.format("%.1f dB", noiseDb.toDouble())
                        else "Mikrofon izni verilmedi",
                        color = if (hasMicPermission) theme.primary else TextTertiary,
                        fontSize = if (hasMicPermission) 34.sp else 16.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Telefon mikrofonuyla yaklaşık ölçüm",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceVariant)
                    ) {
                        // Sabit 100 örneklik (≈10sn) zaman ekseni, en sağ = en yeni örnek
                        if (noiseHistory.size >= 2) {
                            val stepX = size.width / 100f
                            val path = Path().apply {
                                noiseHistory.forEachIndexed { i, db ->
                                    val x = i * stepX
                                    val y = size.height * (-db / 90f)
                                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = theme.primary,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }

            // ANC Master Control Section
            if (ProductDatabase.getCapabilities(state.vendorId).hasAnc) item {
                AegisCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gürültü Kontrolü",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val activeAncLabel = when (state.ancMode) {
                            AncMode.OFF -> "Normal Mod"
                            AncMode.ANC -> if (state.isAiAdaptiveAncOn) "AI Adaptif ANC" else "ANC • ${state.ancSubScene.title}"
                            AncMode.TRANSPARENCY -> "Şeffaf Mod (${state.transparencyLevel}/6)"
                        }
                        Text(
                            text = activeAncLabel,
                            color = theme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3-way Segmented Button Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkSurfaceVariant)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            AncMode.OFF to "Normal",
                            AncMode.ANC to "ANC",
                            AncMode.TRANSPARENCY to "Şeffaf"
                        ).forEach { (mode, label) ->
                            val isSelected = state.ancMode == mode
                            val bgColor by animateColorAsState(
                                targetValue = if (isSelected) theme.primary else Color.Transparent,
                                label = "ancBg"
                            )
                            val textColor = if (isSelected) OledBlack else TextSecondary

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bgColor)
                                    .clickable {
                                        if (!state.isConnected) return@clickable
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        bleManager.setAnc(mode)
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = textColor,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Sub-scenes & AI Adaptive ANC when ANC is active
                    AnimatedVisibility(
                        visible = state.ancMode == AncMode.ANC,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            // AI Adaptive ANC Toggle Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurfaceVariant)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // end padding keeps wrapped description clear of the Switch
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "AI Adaptif Gürültü Engelleme",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Ortam gürültüsüne göre ANC derinliğini otomatik ayarlar",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = state.isAiAdaptiveAncOn,
                                    onCheckedChange = {
                                        if (!state.isConnected) return@Switch
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        bleManager.setAdaptiveAiAnc(it)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = OledBlack,
                                        checkedTrackColor = theme.primary
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "ANC Ortam Modu",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AncSubScene.values().forEach { scene ->
                                    val selected = state.ancSubScene == scene
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) theme.primary else DarkSurfaceVariant,
                                        border = BorderStroke(1.dp, if (selected) theme.primary else DarkBorder),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                if (!state.isConnected) return@clickable
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                bleManager.setAnc(AncMode.ANC, subScene = scene)
                                            }
                                    ) {
                                        Text(
                                            text = scene.title,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) OledBlack else TextSecondary,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            if (state.ancSubScene in listOf(AncSubScene.INDOOR, AncSubScene.COMMUTE, AncSubScene.NOISY)) {
                                Spacer(modifier = Modifier.height(14.dp))

                                val levelLabel = when (state.ancLevel.coerceIn(1, 3)) {
                                    1 -> "Düşük (1/3)"
                                    2 -> "Orta (2/3)"
                                    else -> "Yüksek (3/3)"
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Gürültü Engelleme Derinliği",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = levelLabel,
                                        color = theme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Slider(
                                    value = state.ancLevel.coerceIn(1, 3).toFloat(),
                                    onValueChange = {
                                        if (!state.isConnected) return@Slider
                                        val newLvl = it.toInt().coerceIn(1, 3)
                                        if (newLvl != state.ancLevel) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            bleManager.setAnc(AncMode.ANC, level = newLvl)
                                        }
                                    },
                                    valueRange = 1f..3f,
                                    steps = 1,
                                    colors = SliderDefaults.colors(
                                        thumbColor = theme.primary,
                                        activeTrackColor = theme.primary
                                    )
                                )
                            }
                        }
                    }

                    // Transparency slider
                    AnimatedVisibility(
                        visible = state.ancMode == AncMode.TRANSPARENCY,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Şeffaf Mod Geçirgenlik Seviyesi",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "${state.transparencyLevel}/6",
                                    color = theme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = state.transparencyLevel.toFloat(),
                                onValueChange = {
                                    if (!state.isConnected) return@Slider
                                    val newLvl = it.toInt().coerceIn(1, 6)
                                    if (newLvl != state.transparencyLevel) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        bleManager.setAnc(AncMode.TRANSPARENCY, transLevel = newLvl)
                                    }
                                },
                                valueRange = 1f..6f,
                                steps = 4,
                                colors = SliderDefaults.colors(
                                    thumbColor = theme.primary,
                                    activeTrackColor = theme.primary
                                )
                            )
                        }
                    }
                }
            }

            // Quick Hardware Toggles Grid (2x2) with Equal Dimensions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickToggleCard(
                            icon = Icons.Rounded.SportsEsports,
                            title = "Oyun Modu",
                            subtitle = if (state.isGameModeOn) "Açık • Düşük Gecikme" else "Kapalı • Standart",
                            isActive = state.isGameModeOn,
                            themeColor = theme.primary,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                bleManager.setGameMode(!state.isGameModeOn)
                            }
                        )

                        if (ProductDatabase.getCapabilities(state.vendorId).hasLdac) {
                            QuickToggleCard(
                                icon = Icons.Rounded.HighQuality,
                                title = "LDAC HD Ses",
                                subtitle = if (state.isLdacOn) "Açık • 990 kbps Hi-Res" else "Kapalı • AAC/SBC",
                                isActive = state.isLdacOn,
                                themeColor = theme.primary,
                                warning = "Uyarı: Kulaklık kendiliğinden kapanıp açılabilir",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    bleManager.setLdac(!state.isLdacOn)
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickToggleCard(
                            icon = Icons.Rounded.Hearing,
                            title = "Kulak Algılama",
                            subtitle = if (state.isInEarDetectionOn) "Açık • Otomatik Durdur" else "Kapalı • Devre Dışı",
                            isActive = state.isInEarDetectionOn,
                            themeColor = theme.primary,
                            warning = "Uyarı: Kulaklık kendiliğinden kapanıp açılabilir",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                bleManager.setInEarDetection(!state.isInEarDetectionOn)
                            }
                        )

                        QuickToggleCard(
                            icon = Icons.Rounded.Bedtime,
                            title = "Uyku Modu",
                            subtitle = if (state.isSleepModeOn) "Açık • Dokunmatik Kilitli" else "Kapalı • Sensör Aktif",
                            isActive = state.isSleepModeOn,
                            themeColor = WarningAmber,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                bleManager.setSleepMode(!state.isSleepModeOn)
                            }
                        )
                    }
                }
            }

            // Navigation Menu Cards with Premium Vector Icons
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NavCard(
                        icon = Icons.Rounded.GraphicEq,
                        title = "10-Bant Parametrik Ekolayzer",
                        subtitle = "Sol/Sağ bağımsız kanal profilleri, biquad eğrileri",
                        themeColor = theme.primary,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigate("equalizer")
                        }
                    )

                    NavCard(
                        icon = Icons.Rounded.TouchApp,
                        title = "Dokunmatik Jest Eşleme",
                        subtitle = "Tek, çift ve üç dokunma işlevleri",
                        themeColor = theme.primary,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigate("gestures")
                        }
                    )

                    NavCard(
                        icon = Icons.Rounded.Headphones,
                        title = "Kulak Uyum & Akustik Testi",
                        subtitle = "Donanımsal mikrofon akustik sızdırmazlık analizi",
                        themeColor = theme.primary,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigate("fittest")
                        }
                    )

                    NavCard(
                        icon = Icons.Rounded.Radar,
                        title = "Kulaklıklarımı Bul",
                        subtitle = "BLE RSSI radar takibi & yüksek sesli bip alarmı",
                        themeColor = theme.primary,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigate("find")
                        }
                    )

                    NavCard(
                        icon = Icons.Rounded.Devices,
                        title = "Çift Cihaz Bağlantısı (Multipoint)",
                        subtitle = if (state.isMultipointOn) "Açık • Eşzamanlı iki ses kaynağı yönetimi" else "Kapalı • Tek cihaz bağlantısı",
                        themeColor = theme.primary,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigate("multipoint")
                        }
                    )

                    NavCard(
                        icon = Icons.Rounded.Translate,
                        title = "AI Canlı Çeviri",
                        subtitle = "Çift dilli anlık konuşma çevirisi & kulaklığa sesli okuma",
                        themeColor = theme.primary,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigate("translate")
                        }
                    )
                }
            }
        }
    }

    if (showDeviceSheet) {
        DeviceSelectionSheet(
            devices = discoveredList,
            connectedAddress = state.bleAddress,
            connectedClassicMac = state.classicMac,
            connectedModelName = state.modelName,
            isStateConnected = state.isConnected,
            isConnecting = state.isConnecting,
            connectingAddress = state.connectingAddress,
            isScanning = isScanning,
            onStartScan = {
                bleManager.startScanning(autoConnect = false)
            },
            onStopScan = { bleManager.stopScanning() },
            onSelectDevice = { dev ->
                val targetAddr = if (dev.isBonded && !dev.classicMac.isNullOrEmpty() && dev.bleAddress == dev.classicMac) {
                    dev.classicMac
                } else {
                    dev.bleAddress
                }
                bleManager.connect(targetAddr)
            },
            onDisconnectDevice = {
                bleManager.disconnect()
            },
            onDismissRequest = { showDeviceSheet = false }
        )
    }
}

@Composable
fun QuickToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isActive: Boolean,
    themeColor: Color,
    modifier: Modifier = Modifier,
    warning: String? = null,
    onClick: () -> Unit
) {
    AegisCard(
        modifier = modifier.defaultMinSize(minHeight = 80.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        hasGlow = isActive,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) themeColor else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (isActive) themeColor else DarkBorder)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            color = if (isActive) themeColor else TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (warning != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = warning,
                color = WarningAmber,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 2
            )
        }
    }
}

@Composable
fun NavCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    themeColor: Color,
    onClick: () -> Unit
) {
    AegisCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = themeColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = themeColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
