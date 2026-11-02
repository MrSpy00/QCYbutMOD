package com.aegissoft.qcybutmod.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegissoft.qcybutmod.core.audio.VoiceSettings
import com.aegissoft.qcybutmod.core.bluetooth.BleConnectionManager
import com.aegissoft.qcybutmod.core.bluetooth.PromptMutePolicy
import com.aegissoft.qcybutmod.core.models.GestureFunctions
import com.aegissoft.qcybutmod.core.models.ProductDatabase
import com.aegissoft.qcybutmod.core.models.TouchSides
import com.aegissoft.qcybutmod.core.models.longPressAvailable
import com.aegissoft.qcybutmod.ui.components.AegisCard
import com.aegissoft.qcybutmod.ui.theme.*

data class GestureConfig(
    val gestureName: String,
    val keyId: Int,
    var selectedFuncId: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureScreen(
    bleManager: BleConnectionManager,
    onBack: () -> Unit
) {
    val theme = LocalAppThemeColor.current
    val state by bleManager.deviceState.collectAsState()
    var selectedEarbud by remember { mutableStateOf(0) } // 0 = Left, 1 = Right
    val capabilities = remember(state.vendorId) { ProductDatabase.getCapabilities(state.vendorId) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val assistantAllowed = remember(context) {
        PromptMutePolicy.shouldAllowVoiceAssistantGesture(VoiceSettings.getVoiceSource(context).id)
    }
    val pickerFunctions = remember(assistantAllowed) {
        if (assistantAllowed) GestureFunctions.functionList
        else GestureFunctions.functionList.filter { it.first != GestureFunctions.VOICE_ASSISTANT }
    }

    // Query gesture mappings from earbud hardware (0000000D + 0x2B) on screen open
    LaunchedEffect(Unit) {
        bleManager.refreshKeyMappings()
    }

    val hasLongPressRuntime = capabilities.longPressAvailable(state.keyMappings)

    val leftGestures = remember(state.keyMappings, hasLongPressRuntime) {
        listOf(
            GestureConfig("Tek Dokunma", 1, state.keyMappings[1] ?: GestureFunctions.NONE),
            GestureConfig("Çift Dokunma", 3, state.keyMappings[3] ?: GestureFunctions.PLAY_PAUSE),
            GestureConfig("Üç Dokunma", 5, state.keyMappings[5] ?: GestureFunctions.VOICE_ASSISTANT)
        ) + if (hasLongPressRuntime) listOf(
            // 0x2B tablosu keyId 7/8 canlı kanıtlandı; panel verisi yoksa CapabilityParser varsayılanı gösterir
            GestureConfig("Uzun Basma (2 sn)", 7, state.keyMappings[7] ?: GestureFunctions.NONE)
        ) else emptyList()
    }

    val rightGestures = remember(state.keyMappings, hasLongPressRuntime) {
        listOf(
            GestureConfig("Tek Dokunma", 2, state.keyMappings[2] ?: GestureFunctions.NONE),
            GestureConfig("Çift Dokunma", 4, state.keyMappings[4] ?: GestureFunctions.PLAY_PAUSE),
            GestureConfig("Üç Dokunma", 6, state.keyMappings[6] ?: GestureFunctions.NEXT_TRACK)
        ) + if (hasLongPressRuntime) listOf(
            GestureConfig("Uzun Basma (2 sn)", 8, state.keyMappings[8] ?: GestureFunctions.NONE)
        ) else emptyList()
    }

    var dialogGestureTarget by remember { mutableStateOf<GestureConfig?>(null) }

    fun updateGesture(config: GestureConfig, funcId: Int) {
        val earbudName = if (selectedEarbud == 0) "Sol kulaklık" else "Sağ kulaklık"
        bleManager.setTouchGesture(config.keyId, funcId, earbudName, config.gestureName)
    }

    val currentList = if (selectedEarbud == 0) leftGestures else rightGestures
    // Yetenek kapısı (H4): seçili tarafta dokunmatik sensör yoksa içerik yerine bilgi kartı
    val sideMissingTouch = if (selectedEarbud == 0) {
        capabilities.touchSides == TouchSides.RIGHT
    } else {
        capabilities.touchSides == TouchSides.LEFT
    }

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
                        text = "Dokunmatik Jest Eşleme",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Kulaklık dokunmatik sensör kontrolü",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Earbud Tab (Sol vs Sağ)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0 to "Sol Kulaklık (L)", 1 to "Sağ Kulaklık (R)").forEach { (idx, label) ->
                    val isSelected = selectedEarbud == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) theme.primary else Color.Transparent)
                            .clickable { selectedEarbud = idx }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Headphones,
                                contentDescription = null,
                                tint = if (isSelected) OledBlack else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                color = if (isSelected) OledBlack else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Gestures List — yetenek kapısı: tarafta dokunmatik sensör yoksa açıklayıcı kart
        if (sideMissingTouch) {
            item {
                AegisCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = DarkSurfaceVariant
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedEarbud == 0) {
                                "Bu cihazda sol kulaklıkta dokunmatik algılama yok"
                            } else {
                                "Bu cihazda sağ kulaklıkta dokunmatik algılama yok"
                            },
                            color = WarningAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            items(currentList.size) { index ->
                val gesture = currentList[index]
                val funcName = GestureFunctions.functionList.find { it.first == gesture.selectedFuncId }?.second ?: "Seçilmedi"

                AegisCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { dialogGestureTarget = gesture }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = gesture.gestureName,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = funcName,
                                color = theme.primary,
                                fontSize = 13.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Düzenle",
                            tint = TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // Function Selection Modal Dialog
    if (dialogGestureTarget != null) {
        val target = dialogGestureTarget!!
        AlertDialog(
            onDismissRequest = { dialogGestureTarget = null },
            title = {
                Text(
                    text = "${target.gestureName} İşlevi",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pickerFunctions.size) { idx ->
                        val (funcId, label) = pickerFunctions[idx]
                        val isSelected = target.selectedFuncId == funcId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    updateGesture(target, funcId)
                                    dialogGestureTarget = null
                                },
                            color = if (isSelected) theme.primary.copy(alpha = 0.2f) else DarkSurfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) theme.primary else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Seçili",
                                        tint = theme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { dialogGestureTarget = null }) {
                    Text(text = "Kapat", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
