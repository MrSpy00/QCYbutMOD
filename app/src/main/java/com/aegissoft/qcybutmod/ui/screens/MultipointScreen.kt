package com.aegissoft.qcybutmod.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegissoft.qcybutmod.core.bluetooth.BleConnectionManager
import com.aegissoft.qcybutmod.core.bluetooth.MultipointQueryStatus
import com.aegissoft.qcybutmod.core.models.ProductDatabase
import com.aegissoft.qcybutmod.ui.components.AegisCard
import com.aegissoft.qcybutmod.ui.theme.*

@Composable
fun MultipointScreen(
    bleManager: BleConnectionManager,
    onBack: () -> Unit
) {
    val state by bleManager.deviceState.collectAsState()
    val theme = LocalAppThemeColor.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val queryStatus by bleManager.multipointQueryStatus.collectAsState()
    val localBonded = remember(state.isConnected) { bleManager.localBondedDevices() }
    val hasMultipoint = remember(state.vendorId, state.modelUiVersion) {
        ProductDatabase.getCapabilities(state.vendorId).hasMultipoint
    }

    LaunchedEffect(state.isConnected) {
        if (state.isConnected) {
            bleManager.refreshMultipointDevices()
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
                        text = "Çift Cihaz Bağlantısı",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Eşzamanlı iki ses kaynağı (Multipoint • 0x24 & 0x4D)",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Master Switch Card bound directly to live hardware state.isMultipointOn
        item {
            AegisCard(
                modifier = Modifier.fillMaxWidth(),
                hasGlow = state.isMultipointOn && state.isConnected
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Çift Cihaz Modu (Multipoint)",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Kulaklık aynı anda telefonunuza ve ikinci bir cihaza bağlı kalır; aktif ses kaynağına otomatik geçiş yapar.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = state.isMultipointOn,
                        enabled = state.isConnected && hasMultipoint,
                        onCheckedChange = { enabled ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            bleManager.setMultipoint(enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OledBlack,
                            checkedTrackColor = theme.primary
                        )
                    )
                }
            }
        }

        // Yetenek kapısı: model multipoint desteklemiyorsa açıklayıcı kart
        if (!hasMultipoint) {
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
                            text = "Bu model çift cihaz bağlantı desteklemiyor",
                            color = WarningAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Bu kulaklık modelinde çift cihaz (multipoint) bağlantısı donanım tarafından desteklenmiyor.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Active Hardware Devices from Opcode 77 (0x4D) & 79 (0x4F)
        item {
            AegisCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Donanımda Eşleşmiş Cihazlar",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (state.isConnected) "${state.pairedPhones.count { it.isConnected }} bağlı • ${state.pairedPhones.size} kayıtlı kaynak"
                            else "Kulaklık bağlı değil",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            bleManager.refreshMultipointDevices()
                        },
                        enabled = state.isConnected,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Yenile",
                            tint = if (state.isConnected) theme.primary else TextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (!state.isConnected) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BluetoothDisabled,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Bağlı cihaz listesini görmek için önce kulaklığı bağlayın.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                } else if (state.pairedPhones.isEmpty()) {
                    when (queryStatus) {
                        MultipointQueryStatus.WAITING -> Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Yanıt bekleniyor…",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Kulaklık 0x4D cihaz listesine yanıt aranıyor.",
                                color = TextTertiary,
                                fontSize = 11.sp
                            )
                        }
                        MultipointQueryStatus.NO_RESPONSE -> Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.BluetoothDisabled,
                                    contentDescription = null,
                                    tint = WarningAmber,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Kulaklık listeden yanıt vermedi",
                                    color = WarningAmber,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Cihaz 0x4D sorgusuna yanıt yazmadı (liste uydurulmaz, yalnızca gerçek yanıt gösterilir). Deneyin:",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "1) Kulaklığı kılıfından çıkarıp yeniden bağlayın.\n2) Çift cihaz modunu kapatıp tekrar açın.\n3) İkinci cihazı kulaklığa yakın tutup listeyi yenileyin.",
                                color = TextTertiary,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        bleManager.refreshMultipointDevices()
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        tint = theme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tekrar Dene", color = theme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            val send = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "QCYbutMOD multipoint tanı")
                                                putExtra(Intent.EXTRA_TEXT, bleManager.multipointDiagnosticsText())
                                            }
                                            context.startActivity(Intent.createChooser(send, "Tanı kaydını paylaş"))
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tanı Kaydı", color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                        MultipointQueryStatus.ANSWERED -> Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DevicesOther,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Kulaklıkta kayıtlı ikinci cihaz yok",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Donanım boş liste yanıtı verdi (gerçek durum).",
                                color = TextTertiary,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    bleManager.refreshMultipointDevices()
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Yenile",
                                    color = theme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        else -> Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DevicesOther,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Eşleşen telefon bulunamadı",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    bleManager.refreshMultipointDevices()
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Yenile",
                                    color = theme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.pairedPhones.forEach { phone ->
                            val isLocalPrimary = phone.mac.startsWith("Bu Cihaz")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkSurfaceVariant)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val isComputer = phone.name.contains("PC", ignoreCase = true) ||
                                            phone.name.contains("Laptop", ignoreCase = true) ||
                                            phone.name.contains("Mac", ignoreCase = true) ||
                                            phone.name.contains("Windows", ignoreCase = true) ||
                                            phone.name.contains("Desktop", ignoreCase = true)
                                    Icon(
                                        imageVector = if (isComputer) Icons.Rounded.Laptop else Icons.Rounded.Smartphone,
                                        contentDescription = null,
                                        tint = when {
                                            phone.isAudioActive -> SuccessGreen
                                            phone.isConnected -> theme.primary
                                            else -> TextSecondary
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = phone.name,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                        val subLabel = when {
                                            phone.isAudioActive -> "${phone.mac} • Aktif Ses Çıkışı"
                                            phone.isConnected -> "${phone.mac} • Bağlı (Beklemede)"
                                            else -> "${phone.mac} • Kayıtlı"
                                        }
                                        Text(
                                            text = subLabel,
                                            color = if (phone.isAudioActive) SuccessGreen else TextTertiary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                if (!isLocalPrimary && phone.mac.contains(":")) {
                                    OutlinedButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            bleManager.setPhoneBtAction(!phone.isConnected, phone.mac, phone.name)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (phone.isConnected) theme.primary.copy(alpha = 0.6f) else DarkBorder
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(
                                            text = if (phone.isConnected) "Bağlantıyı Kes" else "Bağlan",
                                            color = if (phone.isConnected) theme.primary else TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (phone.isAudioActive) SuccessGreen.copy(alpha = 0.15f)
                                        else if (phone.isConnected) theme.primary.copy(alpha = 0.15f)
                                        else DarkBorder.copy(alpha = 0.3f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (phone.isAudioActive) SuccessGreen.copy(alpha = 0.5f)
                                            else if (phone.isConnected) theme.primary.copy(alpha = 0.5f)
                                            else DarkBorder
                                        )
                                    ) {
                                        Text(
                                            text = if (phone.isAudioActive) "Aktif Ses" else if (phone.isConnected) "Bağlı" else "Kayıtlı",
                                            color = if (phone.isAudioActive) SuccessGreen else if (phone.isConnected) theme.primary else TextTertiary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Yerel telefon eşleşmeleri — kulaklığın kendi multipoint listesi değildir
        if (state.isConnected && localBonded.isNotEmpty()) {
            item {
                AegisCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = DarkSurfaceVariant
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.PhoneAndroid,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Bu Telefonun Eşleşmeleri (yerel)",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Kulaklığın çoklu bağlantı listesi değildir; yalnızca bu cihazda kayıtlı eşleşmeler.",
                                color = TextTertiary,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    localBonded.forEach { (name, mac) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = mac,
                                color = TextTertiary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        // Note Card regarding LDAC + Multipoint
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
                        text = "LDAC ve Çift Cihaz Uyumluluğu",
                        color = WarningAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Donanım bant genişliği sınırı nedeniyle LDAC açıkken çift cihaz bağlantısı AAC codec'ine düşebilir. En yüksek 990 kbps LDAC kalitesi için tek cihaz bağlantısı tavsiye edilir.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
