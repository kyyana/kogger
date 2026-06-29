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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.kyyana.kogger.LogType
import kotlin.reflect.KClass
import kotlin.time.Instant

/**
 * A [LoggerProvider] decorator that moves log handling off the calling thread.
 *
 * Implements a multi-producer/single-consumer pipeline: [log] only enqueues a task onto a
 * [Channel] (never blocking the caller), while one coroutine on [Dispatchers.Default] drains
 * the channel and forwards each event to [downstream]. This keeps slow log I/O (disk writes,
 * flushes) off latency-sensitive threads.
 *
 * @param capacity size of the channel buffer.
 * @param onBufferOverflow what to do when the buffer is full; [BufferOverflow.DROP_OLDEST]
 *   trades dropped log lines for never blocking the producer.
 * @param downstream the wrapped provider that actually emits the events.
 */
class AsyncLoggerProvider(
    capacity: Int,
    onBufferOverflow: BufferOverflow,
    val downstream: LoggerProvider,
) : LoggerProvider {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val channel = Channel<Runnable>(capacity = capacity, onBufferOverflow = onBufferOverflow)
    private val workerJob: Job =
        scope.launch {
            for (entry in channel) {
                entry.run()
            }
        }

    override fun log(
        clazz: KClass<*>,
        logType: LogType,
        throwable: Throwable?,
        instant: Instant,
        message: String,
    ) {
        channel.trySend(
            Runnable {
                downstream.log(clazz, logType, throwable, instant, message)
            },
        )
    }

    /**
     * Closes the channel so the worker drains any remaining entries, then blocks until it
     * finishes. Not async-signal-safe: do not call from a POSIX signal handler.
     */
    fun shutdown() {
        channel.close()
        runBlocking {
            workerJob.join()
        }
    }
}
