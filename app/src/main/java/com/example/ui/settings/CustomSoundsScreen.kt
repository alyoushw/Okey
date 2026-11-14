package com.example.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audio.AudioRecorderHelper
import com.example.audio.elevenlabs.ElevenLabsSettings
import com.example.data.model.CustomSoundSlot
import com.example.data.model.Player
import com.example.data.model.SoundSlotCategory
import com.example.data.model.SoundSourceType
import java.io.File

@Composable
fun CustomSoundsScreen(
    slots: List<CustomSoundSlot>,
    players: List<Player>,
    recorderHelper: AudioRecorderHelper,
    elevenLabsSettings: ElevenLabsSettings = ElevenLabsSettings(),
    onUpdateElevenLabsSettings: (ElevenLabsSettings) -> Unit = {},
    onTestElevenLabsVoice: (String) -> Unit = {},
    onGenerateSlotElevenLabs: (CustomSoundSlot) -> Unit = {},
    onPregenerateAllElevenLabs: () -> Unit = {},
    totalCharactersUsed: Long = 0L,
    onResetCharactersCounter: () -> Unit = {},
    isGeneratingElevenLabs: String? = null,
    elevenLabsMessage: String? = null,
    onClearElevenLabsMessage: () -> Unit = {},
    onSaveSlot: (CustomSoundSlot) -> Unit,
    onDeleteSlot: (String) -> Unit,
    onResetSlot: (String) -> Unit,
    onResetAllSlots: () -> Unit,
    onExportJson: () -> String,
    onImportJson: (String) -> Boolean,
    onTestSlot: (CustomSoundSlot) -> Unit,
    onRunFullTest: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var activeRecordingSlotId by remember { mutableStateOf<String?>(null) }
    var recordingElapsedMs by remember { mutableLongStateOf(0L) }
    var recordingAmplitude by remember { mutableIntStateOf(0) }

    var showAddSlotDialog by remember { mutableStateOf(false) }
    var showExportImportDialog by remember { mutableStateOf(false) }
    var showResetAllConfirmDialog by remember { mutableStateOf(false) }

    // Clean up when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            if (activeRecordingSlotId != null) {
                recorderHelper.stopRecording()
            }
            recorderHelper.stopPlayback()
        }
    }

    // Permission launcher for microphone recording
    var pendingRecordSlotId by remember { mutableStateOf<String?>(null) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val targetId = pendingRecordSlotId
            if (targetId != null) {
                activeRecordingSlotId = targetId
                recordingElapsedMs = 0L
                recorderHelper.startRecording(
                    slotId = targetId,
                    onTick = { ms, amp ->
                        recordingElapsedMs = ms
                        recordingAmplitude = amp
                    },
                    onComplete = { savedPath ->
                        val currentSlot = slots.find { it.id == targetId }
                        if (currentSlot != null) {
                            onSaveSlot(currentSlot.copy(recordingPath = savedPath))
                        }
                        activeRecordingSlotId = null
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                        activeRecordingSlotId = null
                    }
                )
            }
        } else {
            Toast.makeText(context, "يلزم إذن الميكروفون لتسجيل الصوت الحقيقي", Toast.LENGTH_LONG).show()
        }
        pendingRecordSlotId = null
    }

    fun requestAndStartRecording(slotId: String) {
        val permissionStatus = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
            activeRecordingSlotId = slotId
            recordingElapsedMs = 0L
            recorderHelper.startRecording(
                slotId = slotId,
                onTick = { ms, amp ->
                    recordingElapsedMs = ms
                    recordingAmplitude = amp
                },
                onComplete = { savedPath ->
                    val currentSlot = slots.find { it.id == slotId }
                    if (currentSlot != null) {
                        onSaveSlot(currentSlot.copy(recordingPath = savedPath))
                    }
                    activeRecordingSlotId = null
                },
                onError = { err ->
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    activeRecordingSlotId = null
                }
            )
        } else {
            pendingRecordSlotId = slotId
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
        ) {
            // Header with Back Button & Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("back_to_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "العودة للإعدادات"
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "الأصوات والعبارات المخصصة",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ارفع مقاطع صوتية جاهزة من جهازك، أو سجّل بصوتك، أو اكتب بالتشكيل",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action Bar: Test All, Add Custom, Backup/Restore, Reset All
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onRunFullTest,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("test_full_turn_btn")
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تجربة الدور كاملاً 🔊", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { showAddSlotDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("إضافة صوت", fontSize = 12.sp)
                }

                IconButton(
                    onClick = { showExportImportDialog = true },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = "تصدير أو استيراد", tint = MaterialTheme.colorScheme.primary)
                }

                IconButton(
                    onClick = { showResetAllConfirmDialog = true },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "استعادة كافة الافتراضيات", tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val allowedSlotIds = remember { setOf("turn_general", "countdown_5", "countdown_4", "countdown_3", "countdown_2", "countdown_1", "time_up") }
            val visibleSlots = remember(slots) {
                slots.filter { slot ->
                    slot.id in allowedSlotIds || slot.category == SoundSlotCategory.CUSTOM
                }
            }

            // Sound Slots List with ElevenLabs Master Configuration at top
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // 1. ElevenLabs Voice & Pre-generation Master Card
                item {
                    ElevenLabsMasterCard(
                        settings = elevenLabsSettings,
                        onUpdateSettings = onUpdateElevenLabsSettings,
                        onTestVoice = onTestElevenLabsVoice,
                        onPregenerateAll = onPregenerateAllElevenLabs,
                        totalCharactersUsed = totalCharactersUsed,
                        onResetCharacters = onResetCharactersCounter,
                        isGenerating = isGeneratingElevenLabs,
                        statusMessage = elevenLabsMessage,
                        onDismissMessage = onClearElevenLabsMessage
                    )
                }

                // 2. Connection Status & Diagnostic Card (ElevenLabs API status & log)
                item {
                    val coroutineScope = rememberCoroutineScope()
                    val elevenLabsService = remember { com.example.audio.elevenlabs.ElevenLabsService(context) }
                    var isTestingConn by remember { mutableStateOf(false) }

                    ConnectionStatusCard(
                        voiceId = elevenLabsSettings.voiceId,
                        onTestConnection = {
                            coroutineScope.launch {
                                isTestingConn = true
                                elevenLabsService.testConnection()
                                isTestingConn = false
                            }
                        },
                        isTestingConnection = isTestingConn
                    )
                }

                // 3. Individual Sound Slots
                items(visibleSlots, key = { it.id }) { slot ->
                    val slotReady = remember(slot, elevenLabsSettings) {
                        isSlotAudioReady(slot, elevenLabsSettings, context)
                    }
                    SoundSlotCard(
                        slot = slot,
                        isCurrentlyRecording = activeRecordingSlotId == slot.id,
                        recordingElapsedMs = if (activeRecordingSlotId == slot.id) recordingElapsedMs else 0L,
                        recordingAmplitude = if (activeRecordingSlotId == slot.id) recordingAmplitude else 0,
                        isGeneratingElevenLabs = isGeneratingElevenLabs == slot.id,
                        isReady = slotReady,
                        onStartRecording = { requestAndStartRecording(slot.id) },
                        onStopRecording = {
                            val savedPath = recorderHelper.stopRecording()
                            if (savedPath != null) {
                                onSaveSlot(slot.copy(recordingPath = savedPath))
                            }
                            activeRecordingSlotId = null
                        },
                        onCancelRecording = {
                            recorderHelper.cancelRecording()
                            activeRecordingSlotId = null
                        },
                        onPlayAudio = { path ->
                            recorderHelper.playAudio(path)
                        },
                        onDeleteRecording = { path ->
                            recorderHelper.deleteFile(path)
                            onSaveSlot(slot.copy(recordingPath = null))
                            Toast.makeText(context, "تم حذف التسجيل الصوتي", Toast.LENGTH_SHORT).show()
                        },
                        onUploadAudio = { uri ->
                            val imported = recorderHelper.importAudioFileFromUri(uri, slot.id)
                            if (imported != null) {
                                val (filePath, fileName) = imported
                                val updated = slot.copy(
                                    uploadedAudioPath = filePath,
                                    uploadedAudioName = fileName
                                )
                                onSaveSlot(updated)
                                Toast.makeText(context, "تم رفع الملف الصوتي: $fileName", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "فشل في قراءة الملف الصوتي", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDeleteUploaded = {
                            slot.uploadedAudioPath?.let { recorderHelper.deleteFile(it) }
                            onSaveSlot(slot.copy(uploadedAudioPath = null, uploadedAudioName = null))
                            Toast.makeText(context, "تم حذف الملف المرفوع", Toast.LENGTH_SHORT).show()
                        },
                        onGenerateElevenLabs = {
                            onGenerateSlotElevenLabs(slot)
                        },
                        onDeleteElevenLabs = {
                            slot.elevenLabsAudioPath?.let { recorderHelper.deleteFile(it) }
                            onSaveSlot(slot.copy(elevenLabsAudioPath = null))
                            Toast.makeText(context, "تم إزالة صوت ElevenLabs", Toast.LENGTH_SHORT).show()
                        },
                        onUpdateSlot = { updated ->
                            onSaveSlot(updated)
                        },
                        onTestSlot = {
                            onTestSlot(slot)
                        },
                        onDeleteSlot = if (slot.category == SoundSlotCategory.CUSTOM) {
                            {
                                slot.uploadedAudioPath?.let { recorderHelper.deleteFile(it) }
                                slot.recordingPath?.let { recorderHelper.deleteFile(it) }
                                slot.elevenLabsAudioPath?.let { recorderHelper.deleteFile(it) }
                                onDeleteSlot(slot.id)
                            }
                        } else null
                    )
                }
            }
        }
    }

    // Dialog: Add Custom Extra Sound Slot
    if (showAddSlotDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddSlotDialog = false },
            title = { Text("إضافة صوت أو عبارة جديدة", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("اسم الخانة (مثال: عبارة نصف الوقت أو الفوز)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newText,
                        onValueChange = { newText = it },
                        label = { Text("النص المشكّل") },
                        placeholder = { Text("مثال: اِنْتَصَفَ الوَقْتُ يَا أَبْطَال!") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            val customId = "custom_${System.currentTimeMillis()}"
                            val newSlot = CustomSoundSlot(
                                id = customId,
                                title = newTitle.trim(),
                                description = "عبارة مخصصة مضافة من المستخدم",
                                category = SoundSlotCategory.CUSTOM,
                                customText = newText.trim()
                            )
                            onSaveSlot(newSlot)
                            showAddSlotDialog = false
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSlotDialog = false }) { Text("إلغاء") }
            }
        )
    }

    // Dialog: Export & Import JSON
    if (showExportImportDialog) {
        var jsonInput by remember { mutableStateOf(onExportJson()) }
        AlertDialog(
            onDismissRequest = { showExportImportDialog = false },
            title = { Text("تصدير واستيراد الأصوات والعبارات", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "يمكنك نسخ إعداداتك لنقلها لجهاز آخر أو لصق إعدادات محفوظة:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = jsonInput,
                        onValueChange = { jsonInput = it },
                        label = { Text("بيانات الإعدادات (JSON)") },
                        maxLines = 8,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(jsonInput))
                                Toast.makeText(context, "تم نسخ البيانات إلى الحافظة", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("نسخ للكل")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (jsonInput.isNotBlank()) {
                            val ok = onImportJson(jsonInput)
                            if (ok) {
                                Toast.makeText(context, "تم استيراد الأصوات بنجاح", Toast.LENGTH_SHORT).show()
                                showExportImportDialog = false
                            } else {
                                Toast.makeText(context, "صيغة البيانات غير صحيحة", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("تطبيق واستيراد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportImportDialog = false }) { Text("إغلاق") }
            }
        )
    }

    // Dialog: Reset All Confirm
    if (showResetAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllConfirmDialog = false },
            title = { Text("استعادة كافة الأصوات الافتراضية؟", fontWeight = FontWeight.Bold) },
            text = { Text("سيتم مسح كافة التخصيصات والعودة إلى نصوص وأصوات التطبيق الأصلية.") },
            confirmButton = {
                Button(
                    onClick = {
                        onResetAllSlots()
                        showResetAllConfirmDialog = false
                        Toast.makeText(context, "تمت استعادة كافة الأصوات الافتراضية", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("نعم، استعادة الكل")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllConfirmDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

private fun isSlotAudioReady(
    slot: CustomSoundSlot,
    elevenLabsSettings: ElevenLabsSettings,
    context: android.content.Context
): Boolean {
    if (!slot.uploadedAudioPath.isNullOrBlank() && File(slot.uploadedAudioPath).exists()) return true
    if (!slot.recordingPath.isNullOrBlank() && File(slot.recordingPath).exists()) return true
    if (!slot.elevenLabsAudioPath.isNullOrBlank() && File(slot.elevenLabsAudioPath).exists()) return true

    val cleanText = if (slot.id == "turn_general" || slot.category == SoundSlotCategory.TURN_START) {
        val raw = slot.customText.ifBlank { "يلا يا {الاسم}، دورك" }
        raw.replace("{الاسم}", "اللاعب").trim()
    } else {
        slot.customText.trim().ifBlank {
            when (slot.category) {
                SoundSlotCategory.COUNTDOWN_5 -> "خَمْسَة"
                SoundSlotCategory.COUNTDOWN_4 -> "أَرْبَعَة"
                SoundSlotCategory.COUNTDOWN_3 -> "ثَلَاثَة"
                SoundSlotCategory.COUNTDOWN_2 -> "اِثْنَيْن"
                SoundSlotCategory.COUNTDOWN_1 -> "وَاحِد"
                SoundSlotCategory.TIME_UP -> "اِسْحَبْ ثَلَاثْ يَا غَشِيم!"
                else -> ""
            }
        }
    }

    if (cleanText.isNotBlank()) {
        val cacheDir = File(context.filesDir, "elevenlabs_cache")
        if (cacheDir.exists()) {
            val raw = "${elevenLabsSettings.voiceId}_${elevenLabsSettings.modelId}_$cleanText" +
                    "_${elevenLabsSettings.stability}_${elevenLabsSettings.similarityBoost}" +
                    "_${elevenLabsSettings.style}_${elevenLabsSettings.speed}"
            val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            val hash = bytes.joinToString("") { "%02x".format(it) }.take(28)
            val file = File(cacheDir, "el_$hash.mp3")
            return file.exists() && file.length() > 0
        }
    }
    return false
}

@Composable
private fun SoundSlotCard(
    slot: CustomSoundSlot,
    isCurrentlyRecording: Boolean,
    recordingElapsedMs: Long,
    recordingAmplitude: Int,
    isGeneratingElevenLabs: Boolean = false,
    isReady: Boolean = false,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onDeleteRecording: (String) -> Unit,
    onUploadAudio: (Uri) -> Unit,
    onDeleteUploaded: () -> Unit,
    onGenerateElevenLabs: () -> Unit = {},
    onDeleteElevenLabs: () -> Unit = {},
    onUpdateSlot: (CustomSoundSlot) -> Unit,
    onTestSlot: () -> Unit,
    onDeleteSlot: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activeSource = slot.activeSource
    var textInput by remember(slot.customText) { mutableStateOf(slot.customText) }
    var showAdvanced by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onUploadAudio(uri)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("slot_card_${slot.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Title + Status Badge ("جاهز" أو "لم يُولَّد") + Active Source Badge (+ Delete if CUSTOM)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = slot.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (slot.description.isNotBlank()) {
                        Text(
                            text = slot.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Status Badge: "جاهز ✓" vs "لم يُولَّد"
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isReady) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        border = BorderStroke(1.dp, if (isReady) Color(0xFF81C784) else Color(0xFFEF9A9A))
                    ) {
                        Text(
                            text = if (isReady) "جاهز ✓" else "لم يُولَّد",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            color = if (isReady) Color(0xFF1B5E20) else Color(0xFFC62828)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (activeSource) {
                            SoundSourceType.UPLOADED -> Color(0xFF1565C0).copy(alpha = 0.15f)
                            SoundSourceType.RECORDING -> Color(0xFF2E7D32).copy(alpha = 0.2f)
                            SoundSourceType.ELEVENLABS -> Color(0xFF673AB7).copy(alpha = 0.18f)
                            SoundSourceType.TEXT -> MaterialTheme.colorScheme.primaryContainer
                            SoundSourceType.DEFAULT -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        },
                        border = BorderStroke(
                            1.dp,
                            when (activeSource) {
                                SoundSourceType.UPLOADED -> Color(0xFF1976D2)
                                SoundSourceType.RECORDING -> Color(0xFF2E7D32)
                                SoundSourceType.ELEVENLABS -> Color(0xFF7E57C2)
                                SoundSourceType.TEXT -> MaterialTheme.colorScheme.primary
                                SoundSourceType.DEFAULT -> MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                    ) {
                        Text(
                            text = activeSource.labelAr,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = when (activeSource) {
                                SoundSourceType.UPLOADED -> Color(0xFF0D47A1)
                                SoundSourceType.RECORDING -> Color(0xFF1B5E20)
                                SoundSourceType.ELEVENLABS -> Color(0xFF4527A0)
                                SoundSourceType.TEXT -> MaterialTheme.colorScheme.primary
                                SoundSourceType.DEFAULT -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    if (slot.category == SoundSlotCategory.CUSTOM && onDeleteSlot != null) {
                        IconButton(onClick = onDeleteSlot, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "حذف الخانة",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Single Text Field (Accepts diacritics and {الاسم})
            OutlinedTextField(
                value = textInput,
                onValueChange = {
                    textInput = it
                    onUpdateSlot(slot.copy(customText = it))
                },
                label = { Text("نص العبارة (يقبل التشكيل ومتغير {الاسم})") },
                placeholder = {
                    Text(
                        when (slot.category) {
                            SoundSlotCategory.TURN_START -> "يلا يا {الاسم}، دورك"
                            SoundSlotCategory.COUNTDOWN_5 -> "خَمْسَة"
                            SoundSlotCategory.COUNTDOWN_4 -> "أَرْبَعَة"
                            SoundSlotCategory.COUNTDOWN_3 -> "ثَلَاثَة"
                            SoundSlotCategory.COUNTDOWN_2 -> "اِثْنَيْن"
                            SoundSlotCategory.COUNTDOWN_1 -> "وَاحِد"
                            SoundSlotCategory.TIME_UP -> "اِسْحَبْ ثَلَاثْ يَا غَشِيم!"
                            else -> slot.title
                        }
                    )
                },
                singleLine = false,
                maxLines = 2,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("slot_text_${slot.id}")
            )

            // Arabic Diacritics Helper Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val diacritics = listOf("َ", "ً", "ُ", "ٌ", "ِ", "ٍ", "ّ", "ْ")
                Text("تشكيل:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                diacritics.forEach { mark ->
                    Surface(
                        onClick = {
                            val newText = textInput + mark
                            textInput = newText
                            onUpdateSlot(slot.copy(customText = newText))
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = mark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                if (slot.category == SoundSlotCategory.TURN_START) {
                    Spacer(modifier = Modifier.weight(1f))
                    Surface(
                        onClick = {
                            val newText = if (textInput.contains("{الاسم}")) textInput else textInput + " {الاسم}"
                            textInput = newText
                            onUpdateSlot(slot.copy(customText = newText))
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "+ {الاسم}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Only TWO Primary Buttons: "توليد ⚡" & "استمع 🎧"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Generate Button
                Button(
                    onClick = onGenerateElevenLabs,
                    enabled = !isGeneratingElevenLabs,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("generate_btn_${slot.id}")
                ) {
                    if (isGeneratingElevenLabs) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("جارٍ التوليد...", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("توليد ⚡", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Listen Button
                OutlinedButton(
                    onClick = onTestSlot,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("listen_btn_${slot.id}")
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("استمع 🎧", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Collapsible "خيارات متقدمة" (Folded by default)
            TextButton(
                onClick = { showAdvanced = !showAdvanced },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text(
                    text = if (showAdvanced) "▲ إخفاء الخيارات المتقدمة" else "▼ خيارات متقدمة (رفع ملف صوتي أو تسجيل صوتك)",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Uploaded Audio File info / action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("audio/*") },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ارفع ملف صوتي 📁", fontSize = 11.5.sp)
                        }

                        if (!slot.uploadedAudioPath.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = slot.uploadedAudioName ?: "ملف صوتي",
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    modifier = Modifier.widthIn(max = 120.dp)
                                )
                                IconButton(onClick = onDeleteUploaded, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "حذف الملف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }

                    // 2. Microphone Recording
                    if (isCurrentlyRecording) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Red.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "جارٍ التسجيل: ${String.format(java.util.Locale.US, "%.1f", recordingElapsedMs / 1000.0)} ث",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Red
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = onStopRecording,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("حفظ", fontSize = 11.5.sp)
                                }
                                OutlinedButton(
                                    onClick = onCancelRecording,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("إلغاء", fontSize = 11.5.sp)
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(
                                onClick = onStartRecording,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF2E7D32))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("سجّل صوتك 🎙️", fontSize = 11.5.sp, color = Color(0xFF2E7D32))
                            }

                            if (!slot.recordingPath.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("يوجد تسجيل خاص", fontSize = 11.sp, color = Color(0xFF2E7D32))
                                    IconButton(
                                        onClick = { onDeleteRecording(slot.recordingPath) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "حذف التسجيل", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * ElevenLabs Master Control Card
 * Single master Voice ID and settings (Stability, Similarity, Style, Speed) for all spoken audio in the game.
 * Features batch sound generation and confirmation dialog upon Voice ID change.
 */
@Composable
private fun ElevenLabsMasterCard(
    settings: ElevenLabsSettings,
    onUpdateSettings: (ElevenLabsSettings) -> Unit,
    onTestVoice: (String) -> Unit,
    onPregenerateAll: () -> Unit,
    totalCharactersUsed: Long,
    onResetCharacters: () -> Unit,
    isGenerating: String?,
    statusMessage: String?,
    onDismissMessage: () -> Unit
) {
    var expandedSettings by remember { mutableStateOf(false) }
    var voiceIdInput by remember(settings.voiceId) { mutableStateOf(settings.voiceId) }
    var showVoiceIdConfirmDialog by remember { mutableStateOf(false) }
    var pendingVoiceId by remember { mutableStateOf("") }

    var modelIdInput by remember(settings.modelId) { mutableStateOf(settings.modelId) }
    var stability by remember(settings.stability) { mutableFloatStateOf(settings.stability) }
    var similarityBoost by remember(settings.similarityBoost) { mutableFloatStateOf(settings.similarityBoost) }
    var style by remember(settings.style) { mutableFloatStateOf(settings.style) }
    var speed by remember(settings.speed) { mutableFloatStateOf(settings.speed) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF673AB7).copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.5.dp, Color(0xFF7E57C2).copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF673AB7),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SmartToy, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "محرك الصوت البشري (ElevenLabs) 🤖",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF4527A0)
                        )
                        Text(
                            text = "صوت موحد لجميع الأصوات المنطوقة مع تخزين محلي (صفر تأخير)",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { expandedSettings = !expandedSettings }) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "إعدادات الصوت الموحد",
                        tint = Color(0xFF512DA8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Character Usage Badge & Reset
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, Color(0xFF7E57C2).copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFF57C00),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "استهلاك الرصيد: $totalCharactersUsed حرفاً",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (totalCharactersUsed > 0) {
                        TextButton(
                            onClick = onResetCharacters,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("تصفير العداد", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Status message or progress indicator
            if (isGenerating != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF673AB7).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF512DA8))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isGenerating == "ALL") "جارٍ توليد كافة أصوات اللعبة دفعة واحدة بالصوت الأساسي..." else "جارٍ توليد الصوت عبر ElevenLabs...",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF512DA8)
                    )
                }
            } else if (statusMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (statusMessage.contains("نجاح") || statusMessage.contains("جاهز") || statusMessage.contains("اكتمل")) Color(0xFF2E7D32).copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = statusMessage,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismissMessage, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", modifier = Modifier.size(14.dp))
                    }
                }
            }

            // Collapsible Master Settings (Voice ID, Model, Sliders)
            AnimatedVisibility(visible = expandedSettings) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = Color(0xFF7E57C2).copy(alpha = 0.25f))

                    OutlinedTextField(
                        value = voiceIdInput,
                        onValueChange = { voiceIdInput = it },
                        label = { Text("معرف الصوت الموحد (Voice ID)") },
                        supportingText = { Text("الافتراضي: ECc3xM44reI7J5jdm8jB", fontSize = 11.sp) },
                        trailingIcon = {
                            if (voiceIdInput.trim() != settings.voiceId && voiceIdInput.isNotBlank()) {
                                Button(
                                    onClick = {
                                        pendingVoiceId = voiceIdInput.trim()
                                        showVoiceIdConfirmDialog = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Text("تطبيق", fontSize = 11.sp)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = modelIdInput,
                        onValueChange = {
                            modelIdInput = it
                            onUpdateSettings(settings.copy(modelId = it.trim()))
                        },
                        label = { Text("نموذج الصوت (Model)") },
                        supportingText = { Text("eleven_multilingual_v2 (أو eleven_v3)", fontSize = 11.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Slider: Stability
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ثبات الصوت (Stability):", fontSize = 11.5.sp)
                            Text("${"%.2f".format(stability)}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = stability,
                            onValueChange = {
                                stability = it
                                onUpdateSettings(settings.copy(stability = it))
                            },
                            valueRange = 0.0f..1.0f,
                            steps = 19,
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    // Slider: Similarity Boost
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("مطابقة النبرة (Similarity Boost):", fontSize = 11.5.sp)
                            Text("${"%.2f".format(similarityBoost)}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = similarityBoost,
                            onValueChange = {
                                similarityBoost = it
                                onUpdateSettings(settings.copy(similarityBoost = it))
                            },
                            valueRange = 0.0f..1.0f,
                            steps = 19,
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    // Slider: Style
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("أسلوب التعبير (Style):", fontSize = 11.5.sp)
                            Text("${"%.2f".format(style)}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = style,
                            onValueChange = {
                                style = it
                                onUpdateSettings(settings.copy(style = it))
                            },
                            valueRange = 0.0f..1.0f,
                            steps = 19,
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    // Slider: Speed
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("سرعة الإلقاء (Speed):", fontSize = 11.5.sp)
                            Text("${"%.2f".format(speed)}x", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = speed,
                            onValueChange = {
                                speed = it
                                onUpdateSettings(settings.copy(speed = it))
                            },
                            valueRange = 0.70f..1.30f,
                            steps = 11,
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Actions: Test Voice & Generate All Sounds
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { onTestVoice("يلا يا خالد، دورك!") },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تجربة الصوت 🎧", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onPregenerateAll,
                    enabled = isGenerating == null,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("pregenerate_all_btn")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("توليد كل الأصوات ⚡", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Voice ID Change Confirmation Dialog
    if (showVoiceIdConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                voiceIdInput = settings.voiceId
                showVoiceIdConfirmDialog = false
            },
            title = { Text("تغيير معرف الصوت (Voice ID)", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = """تم تعديل معرف الصوت إلى:
$pendingVoiceId

هل تريد إعادة توليد كافة أصوات اللعبة تلقائيًا بالصوت الجديد؟""",
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateSettings(settings.copy(voiceId = pendingVoiceId))
                        showVoiceIdConfirmDialog = false
                        onPregenerateAll()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
                ) {
                    Text("نعم، أعد توليد كل الأصوات ⚡")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            onUpdateSettings(settings.copy(voiceId = pendingVoiceId))
                            showVoiceIdConfirmDialog = false
                        }
                    ) {
                        Text("تغيير المعرف فقط")
                    }
                    TextButton(
                        onClick = {
                            voiceIdInput = settings.voiceId
                            showVoiceIdConfirmDialog = false
                        }
                    ) {
                        Text("إلغاء")
                    }
                }
            }
        )
    }
}

/**
 * Diagnostic Connection Status Card (Requirement 4 & 5)
 * Displays:
 * 1. API key presence (نعم/لا without displaying the key value)
 * 2. Active Voice ID
 * 3. Result and timestamp of the latest generation attempt
 * 4. Test connection trigger
 * 5. Full generation log dialog with unsummarized error codes and server messages
 */
@Composable
private fun ConnectionStatusCard(
    voiceId: String,
    onTestConnection: () -> Unit,
    isTestingConnection: Boolean = false
) {
    val context = LocalContext.current
    val elevenLabsService = remember { com.example.audio.elevenlabs.ElevenLabsService(context) }
    val hasKey = remember { elevenLabsService.hasValidApiKey() }

    val lastResult by com.example.audio.elevenlabs.ElevenLabsService.lastAttemptResult.collectAsState()
    val lastTimestamp by com.example.audio.elevenlabs.ElevenLabsService.lastAttemptTimestamp.collectAsState()
    val lastSuccess by com.example.audio.elevenlabs.ElevenLabsService.lastAttemptSuccess.collectAsState()
    val logs by com.example.audio.elevenlabs.ElevenLabsService.logs.collectAsState()

    var showLogsDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Title & Test Connection Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "حالة الاتصال والتشخيص (ElevenLabs)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                FilledTonalButton(
                    onClick = onTestConnection,
                    enabled = !isTestingConnection,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("جارٍ الفحص...", fontSize = 11.sp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("فحص الاتصال 🔄", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1. API Key Presence Check (نعم / لا بدون عرض القيمة)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "وجود مفتاح API:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasKey) {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "نعم ✓",
                            color = Color(0xFF1B5E20),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "لا ✕ (المفتاح مفقود: لم يُضبط ELEVENLABS_API_KEY)",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // 2. Used Voice ID
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "معرّف الصوت (Voice ID):",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = voiceId,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 3. Last Attempt Result
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "نتيجة آخر محاولة توليد:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val statusText = lastResult ?: "لم تتم أي محاولة بعد"
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (lastSuccess) {
                        true -> Color(0xFF1B5E20)
                        false -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 4. Last Attempt Timestamp
            if (lastTimestamp != null) {
                val formatted = remember(lastTimestamp) {
                    SimpleDateFormat("HH:mm:ss - yyyy/MM/dd", Locale.getDefault()).format(Date(lastTimestamp!!))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "وقت آخر محاولة:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatted,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 5. Generation Log Modal Opener & Clear Log
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showLogsDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text("سجل محاولات التوليد (${logs.size}) 📋", fontSize = 11.5.sp)
                }

                if (logs.isNotEmpty()) {
                    TextButton(
                        onClick = { com.example.audio.elevenlabs.ElevenLabsService.clearLogs() },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("مسح السجل", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    // Modal dialog showing generation logs with unsummarized error codes & server messages
    if (showLogsDialog) {
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("سجل محاولات التوليد 📋", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    TextButton(onClick = { com.example.audio.elevenlabs.ElevenLabsService.clearLogs() }) {
                        Text("مسح", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            text = {
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("لا توجد محاولات توليد مسجلة حتى الآن", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs, key = { it.id }) { log ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (log.success) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, if (log.success) Color(0xFF81C784) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = log.target,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (log.success) Color(0xFF1B5E20) else MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = log.formattedTime,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (log.success) "النتيجة: نجاح ✓ (رمز 200)" else "النتيجة: فشل ${if (log.statusCode != null) "(رمز ${log.statusCode})" else ""}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (log.success) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = log.message,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showLogsDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }
}

