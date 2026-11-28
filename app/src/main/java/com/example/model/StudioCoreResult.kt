package com.example.model

import java.util.Collections

/**
 * Typed domain error with safe user-facing message and secret-redacted diagnostic detail.
 */
data class StudioError(
    val category: String,
    val userMessage: String,
    val technicalDetail: String = "",
    val recoverable: Boolean = true
)

/**
 * Typed Task / Pipeline state for non-blocking studio operations.
 * progressFraction is null whenever determinate progress cannot be truthfully measured.
 */
sealed interface StudioTaskState<out T> {
    data object Idle : StudioTaskState<Nothing>
    data class Progress(
        val stage: String,
        val detail: String,
        val fraction: Float? = null
    ) : StudioTaskState<Nothing>
    data class Success<T>(val data: T) : StudioTaskState<T>
    data class Failure(val error: StudioError) : StudioTaskState<Nothing>
    data class Cancelled(val reason: String = "Cancelled by user") : StudioTaskState<Nothing>
}

data class StudioLogEvent(
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String
)

/**
 * Central event/logging hub with automatic secret redaction.
 */
object StudioEventLogger {
    private const val MAX_EVENTS = 150
    private val events = Collections.synchronizedList(mutableListOf<StudioLogEvent>())

    private val redactionPatterns = listOf(
        Regex("(gh[pousr]_[A-Za-z0-9_]{8,})"),
        Regex("(AIza[0-9A-Za-z\\-_]{16,})"),
        Regex("(?i)(bearer\\s+)[A-Za-z0-9\\-._~+/]+=*"),
        Regex("(?i)(token|password|secret|apikey|api_key)\\s*[:=]\\s*[^\\s,;\"']+")
    )

    fun redact(raw: String): String {
        var out = raw
        redactionPatterns.forEachIndexed { idx, rx ->
            out = when (idx) {
                2 -> rx.replace(out) { "${it.groupValues[1]}[REDACTED]" }
                3 -> rx.replace(out) { "${it.groupValues[1]}=[REDACTED]" }
                else -> rx.replace(out, "[REDACTED]")
            }
        }
        return out
    }

    fun log(level: String, tag: String, message: String) {
        val entry = StudioLogEvent(
            timestamp = System.currentTimeMillis(),
            level = level.uppercase(),
            tag = tag,
            message = redact(message)
        )
        synchronized(events) {
            events.add(entry)
            if (events.size > MAX_EVENTS) {
                events.removeAt(0)
            }
        }
    }

    fun snapshot(): List<StudioLogEvent> = synchronized(events) { events.toList().reversed() }
}
