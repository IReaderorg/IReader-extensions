package ireader.testserver

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import java.util.concurrent.ConcurrentLinkedDeque
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Captures logs from sources and makes them available via API.
 * Also prints to console for immediate visibility.
 */
object LogCapture {
    
    private val logs = ConcurrentLinkedDeque<LogEntry>()
    private const val MAX_LOGS = 500
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    
    data class LogEntry(
        val timestamp: String,
        val level: String,
        val tag: String,
        val message: String,
        val throwable: String? = null
    )
    
    /**
     * Initialize log capture by adding our custom log writer to Kermit.
     */
    fun initialize() {
        Logger.setMinSeverity(Severity.Verbose)
        Logger.addLogWriter(object : LogWriter() {
            override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                val entry = LogEntry(
                    timestamp = LocalDateTime.now().format(formatter),
                    level = severity.name,
                    tag = tag,
                    message = message,
                    throwable = throwable?.stackTraceToString()?.take(500)
                )
                
                // Add to buffer
                logs.addFirst(entry)
                while (logs.size > MAX_LOGS) {
                    logs.removeLast()
                }
                
                // Also print to console with color
                val color = when (severity) {
                    Severity.Error -> "\u001B[31m"   // Red
                    Severity.Warn -> "\u001B[33m"    // Yellow
                    Severity.Info -> "\u001B[36m"    // Cyan
                    Severity.Debug -> "\u001B[32m"   // Green
                    Severity.Verbose -> "\u001B[37m" // White
                    else -> "\u001B[0m"
                }
                val reset = "\u001B[0m"
                println("$color[${entry.timestamp}] [${severity.name.padEnd(5)}] [$tag] $message$reset")
                throwable?.let { println("$color${it.stackTraceToString().take(500)}$reset") }
            }
        })
        
        println("📋 Log capture initialized - source logs will appear here and in /api/logs")
    }
    
    /**
     * Get recent logs, optionally filtered by level.
     */
    fun getLogs(minLevel: String? = null, limit: Int = 100): List<LogEntry> {
        val filtered = if (minLevel != null) {
            val minSeverity = try { Severity.valueOf(minLevel) } catch (e: Exception) { Severity.Verbose }
            logs.filter { 
                try { Severity.valueOf(it.level).ordinal >= minSeverity.ordinal } 
                catch (e: Exception) { true }
            }
        } else {
            logs.toList()
        }
        return filtered.take(limit)
    }
    
    /**
     * Clear all captured logs.
     */
    fun clearLogs() {
        logs.clear()
    }
}
