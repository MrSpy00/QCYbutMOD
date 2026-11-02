package com.aegissoft.qcybutmod.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aegissoft.qcybutmod.core.audio.VoiceSettings
import com.aegissoft.qcybutmod.core.translate.*
import com.aegissoft.qcybutmod.ui.components.AegisCard
import com.aegissoft.qcybutmod.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTranslateScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val session = remember { TranslateSession(context) }
    val uiState by session.uiState.collectAsState()
    val theme = LocalAppThemeColor.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var showLanguagePickerFor: String? by remember { mutableStateOf(null) } // "source" or "target"
    var showEngineDialog by remember { mutableStateOf(false) }
    var showManualInputDialog by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf("") }
    var geminiApiKeyInput by remember { mutableStateOf(GeminiLiveEngine.getApiKey(context)) }

    // Çeviri günlüğü geçmişi (History sheet) durumu
    var showHistorySheet by remember { mutableStateOf(false) }
    var historyQuery by remember { mutableStateOf("") }
    var historySessions by remember { mutableStateOf<List<JournalSession>>(emptyList()) }
    var historyResults by remember { mutableStateOf<List<Pair<JournalSession, TranscriptItem>>>(emptyList()) }

    // Newest transcript renders at the top - scroll back to top when a new one arrives
    LaunchedEffect(uiState.transcriptItems.size, uiState.currentPartialOriginal) {
        if (uiState.transcriptItems.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // Geçmiş sheet açıkken oturumları veya arama sonuçlarını yükle
    LaunchedEffect(showHistorySheet, historyQuery) {
        if (!showHistorySheet) return@LaunchedEffect
        if (historyQuery.isBlank()) {
            historyResults = emptyList()
            historySessions = session.journal.load()
        } else {
            historySessions = emptyList()
            historyResults = session.journal.search(historyQuery)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            session.saveToJournalAsync()
            session.destroy()
        }
    }

    Scaffold(
        containerColor = OledBlack,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OledBlack)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            session.stopListening()
                            onBack()
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceVariant)
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "Geri",
                            tint = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Canlı Çeviri",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Kulaklık Çift Dilli Sesli Asistan",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = { showHistorySheet = true },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceVariant)
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = "Geçmiş",
                            tint = TextSecondary
                        )
                    }

                    IconButton(
                        onClick = { showEngineDialog = true },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceVariant)
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Çeviri Motoru",
                            tint = theme.primaryColor
                        )
                    }
                }
            }
        },
        bottomBar = {
            TranslateBottomBar(
                isListening = uiState.isListening,
                audioRms = uiState.audioRms,
                theme = theme,
                onToggleListening = { session.toggleListening() },
                onClearTranscripts = {
                    // Temizlemeden ÖNCE günlüğe kaydet (sıra: kayıt → temizle)
                    scope.launch {
                        session.saveToJournal()
                        session.clearTranscripts()
                    }
                },
                onOpenManualInput = { showManualInputDialog = true }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
        ) {
            // Language Selection Card
            item {
                LanguageSelectionCard(
                    sourceLang = uiState.sourceLang,
                    targetLang = uiState.targetLang,
                    theme = theme,
                    onSelectSource = { showLanguagePickerFor = "source" },
                    onSelectTarget = { showLanguagePickerFor = "target" },
                    onSwap = { session.swapLanguages() }
                )
            }

            // Quick Configuration Chips Row
            item {
                QuickControlsRow(
                    micSource = uiState.micSource,
                    ttsRoute = uiState.ttsRoute,
                    engineType = uiState.engineType,
                    theme = theme,
                    onToggleMic = {
                        val next = if (uiState.micSource == MicSource.PHONE_MIC) {
                            MicSource.HEADSET_MIC
                        } else {
                            MicSource.PHONE_MIC
                        }
                        session.setMicSource(next)
                    },
                    onToggleTts = {
                        session.cycleTranslateTtsRoute()
                    },
                    onOpenEngineDialog = { showEngineDialog = true }
                )
            }

            // Engine / Preparation Status Alert
            if (uiState.prepareState is PrepareState.Downloading) {
                item {
                    AegisCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = theme.primaryColor,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Çevrimdışı dil paketi indiriliyor...",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Error Banner
            uiState.errorMessage?.let { error ->
                item {
                    AegisCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = DangerRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = error,
                                color = DangerRed,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Empty State Placeholder
            if (uiState.transcriptItems.isEmpty() && uiState.currentPartialOriginal.isBlank()) {
                item {
                    EmptyTranscriptPlaceholder(theme = theme, isListening = uiState.isListening)
                }
            }

            // Live partial speech - sits above history so the newest content is always at the top
            if (uiState.currentPartialOriginal.isNotBlank()) {
                item {
                    PartialSpeechBubble(
                        text = uiState.currentPartialOriginal,
                        sourceLang = uiState.sourceLang,
                        theme = theme
                    )
                }
            }

            // Transcripts History - newest first (top), oldest at the bottom
            items(uiState.transcriptItems.asReversed(), key = { it.id }) { item ->
                TranscriptBubbleCard(
                    item = item,
                    theme = theme,
                    transcriptOrder = uiState.transcriptOrder,
                    transcriptRotate = uiState.transcriptRotate,
                    onReplay = { session.replaySpeech(item) },
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Çeviri", item.translatedText))
                        Toast.makeText(context, "Çeviri kopyalandı", Toast.LENGTH_SHORT).show()
                    },
                    onDelete = { session.deleteTranscript(item.id) }
                )
            }
        }
    }

    // Language Picker Dialog
    if (showLanguagePickerFor != null) {
        val isSource = showLanguagePickerFor == "source"
        val currentSelected = if (isSource) uiState.sourceLang else uiState.targetLang

        LanguagePickerDialog(
            title = if (isSource) "Konuşulan Dil (Girdi)" else "Çevrilecek Hedef Dil",
            currentSelected = currentSelected,
            theme = theme,
            onDismiss = { showLanguagePickerFor = null },
            onSelect = { selected ->
                if (isSource) {
                    session.setSourceLang(selected)
                } else {
                    session.setTargetLang(selected)
                }
                showLanguagePickerFor = null
            }
        )
    }

    // Engine Selection & Gemini Settings Dialog
    if (showEngineDialog) {
        EngineSettingsDialog(
            currentEngine = uiState.engineType,
            apiKey = geminiApiKeyInput,
            theme = theme,
            transcriptOrder = uiState.transcriptOrder,
            transcriptRotate = uiState.transcriptRotate,
            onTranscriptOrder = { session.setTranscriptOrder(it) },
            onTranscriptRotate = { session.setTranscriptRotate(it) },
            onDismiss = { showEngineDialog = false },
            onSave = { engine, key ->
                session.setEngineType(engine)
                geminiApiKeyInput = key
                GeminiLiveEngine.setApiKey(context, key)
                showEngineDialog = false
            }
        )
    }

    // Manual Text Input Dialog
    if (showManualInputDialog) {
        ManualTextInputDialog(
            text = manualText,
            theme = theme,
            onTextChange = { manualText = it },
            onDismiss = { showManualInputDialog = false },
            onSend = {
                if (manualText.isNotBlank()) {
                    session.processSpeechInput(manualText)
                    manualText = ""
                    showManualInputDialog = false
                }
            }
        )
    }

    // Çeviri Günlüğü Geçmişi (History sheet)
    if (showHistorySheet) {
        val displayEntries: List<Pair<JournalSession, List<TranscriptItem>>> =
            if (historyQuery.isBlank()) {
                historySessions.map { it to it.items }
            } else {
                historyResults.groupBy({ it.first }, { it.second })
                    .map { (journalSession, matched) -> journalSession to matched }
            }

        TranslateHistorySheet(
            entries = displayEntries,
            query = historyQuery,
            theme = theme,
            onQueryChange = { historyQuery = it },
            onDismiss = { showHistorySheet = false },
            onReplay = { session.replaySpeech(it) },
            onDeleteSession = { sessionId ->
                scope.launch {
                    session.journal.deleteSession(sessionId)
                    if (historyQuery.isBlank()) {
                        historySessions = session.journal.load()
                    } else {
                        historyResults = session.journal.search(historyQuery)
                    }
                }
            }
        )
    }
}

@Composable
private fun LanguageSelectionCard(
    sourceLang: SupportedLanguage,
    targetLang: SupportedLanguage,
    theme: AppThemeColor,
    onSelectSource: () -> Unit,
    onSelectTarget: () -> Unit,
    onSwap: () -> Unit
) {
    AegisCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Source Language Pill
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant)
                    .clickable { onSelectSource() }
                    .padding(vertical = 12.dp, horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = sourceLang.flag, fontSize = 24.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = sourceLang.nameTr,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Girdi Dili",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }

            // Swap Button
            IconButton(
                onClick = onSwap,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clip(CircleShape)
                    .background(theme.glow)
                    .size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapHoriz,
                    contentDescription = "Dilleri Değiştir",
                    tint = theme.primaryColor
                )
            }

            // Target Language Pill
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant)
                    .clickable { onSelectTarget() }
                    .padding(vertical = 12.dp, horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = targetLang.flag, fontSize = 24.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = targetLang.nameTr,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Hedef Dil",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun QuickControlsRow(
    micSource: MicSource,
    ttsRoute: String,
    engineType: EngineType,
    theme: AppThemeColor,
    onToggleMic: () -> Unit,
    onToggleTts: () -> Unit,
    onOpenEngineDialog: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Mic Source Chip
        FilterChip(
            selected = micSource == MicSource.HEADSET_MIC,
            onClick = onToggleMic,
            label = {
                Text(
                    text = if (micSource == MicSource.HEADSET_MIC) "Kulaklık Mikrofonu" else "Telefon Mikrofonu",
                    fontSize = 11.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (micSource == MicSource.HEADSET_MIC) Icons.Rounded.HeadsetMic else Icons.Rounded.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = theme.glow,
                selectedLabelColor = theme.primaryColor,
                selectedLeadingIconColor = theme.primaryColor,
                containerColor = DarkSurfaceVariant,
                labelColor = TextSecondary,
                iconColor = TextSecondary
            ),
            border = BorderStroke(1.dp, if (micSource == MicSource.HEADSET_MIC) theme.primaryColor.copy(alpha = 0.5f) else DarkBorder),
            modifier = Modifier.weight(1f)
        )

        // Translation TTS route chip: cycles headset -> phone -> off
        val ttsOn = ttsRoute != com.aegissoft.qcybutmod.core.audio.VoiceSettings.ROUTE_OFF
        FilterChip(
            selected = ttsOn,
            onClick = onToggleTts,
            label = {
                Text(
                    text = when (ttsRoute) {
                        com.aegissoft.qcybutmod.core.audio.VoiceSettings.ROUTE_PHONE -> "Telefonda Oku"
                        com.aegissoft.qcybutmod.core.audio.VoiceSettings.ROUTE_OFF -> "Ses Kapalı"
                        else -> "Kulaklığa Oku"
                    },
                    fontSize = 11.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = when {
                        ttsRoute == com.aegissoft.qcybutmod.core.audio.VoiceSettings.ROUTE_PHONE -> Icons.Rounded.SpeakerPhone
                        ttsOn -> Icons.Rounded.VolumeUp
                        else -> Icons.Rounded.VolumeOff
                    },
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = SuccessGreen.copy(alpha = 0.15f),
                selectedLabelColor = SuccessGreen,
                selectedLeadingIconColor = SuccessGreen,
                containerColor = DarkSurfaceVariant,
                labelColor = TextSecondary,
                iconColor = TextSecondary
            ),
            border = BorderStroke(1.dp, if (ttsOn) SuccessGreen.copy(alpha = 0.4f) else DarkBorder),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EmptyTranscriptPlaceholder(theme: AppThemeColor, isListening: Boolean) {
    AegisCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(theme.glow),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Translate,
                    contentDescription = null,
                    tint = theme.primaryColor,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isListening) "Dinleniyor..." else "Konuşmayı Başlatın",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Aşağıdaki mikrofona dokunarak konuşmaya başlayın. Söylenenler anlık olarak çevrilir ve kulaklığınıza sesli aktarılır.",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun TranscriptBubbleCard(
    item: TranscriptItem,
    theme: AppThemeColor,
    transcriptOrder: String,
    transcriptRotate: Boolean,
    onReplay: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(item.timestamp) { timeFormat.format(Date(item.timestamp)) }

    AegisCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Facing mode: 180 deg rotation so the person opposite the phone
                // holder reads the transcript right-side up.
                .rotate(if (transcriptRotate) 180f else 0f)
                .padding(14.dp)
        ) {
            // Header: Time and Mic source
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (item.isFromHeadsetMic) Icons.Rounded.HeadsetMic else Icons.Rounded.PhoneAndroid,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (item.isFromHeadsetMic) "Kulaklık Mikrofonu" else "Telefon Mikrofonu",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = timeStr,
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Order is user-configurable (Ayarlar dialog -> Gorunum)
            val targetFirst = transcriptOrder == VoiceSettings.ORDER_TARGET_FIRST

            val sourceBlock: @Composable () -> Unit = {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = item.sourceLang.flag, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.originalText,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val dividerBlock: @Composable () -> Unit = {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(10.dp))
            }

            val targetBlock: @Composable () -> Unit = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(text = item.targetLang.flag, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.translatedText,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp,
                        modifier = Modifier.weight(1f)
                    )

                    // Actions: Replay, Copy & Delete
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onReplay,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VolumeUp,
                                contentDescription = "Seslendir",
                                tint = theme.primaryColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Kopyala",
                                tint = TextTertiary,
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Mesajı Sil",
                                tint = DangerRed,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            if (targetFirst) {
                targetBlock()
                dividerBlock()
                sourceBlock()
            } else {
                sourceBlock()
                dividerBlock()
                targetBlock()
            }
        }
    }
}

@Composable
private fun PartialSpeechBubble(
    text: String,
    sourceLang: SupportedLanguage,
    theme: AppThemeColor
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    AegisCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(theme.primaryColor.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = sourceLang.flag, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = TextPrimary.copy(alpha = 0.85f),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TranslateBottomBar(
    isListening: Boolean,
    audioRms: Float,
    theme: AppThemeColor,
    onToggleListening: () -> Unit,
    onClearTranscripts: () -> Unit,
    onOpenManualInput: () -> Unit
) {
    val pulseScale by animateFloatAsState(
        targetValue = if (isListening) 1f + (audioRms * 0.25f).coerceIn(0f, 0.3f) else 1f,
        label = "rmsScale"
    )

    Surface(
        color = DarkSurface,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .border(BorderStroke(1.dp, DarkBorder), shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Manual Text Input Button
            IconButton(
                onClick = onOpenManualInput,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant)
                    .size(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Metin Yaz",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Big Action Mic Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(72.dp)
            ) {
                if (isListening) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(DangerRed.copy(alpha = 0.25f))
                    )
                }

                FloatingActionButton(
                    onClick = onToggleListening,
                    containerColor = if (isListening) DangerRed else theme.primaryColor,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = if (isListening) "Durdur" else "Dinle",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Clear Transcripts Button
            IconButton(
                onClick = onClearTranscripts,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant)
                    .size(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Temizle",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerDialog(
    title: String,
    currentSelected: SupportedLanguage,
    theme: AppThemeColor,
    onDismiss: () -> Unit,
    onSelect: (SupportedLanguage) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val favorites = remember { LanguageCatalog.getFavoriteLanguages(context) }

    val filteredLanguages = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            LanguageCatalog.ALL_LANGUAGES
        } else {
            LanguageCatalog.ALL_LANGUAGES.filter {
                it.nameTr.contains(searchQuery, ignoreCase = true) ||
                it.nativeName.contains(searchQuery, ignoreCase = true) ||
                it.code.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Kapat",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Dil ara...", color = TextTertiary, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Sil", tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.primaryColor,
                        unfocusedBorderColor = DarkBorder,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Favorites Row
                Text(
                    text = "Sık Kullanılanlar",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(favorites) { fav ->
                        SuggestionChip(
                            onClick = { onSelect(fav) },
                            label = { Text("${fav.flag} ${fav.nameTr}", fontSize = 12.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (fav.code == currentSelected.code) theme.glow else DarkSurfaceVariant,
                                labelColor = if (fav.code == currentSelected.code) theme.primaryColor else TextPrimary
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (fav.code == currentSelected.code) theme.primaryColor else DarkBorder
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // All Languages List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredLanguages, key = { it.code }) { lang ->
                        val isSelected = lang.code == currentSelected.code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) theme.glow else Color.Transparent)
                                .clickable { onSelect(lang) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = lang.flag, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lang.nameTr,
                                    color = if (isSelected) theme.primaryColor else TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = "${lang.nativeName} (${lang.code})",
                                    color = TextTertiary,
                                    fontSize = 11.sp
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = theme.primaryColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Compact single-select chip used in the engine dialog's Görünüm section. */
@Composable
private fun LayoutChoiceChip(
    label: String,
    selected: Boolean,
    theme: AppThemeColor,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) theme.glow else DarkSurfaceVariant)
            .border(
                BorderStroke(1.dp, if (selected) theme.primaryColor else DarkBorder),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) theme.primaryColor else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EngineSettingsDialog(
    currentEngine: EngineType,
    apiKey: String,
    theme: AppThemeColor,
    transcriptOrder: String,
    transcriptRotate: Boolean,
    onTranscriptOrder: (String) -> Unit,
    onTranscriptRotate: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (EngineType, String) -> Unit
) {
    var selectedEngine by remember { mutableStateOf(currentEngine) }
    var keyInput by remember { mutableStateOf(apiKey) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Çeviri Motoru & Ayarlar",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Engine Option 1: ML Kit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedEngine == EngineType.OFFLINE_MLKIT) theme.glow else DarkSurfaceVariant)
                        .border(
                            BorderStroke(
                                1.dp,
                                if (selectedEngine == EngineType.OFFLINE_MLKIT) theme.primaryColor else DarkBorder
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedEngine = EngineType.OFFLINE_MLKIT }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedEngine == EngineType.OFFLINE_MLKIT,
                        onClick = { selectedEngine = EngineType.OFFLINE_MLKIT },
                        colors = RadioButtonDefaults.colors(selectedColor = theme.primaryColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = EngineType.OFFLINE_MLKIT.title,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = EngineType.OFFLINE_MLKIT.subtitle,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Engine Option 2: Gemini Live Cloud
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedEngine == EngineType.GEMINI_CLOUD) theme.glow else DarkSurfaceVariant)
                        .border(
                            BorderStroke(
                                1.dp,
                                if (selectedEngine == EngineType.GEMINI_CLOUD) theme.primaryColor else DarkBorder
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedEngine = EngineType.GEMINI_CLOUD }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedEngine == EngineType.GEMINI_CLOUD,
                        onClick = { selectedEngine = EngineType.GEMINI_CLOUD },
                        colors = RadioButtonDefaults.colors(selectedColor = theme.primaryColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = EngineType.GEMINI_CLOUD.title,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = EngineType.GEMINI_CLOUD.subtitle,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                if (selectedEngine == EngineType.GEMINI_CLOUD) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Gemini API Anahtarı", fontSize = 12.sp) },
                        placeholder = { Text("AIzaSy...", color = TextTertiary, fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.primaryColor,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // --- Gorunum: transcript order & facing rotation (persists immediately) ---
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Görünüm",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Metin sırası", color = TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LayoutChoiceChip(
                        label = "Kaynak Üstte",
                        selected = transcriptOrder == VoiceSettings.ORDER_SOURCE_FIRST,
                        theme = theme,
                        modifier = Modifier.weight(1f),
                        onClick = { onTranscriptOrder(VoiceSettings.ORDER_SOURCE_FIRST) }
                    )
                    LayoutChoiceChip(
                        label = "Çeviri Üstte",
                        selected = transcriptOrder == VoiceSettings.ORDER_TARGET_FIRST,
                        theme = theme,
                        modifier = Modifier.weight(1f),
                        onClick = { onTranscriptOrder(VoiceSettings.ORDER_TARGET_FIRST) }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "Yön (karşı taraf için)", color = TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LayoutChoiceChip(
                        label = "Normal",
                        selected = !transcriptRotate,
                        theme = theme,
                        modifier = Modifier.weight(1f),
                        onClick = { onTranscriptRotate(false) }
                    )
                    LayoutChoiceChip(
                        label = "Ters (karşı taraf)",
                        selected = transcriptRotate,
                        theme = theme,
                        modifier = Modifier.weight(1f),
                        onClick = { onTranscriptRotate(true) }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("İptal", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(selectedEngine, keyInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor)
                    ) {
                        Text("Kaydet", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualTextInputDialog(
    text: String,
    theme: AppThemeColor,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Metin Çevir & Seslendir",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text("Çevrilecek cümleyi buraya yazın...", color = TextTertiary, fontSize = 13.sp) },
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.primaryColor,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("İptal", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSend,
                        enabled = text.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primaryColor)
                    ) {
                        Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Çevir & Oku", color = Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslateHistorySheet(
    entries: List<Pair<JournalSession, List<TranscriptItem>>>,
    query: String,
    theme: AppThemeColor,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onReplay: (TranscriptItem) -> Unit,
    onDeleteSession: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Çeviri Geçmişi",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Kapat",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Arama alanı — dil seçim sheet'indeki arama deseninin aynısı
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Geçmişte ara...", color = TextTertiary, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Aramayı Temizle", tint = TextSecondary)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.primaryColor,
                    unfocusedBorderColor = DarkBorder,
                    focusedContainerColor = DarkSurfaceVariant,
                    unfocusedContainerColor = DarkSurfaceVariant,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (entries.isEmpty()) {
                    item {
                        Text(
                            text = if (query.isBlank()) "Henüz kayıtlı çeviri yok" else "Sonuç bulunamadı",
                            color = TextTertiary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(entries) { (journalSession, sessionItems) ->
                        HistorySessionCard(
                            journalSession = journalSession,
                            items = sessionItems,
                            dateFormat = dateFormat,
                            theme = theme,
                            onReplay = onReplay,
                            onDelete = { onDeleteSession(journalSession.sessionId) }
                        )
                    }
                }
            }
        }
    }
}

/** Geçmiş sheet içinde bir oturum kartı: tarih + öğe sayısı başlığı, önizleme satırları ve oturum silme. */
@Composable
private fun HistorySessionCard(
    journalSession: JournalSession,
    items: List<TranscriptItem>,
    dateFormat: SimpleDateFormat,
    theme: AppThemeColor,
    onReplay: (TranscriptItem) -> Unit,
    onDelete: () -> Unit
) {
    AegisCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${dateFormat.format(Date(journalSession.startedAt))} · ${items.size} kayıt",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Oturumu Sil",
                        tint = DangerRed,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            items.forEachIndexed { index, item ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.originalText,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.translatedText,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { onReplay(item) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VolumeUp,
                            contentDescription = "Seslendir",
                            tint = theme.primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
