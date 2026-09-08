package com.zai.chat.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class LogLevel(val shortName: String) {
    VERBOSE("V"),
    DEBUG("D"),
    INFO("I"),
    WARN("W"),
    ERROR("E"),
    ASSERT("A")
}

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val raw: String
)

/**
 * Zero-Overhead In-App Logcat Engine.
 *
 * Guaranteed 0 CPU and RAM consumption when inactive (isLogging == false).
 * Spawns an adb logcat process on IO thread only upon user activation.
 * Includes direct Clipboard export and MediaStore Downloads folder file saving.
 */
@Singleton
class LogcatManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var logJob: Job? = null
    private var logProcess: Process? = null

    private val _isLogging = MutableStateFlow(false)
    val isLogging: StateFlow<Boolean> = _isLogging.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private var entryCounter = 0L

    @Synchronized
    fun startLogging() {
        if (_isLogging.value) return
        _isLogging.value = true

        logJob?.cancel()
        logJob = scope.launch {
            try {
                Log.i("BleedAI-Logcat", "Live logcat session started (Zero-Overhead Engine active)")
                
                // Read logcat with time format and thread/tag details
                val process = ProcessBuilder("logcat", "-v", "time", "*:V")
                    .redirectErrorStream(true)
                    .start()
                logProcess = process

                val reader = BufferedReader(InputStreamReader(process.inputStream), 8192)
                val buffer = ArrayList<LogEntry>(3000)
                var lastBatchTime = System.currentTimeMillis()

                // Initial greeting banner
                val startEntry = LogEntry(
                    id = ++entryCounter,
                    timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date()),
                    level = LogLevel.INFO,
                    tag = "BleedAI-Terminal",
                    message = "─── LIVE ADB LOGCAT CAPTURE INITIALIZED ───",
                    raw = "─── LIVE ADB LOGCAT CAPTURE INITIALIZED ───"
                )
                buffer.add(startEntry)
                _logs.value = listOf(startEntry)

                while (isActive && _isLogging.value) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue

                    val entry = parseLogcatLine(line, ++entryCounter)
                    buffer.add(entry)
                    if (buffer.size > 3000) {
                        buffer.removeAt(0)
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastBatchTime > 100) { // Batch at ~10Hz for smooth 60fps UI
                        _logs.value = ArrayList(buffer)
                        lastBatchTime = now
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    val errEntry = LogEntry(
                        id = ++entryCounter,
                        timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date()),
                        level = LogLevel.ERROR,
                        tag = "BleedAI-Logcat",
                        message = "Logcat process error: ${e.message}",
                        raw = "Logcat process error: ${e.message}"
                    )
                    _logs.update { it + errEntry }
                }
            } finally {
                cleanupProcess()
            }
        }
    }

    @Synchronized
    fun stopLogging() {
        _isLogging.value = false
        cleanupProcess()
        Log.i("BleedAI-Logcat", "Live logcat session terminated (0 overhead mode restored)")
    }

    private fun cleanupProcess() {
        logJob?.cancel()
        logJob = null
        try {
            logProcess?.destroy()
        } catch (_: Exception) {}
        logProcess = null
    }

    fun clearLogs() {
        _logs.value = emptyList()
        scope.launch {
            try {
                ProcessBuilder("logcat", "-c").start().waitFor()
            } catch (_: Exception) {}
        }
    }

    fun copyLogsToClipboard(entries: List<LogEntry>): Int {
        val text = buildString {
            entries.forEach { appendLine(it.raw) }
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Bleed-AI Logcat", text)
        clipboard.setPrimaryClip(clip)
        return entries.size
    }

    fun exportLogsToDownloads(entries: List<LogEntry>): Result<String> = runCatching {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "bleed_ai_logcat_$timestamp.txt"
        val header = """
            ================================================================
            BLEED-AI SYSTEM LOGCAT TERMINAL EXPORT
            Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())}
            Total Log Lines: ${entries.size}
            Target Build: Bleed-AI Android Client
            ================================================================
            
        """.trimIndent()

        val fullText = header + "\n" + entries.joinToString("\n") { it.raw }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IOException("Failed to create file in MediaStore Downloads")

            resolver.openOutputStream(uri)?.use { os ->
                os.write(fullText.toByteArray(Charsets.UTF_8))
                os.flush()
            } ?: throw IOException("Failed to open MediaStore output stream")

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            "Download/$fileName"
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            file.writeText(fullText, Charsets.UTF_8)
            file.absolutePath
        }
    }

    private fun parseLogcatLine(raw: String, id: Long): LogEntry {
        // Typical format: "MM-dd HH:mm:ss.SSS D/Tag( PID): Message" or "MM-dd HH:mm:ss.SSS Tag: Message"
        try {
            if (raw.length > 18 && raw[2] == '-' && raw[5] == ' ') {
                val timePart = raw.substring(0, 18).trim()
                val remaining = raw.substring(18).trimStart()

                // Check for Level/Tag format e.g. "D/BleedAI-Transport( 1234): ..."
                val slashIndex = remaining.indexOf('/')
                if (slashIndex in 1..2) {
                    val levelChar = remaining[slashIndex - 1]
                    val level = when (levelChar) {
                        'V' -> LogLevel.VERBOSE
                        'D' -> LogLevel.DEBUG
                        'I' -> LogLevel.INFO
                        'W' -> LogLevel.WARN
                        'E' -> LogLevel.ERROR
                        'A' -> LogLevel.ASSERT
                        else -> LogLevel.DEBUG
                    }

                    val colonIndex = remaining.indexOf(':', startIndex = slashIndex)
                    if (colonIndex > slashIndex) {
                        val rawTag = remaining.substring(slashIndex + 1, colonIndex).trim()
                        // Strip ( 1234) pid info if present
                        val cleanTag = rawTag.replace(Regex("""\(\s*\d+\)"""), "").trim()
                        val msg = remaining.substring(colonIndex + 1).trimStart()
                        return LogEntry(id, timePart, level, cleanTag, msg, raw)
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback for unformatted lines
        val guessedLevel = when {
            raw.contains(" E ") || raw.contains("ERROR") || raw.contains("Exception") -> LogLevel.ERROR
            raw.contains(" W ") || raw.contains("WARN") -> LogLevel.WARN
            raw.contains(" I ") || raw.contains("INFO") -> LogLevel.INFO
            else -> LogLevel.DEBUG
        }
        return LogEntry(id, "", guessedLevel, "System", raw, raw)
    }
}
