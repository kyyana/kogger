/*
 * Copyright 2026 kyyana
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kyyana.kogger

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.kyyana.kogger.provider.AsyncLoggerProvider
import org.kyyana.kogger.provider.LoggerProvider
import org.kyyana.kogger.writer.RollingFileWriter

/**
 * Creates a [KotlinLogger] tagged with the receiver's class, so log lines are labeled
 * with the class name. Typical use: `private val logger = logger()`.
 */
inline fun <reified T : Any> T.logger() = KotlinLogger(T::class)

/**
 * Global configuration and dispatch hub shared by every [KotlinLogger].
 *
 * Holds the logging settings ([minLevel], [colors], [timeFormatter]) and the active
 * [loggerProvider] that receives every event passing the level filter. Configure it
 * once at startup — optionally enabling file logging ([enableFileLogging]) and
 * asynchronous dispatch ([enableAsyncLogging]) — and call [shutdown] before the
 * process exits to drain and release resources.
 */
object Kogger {
    /** Minimum level that gets logged; anything below it is dropped before formatting. */
    var minLevel = LogType.INFO

    /** Whether console output is wrapped in ANSI color codes. */
    var colors = true

    /** Timestamp format used in each log line. Defaults to `HH:mm:ss.SSS`. */
    var timeFormatter =
        LocalDateTime.Format {
            hour()
            char(':')
            minute()
            char(':')
            second()
            char('.')
            secondFraction(3)
        }

    /**
     * The active [RollingFileWriter], or `null` until [enableFileLogging] is called. Exposed
     * so a custom [loggerProvider] can reuse the default file output by calling
     * [RollingFileWriter.write] on it.
     */
    var fileWriter: RollingFileWriter? = null

    /**
     * The active provider that receives every log event that passes the level filter.
     * Defaults to a console (+ optional file) provider; [enableAsyncLogging] wraps it.
     */
    var loggerProvider: LoggerProvider? =
        LoggerProvider { clazz, logType, throwable, instant, message ->
            val now = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            val finalMessage =
                if (throwable != null) {
                    "[${now.format(timeFormatter)}] [${clazz.simpleName}/${logType.name}]: $message\n${throwable.stackTraceToString()}"
                } else {
                    "[${now.format(timeFormatter)}] [${clazz.simpleName}/${logType.name}]: $message"
                }

            fileWriter?.write(finalMessage)

            if (colors) {
                println("${logType.ansiColor}$finalMessage${AnsiColors.RESET}")
            } else {
                println(finalMessage)
            }
        }

    /**
     * Applies CLI flags: `--trace` lowers [minLevel] to [LogType.TRACE] and `--debug`
     * lowers it to [LogType.DEBUG] (`--trace` takes precedence if both are present);
     * `--no-colors` disables [colors].
     */
    fun verifyArgs(args: Array<String>) {
        if (args.contains("--trace")) {
            minLevel = LogType.TRACE
        } else if (args.contains("--debug")) {
            minLevel = LogType.DEBUG
        }
        if (args.contains("--no-colors")) colors = false
    }

    /**
     * Enables writing logs to disk via a [RollingFileWriter] under [directory], keeping
     * at most [maxArchivedFiles] gzip archives. See the writer for the rotation scheme.
     */
    fun enableFileLogging(
        directory: String,
        maxArchivedFiles: Int,
    ) {
        fileWriter = RollingFileWriter(directory, maxArchivedFiles)
    }

    /**
     * Wraps the current [loggerProvider] in an [AsyncLoggerProvider] so log I/O runs
     * off the calling thread, using a channel of [capacity] and the given
     * [onBufferOverflow] policy. Must be called after a base provider exists.
     */
    fun enableAsyncLogging(
        capacity: Int,
        onBufferOverflow: BufferOverflow,
    ) {
        loggerProvider =
            loggerProvider?.let { AsyncLoggerProvider(capacity, onBufferOverflow, it) }
                ?: error("loggerProvider must be set before enabling asynchronous logging")
    }

    /**
     * Drains and releases logging resources: stops the async worker (waiting for the
     * queue to flush) and closes the file. Call from a normal execution path before
     * exit — never from a POSIX signal handler, where the coroutine join would deadlock.
     */
    fun shutdown() {
        (loggerProvider as? AsyncLoggerProvider)?.shutdown()
        fileWriter?.close()
    }
}
