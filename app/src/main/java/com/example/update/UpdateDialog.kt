package com.example.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Shows a notice when a newer build is published, then downloads and opens the installer. */
@Composable
fun UpdateNotice() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<AvailableUpdate?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { update = AppUpdater.check() }

    val available = update ?: return
    if (dismissed) return
    val downloading = progress != null
    AlertDialog(
        onDismissRequest = { if (!downloading) dismissed = true },
        title = { Text("تحديث جديد متوفر 🎉") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("نسخة ${available.versionName} جاهزة للتحميل.")
                if (available.notes.isNotBlank()) Text(available.notes)
                progress?.let {
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
                    Text("جارٍ التحميل… ${(it * 100).toInt()}٪")
                }
                if (failed) Text("تعذّر التحميل. تأكد من الإنترنت وحاول مرة ثانية.")
            }
        },
        confirmButton = {
            Button(
                enabled = !downloading,
                onClick = {
                    failed = false
                    progress = 0f
                    scope.launch {
                        val apk = AppUpdater.download(context) { value -> progress = value }
                        progress = null
                        if (apk == null) failed = true else AppUpdater.install(context, apk)
                    }
                }
            ) { Text(if (failed) "إعادة المحاولة" else "تحديث الآن") }
        },
        dismissButton = {
            if (!downloading) TextButton(onClick = { dismissed = true }) { Text("لاحقًا") }
        }
    )
}
