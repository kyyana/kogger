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

import org.kyyana.kogger.provider.LoggerProvider
import kotlin.reflect.KClass
import kotlin.time.Clock

/**
 * User-facing logging API. Each instance is tagged with a [clazz] used to label output.
 *
 * Global configuration and the active [LoggerProvider] live in [Kogger] and are
 * shared by every logger. Configure once at startup (see [Kogger.enableFileLogging] /
 * [Kogger.enableAsyncLogging]) and call [Kogger.shutdown] before the process exits.
 */
class KotlinLogger(
    val clazz: KClass<*>,
) {
    /**
     * Logs at [logType] with an optional [throwable]. [message] is only evaluated if the
     * level passes [Kogger.minLevel], so building it has no cost when the level is disabled.
     */
    fun log(
        logType: LogType,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        if (logType.ordinal < Kogger.minLevel.ordinal) return
        Kogger.loggerProvider?.log(clazz, logType, throwable, Clock.System.now(), message())
    }

    /** Logs at [LogType.TRACE]. */
    fun trace(message: () -> String) = log(LogType.TRACE, message = message)

    /** Logs at [LogType.DEBUG]. */
    fun debug(message: () -> String) = log(LogType.DEBUG, message = message)

    /** Logs at [LogType.INFO]. */
    fun info(message: () -> String) = log(LogType.INFO, message = message)

    /** Logs at [LogType.WARN]. */
    fun warn(message: () -> String) = log(LogType.WARN, message = message)

    /** Logs at [LogType.ERROR], optionally attaching a [throwable]'s stack trace. */
    fun error(
        throwable: Throwable? = null,
        message: () -> String,
    ) = log(LogType.ERROR, throwable, message)

    /** Logs at [LogType.FATAL], optionally attaching a [throwable]'s stack trace. */
    fun fatal(
        throwable: Throwable? = null,
        message: () -> String,
    ) = log(LogType.FATAL, throwable, message)
}
