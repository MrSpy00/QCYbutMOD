package com.aegissoft.qcybutmod.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.widget.Toast
import com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant
import com.aegissoft.qcybutmod.core.audio.VoiceSettings
import com.aegissoft.qcybutmod.core.audio.VoiceSource
import com.aegissoft.qcybutmod.core.bluetooth.BleConnectionManager
import com.aegissoft.qcybutmod.core.net.PanelRepository
import com.aegissoft.qcybutmod.core.utils.AegisLogger
import com.aegissoft.qcybutmod.ui.components.AegisCard
import com.aegissoft.qcybutmod.ui.theme.*

@Composable
fun SettingsScreen(
    bleManager: BleConnectionManager,
    currentTheme: AppThemeColor,
    onThemeSelected: (AppThemeColor) -> Unit,
    onBack: () -> Unit
) {
    val state by bleManager.deviceState.collectAsState()
    val panelStatus by PanelRepository.status.collectAsState()
    val theme = LocalAppThemeColor.current
    val haptic = LocalHapticFeedback.current

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember(state.modelName) { mutableStateOf(state.modelName) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showUnpairDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Hoisted: the Prompt Tone card (separate LazyColumn item) also gates its slider on this.
    var voiceSource by remember { mutableStateOf(VoiceSettings.getVoiceSource(context)) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // Header
        item {
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
                        text = "Ayarlar & Özelleştirme",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "aegis görünüm, donanım tonları ve aygıt yönetimi",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Theme Palettes Picker (including Dynamic Material You)
        item {
            AegisCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "aegis Surface & Material You Teması",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Text(
                    text = "OLED siyah zemin üzerinde özel aegis renkleri ve cihazın duvar kağıdıyla uyumlu Material You dinamik tema",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                var themeMenuExpanded by remember { mutableStateOf(false) }

                // Active Theme Card with Dropdown Trigger
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurfaceVariant,
                        border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                themeMenuExpanded = !themeMenuExpanded
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(theme.primary)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentTheme.title,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Temayı değiştirmek için dokunun",
                                    color = TextTertiary,
                                    fontSize = 11.sp
                                )
                            }
                            Icon(
                                imageVector = if (themeMenuExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Menüyü Aç",
                                tint = theme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = themeMenuExpanded,
                        onDismissRequest = { themeMenuExpanded = false },
                        modifier = Modifier
                            .background(DarkSurfaceVariant)
                            .border(BorderStroke(1.dp, Color(0xFF282830)), RoundedCornerShape(8.dp))
                    ) {
                        AppThemeColor.values().forEach { themeOption ->
                            val isSelected = currentTheme == themeOption
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(themeOption.primary)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = themeOption.title,
                                            color = if (isSelected) themeOption.primary else TextPrimary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                    }
                                },
                                trailingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = themeOption.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onThemeSelected(themeOption)
                                    themeMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick-Select Horizontal Color Dot Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppThemeColor.values().forEach { themeOption ->
                        val isSelected = currentTheme == themeOption
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) themeOption.primary.copy(alpha = 0.18f) else Color.Transparent)
                                .border(
                                    BorderStroke(
                                        if (isSelected) 1.5.dp else 0.dp,
                                        if (isSelected) themeOption.primary else Color.Transparent
                                    ),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onThemeSelected(themeOption)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(themeOption.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = if (themeOption == AppThemeColor.AMBER || themeOption == AppThemeColor.CYAN) Color.Black else Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Seslendirme ve Anons Kaynağı (Voice Announcement & Source)
        item {
            AegisCard(modifier = Modifier.fillMaxWidth()) {
                val voiceAssistant = remember { AegisVoiceAssistant.getInstance(context) }
                var ttsRate by remember { mutableStateOf(VoiceSettings.getTtsRate(context)) }
                var ttsVolume by remember { mutableStateOf(VoiceSettings.getTtsVolume(context)) }
                var gender by remember { mutableStateOf(VoiceSettings.getVoiceGender(context)) }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RecordVoiceOver,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Sesli Bildirim ve Anons Kaynağı",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Mod geçişleri ve sistem bildirimleri seslendirme tercihi",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3 Source Options — one shared container, selected row highlighted
                    // (per-row borders made the outline look broken)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurfaceVariant
                    ) {
                        Column {
                            VoiceSource.entries.forEach { src ->
                                val isSelected = voiceSource == src
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) theme.primary.copy(alpha = 0.10f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            voiceSource = src
                                            bleManager.applyVoiceSource(src)
                                            if (src == VoiceSource.AEGIS_TR) {
                                                voiceAssistant.speak("aegis Türkçe sesli bildirim devrede")
                                            }
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = src.title,
                                            color = if (isSelected) theme.primary else TextPrimary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = src.subtitle,
                                            color = TextSecondary,
                                            fontSize = 10.5.sp
                                        )
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = theme.primary,
                                            unselectedColor = DarkBorder
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Extra TTS Controls when aegis TR is active
                    if (voiceSource == VoiceSource.AEGIS_TR) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = DarkBorder, thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Speech Rate
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Konuşma Hızı",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "%.2fx".format(ttsRate),
                                color = theme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = ttsRate,
                            onValueChange = {
                                ttsRate = it
                                VoiceSettings.setTtsRate(context, it)
                                voiceAssistant.applySpeechSettings()
                            },
                            valueRange = 0.8f..1.5f,
                            steps = 6,
                            colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary)
                        )

                        // Speech Volume
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ses Düzeyi",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "%${(ttsVolume * 100).toInt()}",
                                color = theme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = ttsVolume,
                            onValueChange = {
                                ttsVolume = it
                                VoiceSettings.setTtsVolume(context, it)
                            },
                            valueRange = 0.2f..1.0f,
                            steps = 7,
                            colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Voice Gender
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ses Cinsi",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = gender == "female",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        gender = "female"
                                        VoiceSettings.setVoiceGender(context, "female")
                                        voiceAssistant.applySpeechSettings()
                                        voiceAssistant.testSpeech()
                                    },
                                    label = { Text("Kadın", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = gender == "male",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        gender = "male"
                                        VoiceSettings.setVoiceGender(context, "male")
                                        voiceAssistant.applySpeechSettings()
                                        voiceAssistant.testSpeech()
                                    },
                                    label = { Text("Erkek", fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                voiceAssistant.testSpeech("aegis Türkçe sesli asistan hazır ve çalışıyor")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Seslendirmeyi Test Et",
                                color = theme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Prompt Tone Volume
        item {
            AegisCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.VolumeUp,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kulaklık Bip Sesi Düzeyi",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        // Firmware tone level is meaningless while the firmware voice is muted
                        text = if (voiceSource == VoiceSource.FIRMWARE_EN) "${state.promptVolume}/15" else "Kapalı",
                        color = theme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = state.promptVolume.toFloat(),
                    onValueChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        bleManager.setPromptVolume(it.toInt())
                    },
                    enabled = voiceSource == VoiceSource.FIRMWARE_EN,
                    valueRange = 0f..15f,
                    steps = 14,
                    colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary)
                )
                if (voiceSource != VoiceSource.FIRMWARE_EN) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Firmware sesi susturuldu; bu ayar yalnızca \"Kulaklık (İngilizce)\" kaynağında etkin.",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Channel Balance Slider
        item {
            AegisCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kanal Dengesi (Sol / Sağ)",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    val balText = when {
                        state.channelBalance == 10 -> "Merkez"
                        state.channelBalance < 10 -> "Sol %${(10 - state.channelBalance) * 10}"
                        else -> "Sağ %${(state.channelBalance - 10) * 10}"
                    }
                    Text(
                        text = balText,
                        color = theme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = state.channelBalance.toFloat(),
                    onValueChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        bleManager.setChannelBalance(it.toInt())
                    },
                    valueRange = 0f..20f,
                    steps = 19,
                    colors = SliderDefaults.colors(thumbColor = theme.primary, activeTrackColor = theme.primary)
                )
            }
        }

        // Device Management & Hardware Technical Specs
        item {
            AegisCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Aygıt Yönetimi & Donanım Bilgileri",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Visible Current Device Name Card + Explicit "Adlandır" Button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceVariant,
                    border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mevcut Aygıt Adı",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = state.modelName.ifEmpty { "QCY MeloBuds Pro" },
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                renameInput = state.modelName
                                showRenameDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = OledBlack,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Adlandır",
                                color = OledBlack,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Technical Info Grid
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceVariant)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val classicMac = state.classicMac.ifEmpty { state.bleAddress.ifEmpty { "--" } }
                    // Show BLE GATT MAC only when it is a DISTINCT address; after a failed
                    // direct-LE attempt bleAddress falls back to classicMac and would duplicate the row.
                    val bleMac = state.bleAddress
                        .takeIf { it.isNotEmpty() && !it.equals(state.classicMac, ignoreCase = true) }
                        ?: "--"
                    val fw = state.firmwareVersion.ifEmpty { "--" }
                    val vendorHex = "0x${state.vendorId.toString(16).uppercase()} (${state.vendorId})"
                    val rssiStr = if (state.isConnected && state.rssi in -104..-11) {
                        val pct = (((state.rssi + 95) * 100f) / 70f).toInt().coerceIn(1, 100)
                        "${state.rssi} dBm (%$pct)"
                    } else "-- (Bağlı Değil)"

                    val battStr = if (state.isConnected) {
                        val l = if (state.battery.leftLevel in 0..100) "%${state.battery.leftLevel}" else "--"
                        val r = if (state.battery.rightLevel in 0..100) "%${state.battery.rightLevel}" else "--"
                        val c = if (state.battery.caseLevel in 0..100) "%${state.battery.caseLevel}" else "--"
                        "L: $l  |  R: $r  |  Kutu: $c"
                    } else "L: --  |  R: --  |  Kutu: --"

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Klasik BT MAC (BR/EDR)", color = TextSecondary, fontSize = 12.sp)
                        Text(text = classicMac, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "BLE GATT MAC", color = TextSecondary, fontSize = 12.sp)
                        Text(text = bleMac, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Vendor ID / Yonga", color = TextSecondary, fontSize = 12.sp)
                        Text(text = "$vendorHex • WQ7033", color = theme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Firmware Sürümü", color = TextSecondary, fontSize = 12.sp)
                        Text(text = fw, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Anlık Pil Seviyeleri", color = TextSecondary, fontSize = 12.sp)
                        Text(text = battStr, color = if (state.isConnected) SuccessGreen else TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Sinyal Şiddeti (RSSI)", color = TextSecondary, fontSize = 12.sp)
                        Text(text = rssiStr, color = if (state.isConnected) theme.primary else DangerRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Protokol Durumu", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            text = if (state.isConnected) "Bağlı (GATT 0000A001 + A2DP)" else "Bağlantı Kesildi",
                            color = if (state.isConnected) SuccessGreen else TextTertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions: Disconnect/Reconnect & Unpair (Row 1), Factory Reset (Row 2)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Disconnect / Reconnect Button
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (state.isConnected) {
                                    bleManager.disconnect()
                                } else {
                                    bleManager.reconnectSelectedDevice()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isConnected) DarkSurfaceVariant else theme.primary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (state.isConnected) Icons.Rounded.LinkOff else Icons.Rounded.BluetoothConnected,
                                contentDescription = null,
                                tint = if (state.isConnected) TextPrimary else OledBlack,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (state.isConnected) "Bağlantıyı Kes" else "Yeniden Bağlan",
                                color = if (state.isConnected) TextPrimary else OledBlack,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // Unpair Button
                        Button(
                            onClick = { showUnpairDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Eşleşmeyi Sil",
                                color = WarningAmber,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Reset Button (Full width)
                    Button(
                        onClick = { showResetDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Fabrika Ayarlarına Sıfırla",
                            color = DangerRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Panel JSON durum satırı (firmware delta takibi — Task 11)
        item {
            val panelStatusText = when (val s = panelStatus) {
                is PanelRepository.Status.Idle -> "—"
                is PanelRepository.Status.Fetching -> "Yenileniyor..."
                is PanelRepository.Status.Ok -> s.file
                is PanelRepository.Status.Error -> "Hata: ${s.message}"
            }
            AegisCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Panel JSON: ${if (state.vendorId == 0) "--" else state.vendorId} • ${state.firmwareVersion.ifEmpty { "--" }} • $panelStatusText",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Log Yönetimi (Forensic Debug Logs)
        item {
            AegisCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Adli Teşhis Logları",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "qcy_debug.log dosyası ve canlı telemetri kayıtları",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val logContent = AegisLogger.getLogText()
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "QCYbutMOD Adli Teşhis Logu")
                                putExtra(Intent.EXTRA_TEXT, logContent)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Logu Paylaş"))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Logu Paylaş",
                            color = theme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Button(
                        onClick = {
                            AegisLogger.clear()
                            Toast.makeText(context, "Loglar temizlendi", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Temizle",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // About & aegisSoft Signature
        item {
            AegisCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = DarkSurfaceVariant
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "aegis QCYbutMOD v1.1.0",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Geliştirici: mrSpy / aegisSoft (aegissoft.com.tr)",
                    color = theme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Yerel öncelikli, sıfır telemetri, sıfır webview ve sıfır reklam mimarisi ile donanımın saf potansiyeli için tasarlanmıştır.",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(text = "Kulaklığı Yeniden Adlandır", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Yeni İsim") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.primary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        bleManager.renameDevice(renameInput.trim())
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                ) {
                    Text(text = "Kaydet", color = OledBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(text = "İptal", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Reset Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(text = "Fabrika Ayarlarına Sıfırla", color = DangerRed) },
            text = {
                Text(
                    text = "Kulaklığın tüm hafızası ve eşleşme bilgileri silinecek ve cihaz yeniden başlatılacaktır. Emin misiniz?",
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        bleManager.factoryReset()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text(text = "Evet, Sıfırla", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(text = "İptal", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Unpair Dialog
    if (showUnpairDialog) {
        AlertDialog(
            onDismissRequest = { showUnpairDialog = false },
            title = { Text(text = "Aygıt Eşleşmesini Sil", color = WarningAmber) },
            text = {
                Text(
                    text = "Bu kulaklığın Android sistemindeki Bluetooth eşleşmesi kaldırılacak ve bağlantı sonlandırılacaktır. Devam etmek istiyor musunuz?",
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetAddr = state.classicMac.ifEmpty { state.bleAddress }
                        bleManager.unpairDevice(targetAddr)
                        showUnpairDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningAmber)
                ) {
                    Text(text = "Evet, Eşleşmeyi Sil", color = OledBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnpairDialog = false }) {
                    Text(text = "İptal", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}
