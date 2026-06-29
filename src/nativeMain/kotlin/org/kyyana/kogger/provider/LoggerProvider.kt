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
package org.kyyana.kogger.provider

import org.kyyana.kogger.LogType
import kotlin.reflect.KClass
import kotlin.time.Instant

/**
 * A sink for log events. Implementations decide how to format and where to emit a line
 * (console, file, ...). Providers can be chained as decorators; see [AsyncLoggerProvider].
 */
fun interface LoggerProvider {
    /**
     * Handles a single log event.
     *
     * @param clazz the source class, used to label the line.
     * @param logType the severity level.
     * @param throwable optional error whose stack trace should be included.
     * @param instant the time the event was created.
     * @param message the already-evaluated log message.
     */
    fun log(
        clazz: KClass<*>,
        logType: LogType,
        throwable: Throwable?,
        instant: Instant,
        message: String,
    )
}
