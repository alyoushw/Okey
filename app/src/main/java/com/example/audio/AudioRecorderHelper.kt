package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Native Android Audio Recorder and Player for Custom Sounds.
 * Handles recording up to 10 seconds with amplitude feedback,
 * playback, and safe storage in app-private files directory.
 */
class AudioRecorderHelper(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentOutputFile: File? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val audioDir: File
        get() = File(context.filesDir, "custom_audio").apply {
            if (!exists()) mkdirs()
        }

    fun isRecording(): Boolean = mediaRecorder != null

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    /**
     * Starts recording audio into app-private storage.
     * [onTick] is invoked every 100ms with elapsed time in ms and current amplitude (0..32767).
     * Automatically stops and invokes [onComplete] when 10 seconds is reached.
     */
    fun startRecording(
        slotId: String,
        onTick: (elapsedMs: Long, amplitude: Int) -> Unit,
        onComplete: (filePath: String) -> Unit,
        onError: (message: String) -> Unit
    ) {
        stopPlayback()
        stopRecording()

        try {
            val safeSlotName = slotId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val outputFile = File(audioDir, "rec_${safeSlotName}.m4a")
            if (outputFile.exists()) {
                outputFile.delete()
            }
            currentOutputFile = outputFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                setMaxDuration(10_000) // 10 seconds limit
                prepare()
                start()
            }
            mediaRecorder = recorder

            val startTime = System.currentTimeMillis()
            recordingJob = scope.launch {
                while (isActive && mediaRecorder != null) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val amp = try {
                        mediaRecorder?.maxAmplitude ?: 0
                    } catch (_: Exception) {
                        0
                    }
                    onTick(elapsed, amp)

                    if (elapsed >= 10_000L) {
                        stopRecording()
                        onComplete(outputFile.absolutePath)
                        break
                    }
                    delay(100)
                }
            }
        } catch (e: Exception) {
            Log.e("AudioRecorderHelper", "Failed to start recording", e)
            mediaRecorder = null
            onError(e.localizedMessage ?: "فشل بدء التسجيل")
        }
    }

    fun stopRecording(): String? {
        recordingJob?.cancel()
        recordingJob = null

        val recorder = mediaRecorder ?: return null
        return try {
            recorder.stop()
            recorder.reset()
            recorder.release()
            mediaRecorder = null
            currentOutputFile?.absolutePath
        } catch (e: Exception) {
            Log.e("AudioRecorderHelper", "Error stopping recorder", e)
            mediaRecorder = null
            null
        }
    }

    fun cancelRecording() {
        recordingJob?.cancel()
        recordingJob = null
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null

        currentOutputFile?.let {
            if (it.exists()) it.delete()
        }
        currentOutputFile = null
    }

    fun playAudio(
        filePath: String,
        volume: Float = 1.0f,
        onFinished: () -> Unit = {}
    ) {
        stopPlayback()
        val file = File(filePath)
        if (!file.exists()) {
            onFinished()
            return
        }

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.absolutePath)
                val effVol = volume.coerceIn(0.0f, 1.0f)
                setVolume(effVol, effVol)
                setOnCompletionListener { mp ->
                    try {
                        mp.release()
                    } catch (_: Exception) {}
                    if (mediaPlayer == mp) {
                        mediaPlayer = null
                    }
                    onFinished()
                }
                prepare()
                start()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e("AudioRecorderHelper", "Failed to play recorded file: $filePath", e)
            onFinished()
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                reset()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun deleteFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) file.delete() else false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Imports an external audio file selected by the user (mp3, wav, m4a, ogg, aac)
     * into the app's private files directory.
     * Returns Pair(absolutePath, originalFileName) or null on error.
     */
    fun importAudioFileFromUri(uri: android.net.Uri, slotId: String): Pair<String, String>? {
        return try {
            val contentResolver = context.contentResolver
            var fileName = "custom_audio_${slotId}.mp3"
            var extension = "mp3"

            // Query original file name if available
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        val queriedName = cursor.getString(nameIndex)
                        if (!queriedName.isNullOrBlank()) {
                            fileName = queriedName
                            val dotIdx = fileName.lastIndexOf('.')
                            if (dotIdx != -1 && dotIdx < fileName.length - 1) {
                                extension = fileName.substring(dotIdx + 1).lowercase()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("AudioRecorderHelper", "Could not query file display name", e)
            }

            val safeSlotName = slotId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val targetFile = File(audioDir, "upload_${safeSlotName}.$extension")
            if (targetFile.exists()) {
                targetFile.delete()
            }

            contentResolver.openInputStream(uri)?.use { inputStream ->
                targetFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return null

            if (targetFile.exists() && targetFile.length() > 0) {
                Pair(targetFile.absolutePath, fileName)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("AudioRecorderHelper", "Failed to import audio file from uri: $uri", e)
            null
        }
    }
}
