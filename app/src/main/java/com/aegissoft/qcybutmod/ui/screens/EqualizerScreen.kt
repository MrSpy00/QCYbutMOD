package com.aegissoft.qcybutmod.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant
import com.aegissoft.qcybutmod.core.audio.EqBand
import com.aegissoft.qcybutmod.core.audio.ParametricEq
import com.aegissoft.qcybutmod.core.bluetooth.BleConnectionManager
import com.aegissoft.qcybutmod.ui.components.AegisCard
import com.aegissoft.qcybutmod.ui.components.InteractiveEqGraph
import com.aegissoft.qcybutmod.ui.theme.*

@Composable
fun EqualizerScreen(
    bleManager: BleConnectionManager,
    onBack: () -> Unit
) {
    val state by bleManager.deviceState.collectAsState()
    val theme = LocalAppThemeColor.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    var selectedPresetId by remember(state.eqPresetId) { mutableStateOf(state.eqPresetId) }
    var leftBands by remember(state.eqGains) {
        mutableStateOf(
            ParametricEq.STANDARD_FREQUENCIES.mapIndexed { idx, freq ->
                EqBand(freq, state.eqGains.getOrElse(idx) { 0f })
            }
        )
    }
    var rightBands by remember(state.eqGains) {
        mutableStateOf(
            ParametricEq.STANDARD_FREQUENCIES.mapIndexed { idx, freq ->
                EqBand(freq, state.eqGains.getOrElse(idx) { 0f })
            }
        )
    }

    // Channel selection: 0 = Stereo Linked, 1 = Left Only, 2 = Right Only
    var selectedChannelMode by remember { mutableStateOf(0) }

    val activeBands = when (selectedChannelMode) {
        1 -> leftBands
        2 -> rightBands
        else -> leftBands
    }

    fun syncEqToEarbuds(presetId: String = selectedPresetId, announceName: String? = null) {
        bleManager.applyEqualizer(
            presetId = presetId,
            leftBands = leftBands,
            rightBands = rightBands,
            stereoLinked = (selectedChannelMode == 0)
        )
        if (!announceName.isNullOrBlank()) {
            AegisVoiceAssistant.getInstance(context).speak("Ekolayzer $announceName moduna ayarlandı")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // Top Bar
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
                        text = "10-Bant Parametrik Ekolayzer",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Donanımsal Biquad IIR Filtreleme (000B & 0x20/0x22)",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Live Curve Visualizer
        item {
            InteractiveEqGraph(bands = activeBands)
        }

        // Channel Mode Switcher (Stereo Linked vs Sol vs Sağ)
        item {
            AegisCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Kanal Eşleme Modu",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        0 to "Stereo (Bağlı)",
                        1 to "Sol Kulaklık",
                        2 to "Sağ Kulaklık"
                    ).forEach { (mode, label) ->
                        val isSelected = selectedChannelMode == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedChannelMode = mode
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    color = if (isSelected) OledBlack else TextPrimary
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = theme.primary
                            )
                        )
                    }
                }
            }
        }

        // Presets Carousel
        item {
            Column {
                Text(
                    text = "Hazır Önayarlar (Presets)",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ParametricEq.PRESETS.forEach { preset ->
                        val isSelected = selectedPresetId == preset.id
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedPresetId = preset.id
                                val updated = preset.gains.mapIndexed { idx, g ->
                                    EqBand(ParametricEq.STANDARD_FREQUENCIES[idx], g)
                                }
                                if (selectedChannelMode == 0 || selectedChannelMode == 1) {
                                    leftBands = updated
                                }
                                if (selectedChannelMode == 0 || selectedChannelMode == 2) {
                                    rightBands = updated
                                }
                                syncEqToEarbuds(presetId = preset.id, announceName = preset.name)
                            },
                            label = {
                                Text(
                                    text = preset.name,
                                    fontSize = 12.sp,
                                    color = if (isSelected) OledBlack else TextPrimary
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = theme.primary
                            )
                        )
                    }
                }
            }
        }

        // 10-Band Sliders Bank
        item {
            AegisCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Frekans Kazanç Ayarları (-10dB / +10dB)",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(14.dp))

                activeBands.forEachIndexed { index, band ->
                    val freqLabel = if (band.frequencyHz >= 1000) "${band.frequencyHz / 1000} kHz" else "${band.frequencyHz} Hz"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = freqLabel,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.width(60.dp)
                        )

                        Slider(
                            value = band.gainDb,
                            onValueChange = { newGain ->
                                selectedPresetId = "custom"
                                val updated = activeBands.toMutableList()
                                updated[index] = band.copy(gainDb = newGain)
                                if (selectedChannelMode == 0) {
                                    leftBands = updated
                                    rightBands = updated
                                } else if (selectedChannelMode == 1) {
                                    leftBands = updated
                                } else {
                                    rightBands = updated
                                }
                                syncEqToEarbuds(presetId = "custom")
                            },
                            valueRange = -10f..10f,
                            steps = 19,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = theme.primary,
                                activeTrackColor = theme.primary
                            )
                        )

                        Text(
                            text = "${if (band.gainDb > 0) "+" else ""}${band.gainDb.toInt()} dB",
                            color = if (band.gainDb != 0f) theme.primary else TextTertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(45.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedPresetId = "flat"
                            leftBands = ParametricEq.createDefaultBands()
                            rightBands = ParametricEq.createDefaultBands()
                            syncEqToEarbuds(presetId = "flat", announceName = "Varsayılan Düz")
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "Sıfırla", color = TextSecondary)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            syncEqToEarbuds(presetId = selectedPresetId, announceName = "Özel")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "Kulaklığa Yaz", color = OledBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
