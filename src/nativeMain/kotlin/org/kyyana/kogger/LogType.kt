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

/** ANSI escape codes used to colorize console output. */
object AnsiColors {
    const val RESET = "\u001B[0m"
    const val GRAY = "\u001B[90m"
    const val CYAN = "\u001B[36m"
    const val GREEN = "\u001B[32m"
    const val YELLOW = "\u001B[33m"
    const val RED = "\u001B[31m"
    const val DARK_RED = "\u001B[38;5;88m"
}

/**
 * Severity levels, ordered from least to most severe. The [ordinal] is used for level
 * filtering, and each level carries its own [ansiColor] for console output.
 */
enum class LogType(
    val ansiColor: String,
) {
    TRACE(AnsiColors.GRAY),
    DEBUG(AnsiColors.CYAN),
    INFO(AnsiColors.GREEN),
    WARN(AnsiColors.YELLOW),
    ERROR(AnsiColors.RED),
    FATAL(AnsiColors.DARK_RED),
}
