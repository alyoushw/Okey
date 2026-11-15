package com.example.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class AvailableUpdate(val versionCode: Int, val versionName: String, val notes: String)

/** Checks the preview-latest release on GitHub and installs a newer APK from it. */
object AppUpdater {
    private const val BASE = "https://github.com/alyoushw/Okey/releases/download/preview-latest"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun check(): AvailableUpdate? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$BASE/version.json?t=${System.currentTimeMillis()}").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string() ?: return@withContext null)
                val code = json.optInt("versionCode", 0)
                if (code <= BuildConfig.VERSION_CODE) return@withContext null
                AvailableUpdate(code, json.optString("versionName"), json.optString("notes"))
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Downloads the APK, reporting progress from 0 to 1. Returns the file, or null on failure. */
    suspend fun download(context: Context, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(folder, "Okey.apk")
        val temporary = File(folder, "Okey.apk.part")
        try {
            client.newCall(Request.Builder().url("$BASE/Okey.apk").build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                val total = body.contentLength().takeIf { it > 0 } ?: -1L
                var copied = 0L
                body.byteStream().use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            target.delete()
            if (!temporary.renameTo(target) || target.length() == 0L) return@withContext null
            target
        } catch (_: Exception) {
            null
        } finally {
            temporary.delete()
        }
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
