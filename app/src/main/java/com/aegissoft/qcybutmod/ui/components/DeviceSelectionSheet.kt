package com.aegissoft.qcybutmod.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegissoft.qcybutmod.core.bluetooth.DiscoveredQcyDevice
import com.aegissoft.qcybutmod.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSelectionSheet(
    devices: List<DiscoveredQcyDevice>,
    connectedAddress: String,
    connectedClassicMac: String = "",
    connectedModelName: String = "",
    isStateConnected: Boolean = false,
    isConnecting: Boolean = false,
    connectingAddress: String = "",
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onSelectDevice: (DiscoveredQcyDevice) -> Unit,
    onDisconnectDevice: () -> Unit = {},
    onDismissRequest: () -> Unit
) {
    val theme = LocalAppThemeColor.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun isDeviceConnected(dev: DiscoveredQcyDevice): Boolean {
        if (!isStateConnected) return false
        if (connectedAddress.isNotEmpty() && (
                connectedAddress.equals(dev.bleAddress, ignoreCase = true) ||
                (dev.classicMac != null && connectedAddress.equals(dev.classicMac, ignoreCase = true))
            )
        ) return true
        if (connectedClassicMac.isNotEmpty() && (
                connectedClassicMac.equals(dev.bleAddress, ignoreCase = true) ||
                (dev.classicMac != null && connectedClassicMac.equals(dev.classicMac, ignoreCase = true))
            )
        ) return true
        return false
    }

    // Deduplicate and group by canonical MAC or normalized name
    val displayDevices = remember(devices, connectedAddress, connectedClassicMac, isStateConnected) {
        devices
            .filter { com.aegissoft.qcybutmod.core.bluetooth.BleAdvertisementParser.isQcyDeviceName(it.deviceName) }
            .groupBy { (it.classicMac ?: it.bleAddress).uppercase() }
            .values
            .map { group ->
                group.maxByOrNull {
                    (if (isDeviceConnected(it)) 4000 else 0) +
                    (if (it.isReachable) 2000 else 0) +
                    (if (it.isBonded) 1000 else 0) +
                    (if (it.battery.hasValidBattery) 500 else 0) +
                    it.rssi
                } ?: group.first()
            }
            .sortedWith(
                compareByDescending<DiscoveredQcyDevice> { isDeviceConnected(it) }
                    .thenByDescending { it.isReachable }
                    .thenByDescending { it.isBonded }
                    .thenBy { it.deviceName }
            )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scanRotate")
    val scanRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanRotateVal"
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = DarkSurface,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp),
                shape = CircleShape,
                color = DarkBorder
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(theme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Headphones,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Kulaklık ve Cihazlar",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        val reachableCount = displayDevices.count { it.isReachable || isDeviceConnected(it) }
                        Text(
                            text = if (isScanning) "Çevredeki açık kulaklıklar taranıyor..."
                            else "${displayDevices.size} kayıtlı aygıt • $reachableCount aktif/erişilebilir",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Kapat",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Device List
            if (displayDevices.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurfaceVariant,
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BluetoothSearching,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier
                                .size(42.dp)
                                .then(if (isScanning) Modifier.rotate(scanRotation) else Modifier)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isScanning) "QCY Kulaklıkları Taranıyor..." else "Kayıtlı QCY Kulaklık Bulunamadı",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Kulaklığınızın kutusunu açın veya telefonunuzun Bluetooth ayarlarından eşleyin.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayDevices, key = { (it.classicMac ?: it.bleAddress) + it.deviceName }) { dev ->
                        val isConnected = isDeviceConnected(dev)
                        val isThisDeviceConnecting = isConnecting && connectingAddress.isNotEmpty() && (
                            connectingAddress.equals(dev.bleAddress, ignoreCase = true) ||
                            (dev.classicMac != null && connectingAddress.equals(dev.classicMac, ignoreCase = true))
                        )
                        val isOnline = isConnected || dev.isReachable || dev.isAudioConnected

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (!isConnected && !isThisDeviceConnecting) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSelectDevice(dev)
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = DarkSurfaceVariant,
                            border = BorderStroke(
                                width = 1.dp,
                                color = when {
                                    isConnected -> theme.primary.copy(alpha = 0.7f)
                                    isOnline -> SuccessGreen.copy(alpha = 0.35f)
                                    else -> DarkBorder
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isConnected -> theme.primary.copy(alpha = 0.2f)
                                                    isOnline -> SuccessGreen.copy(alpha = 0.12f)
                                                    else -> DarkSurface
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isOnline) Icons.Rounded.Headphones else Icons.Rounded.BluetoothDisabled,
                                            contentDescription = null,
                                            tint = when {
                                                isConnected -> theme.primary
                                                isOnline -> SuccessGreen
                                                else -> TextTertiary
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dev.deviceName,
                                            color = if (isOnline) TextPrimary else TextSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val statusText = when {
                                            isConnected -> "Bağlı • Aktif Kontrol"
                                            isThisDeviceConnecting -> "Erişim kontrol ediliyor & bağlanıyor..."
                                            isOnline -> {
                                                val rssiPart = if (dev.rssi in -99..-15) " • ${dev.rssi} dBm" else ""
                                                "Açık • Bağlanabilir$rssiPart"
                                            }
                                            dev.isBonded -> "Kayıtlı • Kutu Kapalı / Menzil Dışı"
                                            else -> "Bulundu"
                                        }
                                        Text(
                                            text = statusText,
                                            color = when {
                                                isConnected -> SuccessGreen
                                                isThisDeviceConnecting -> theme.primary
                                                isOnline -> SuccessGreen
                                                else -> TextTertiary
                                            },
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )

                                        // Only display real battery when the device is actually reachable/connected
                                        if (isOnline && dev.battery.hasValidBattery) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (dev.battery.leftLevel in 0..100) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = theme.primary.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = "L: %${dev.battery.leftLevel}",
                                                            color = theme.primary,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                if (dev.battery.rightLevel in 0..100) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = theme.primary.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = "R: %${dev.battery.rightLevel}",
                                                            color = theme.primary,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                if (dev.battery.caseLevel in 0..100) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = DarkSurface
                                                    ) {
                                                        Text(
                                                            text = "Kutu: %${dev.battery.caseLevel}",
                                                            color = TextSecondary,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                when {
                                    isConnected -> {
                                        OutlinedButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onDisconnectDevice()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.6f)),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = theme.primary.copy(alpha = 0.12f)
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(SuccessGreen)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Bağlı",
                                                color = theme.primary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    isThisDeviceConnecting -> {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = theme.primary.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(12.dp),
                                                    strokeWidth = 2.dp,
                                                    color = theme.primary
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Bağlanıyor...",
                                                    color = theme.primary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    isOnline -> {
                                        Button(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onSelectDevice(dev)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "Bağlan",
                                                color = OledBlack,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    else -> {
                                        OutlinedButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onSelectDevice(dev)
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, DarkBorder),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "Kapalı / Ara",
                                                color = TextTertiary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Bottom Scan Trigger Button (STRICTLY scans only, never auto-connects)
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isScanning) onStopScan() else onStartScan()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) DarkSurfaceVariant else theme.primary
                ),
                border = if (isScanning) BorderStroke(1.dp, theme.primary.copy(alpha = 0.5f)) else null
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Rounded.BluetoothSearching else Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = if (isScanning) theme.primary else OledBlack,
                    modifier = Modifier
                        .size(18.dp)
                        .then(if (isScanning) Modifier.rotate(scanRotation) else Modifier)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isScanning) "Taramayı Durdur" else "Yeni Cihazları Tara",
                    color = if (isScanning) theme.primary else OledBlack,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
