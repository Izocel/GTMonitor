package com.example.gtmonitor

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

object GTLog {

    private const val TAG = "GTMonitor"
    private const val LOG_FILE = "gt_log.txt"
    private const val MAX_LINES = 5000

    private val executor = Executors.newSingleThreadExecutor()
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()

    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE)
    }

    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }

    fun d(message: String) {
        Log.d(TAG, message)
        writeToFile("D", message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
        writeToFile("I", message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(TAG, message, throwable) else Log.w(TAG, message)
        writeToFile("W", if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
        writeToFile("E", if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message)
    }

    fun readAll(): String {
        return logFile?.takeIf { it.exists() }?.readText() ?: ""
    }

    fun clear() {
        logFile?.takeIf { it.exists() }?.writeText("")
    }

    private fun writeToFile(level: String, message: String) {
        val file = logFile ?: return
        val timestamp = dateFormat.format(Date())
        val line = "$timestamp [$level] $message"

        // Notify live listeners
        listeners.forEach { it(line) }

        executor.execute {
            try {
                FileWriter(file, true).use { it.appendLine(line) }
                trimIfNeeded(file)
            } catch (_: Exception) { /* ignore file I/O errors */ }
        }
    }

    private fun trimIfNeeded(file: File) {
        val lines = file.readLines()
        if (lines.size > MAX_LINES) {
            file.writeText(lines.takeLast(MAX_LINES).joinToString("\n") + "\n")
        }
    }
}
