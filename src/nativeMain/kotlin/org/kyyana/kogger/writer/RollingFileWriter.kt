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
package org.kyyana.kogger.writer

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import platform.posix.FILE
import platform.posix.F_OK
import platform.posix.access
import platform.posix.closedir
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.fread
import platform.posix.fwrite
import platform.posix.mkdir
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.remove
import platform.posix.stat
import platform.posix.stderr
import platform.zlib.gzclose
import platform.zlib.gzopen
import platform.zlib.gzwrite
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Writes logs to `[directory]/latest.log`, rotating automatically when the day changes (or at
 * startup, if `latest.log` is left over from a previous day).
 *
 * Archiving scheme: `[directory]/yyyy-MM-dd-N.log.gz`, where `N` increments if a file for that
 * day already exists. Only the most recent [maxArchivedFiles] archives are kept.
 *
 * Not thread-safe: it assumes a single writer (intended use is behind `AsyncLoggerProvider`,
 * whose single worker is the only one consuming the channel).
 *
 * @param directory directory holding `latest.log` and the gzip archives; created if missing.
 * @param maxArchivedFiles maximum number of `*.log.gz` archives to retain.
 */
@OptIn(ExperimentalForeignApi::class)
class RollingFileWriter(
    private val directory: String,
    private val maxArchivedFiles: Int,
) {
    private val timeZone = TimeZone.currentSystemDefault()
    private var currentDate: LocalDate
    private var filePointer: CPointer<FILE>

    init {
        mkdir(directory, 493u)
        currentDate = Clock.System.todayIn(timeZone)

        // Only archive at startup if latest.log is from a previous day; otherwise keep
        // appending to it. Uses the file's real date so the archive is named correctly.
        val existingDate = fileModifiedDate(latestLogPath())
        if (existingDate != null && existingDate != currentDate) {
            archiveCurrentLog(existingDate)
        }

        filePointer = openLatestForAppend()
    }

    /**
     * Writes a single line (a trailing newline is added if missing). Checks whether the day
     * has changed before writing and, if so, archives the current file and opens a fresh
     * `latest.log`. The line is flushed to disk immediately.
     */
    fun write(line: String) {
        val today = Clock.System.todayIn(timeZone)
        if (today != currentDate) {
            rotateForNewDay(today)
        }

        val withNewline = if (line.endsWith("\n")) line else "$line\n"
        val bytes = withNewline.encodeToByteArray()
        val written =
            bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), filePointer)
            }
        if (written < bytes.size.toULong()) {
            reportError("wrote $written of ${bytes.size} bytes to ${latestLogPath()}")
        }
        if (fflush(filePointer) != 0) {
            reportError("fflush failed for ${latestLogPath()}")
        }
    }

    /** Closes the underlying file. The writer must not be used after this. */
    fun close() {
        fclose(filePointer)
    }

    private fun rotateForNewDay(newDate: LocalDate) {
        fclose(filePointer)
        archiveCurrentLog(currentDate)
        currentDate = newDate
        filePointer = openLatestForAppend()
    }

    private fun openLatestForAppend(): CPointer<FILE> =
        fopen(latestLogPath(), "a")
            ?: error("Failed to open ${latestLogPath()} for writing")

    private fun latestLogPath() = "$directory/latest.log"

    private fun archiveCurrentLog(date: LocalDate) {
        val dateStr = date.toString()
        var index = 1
        var targetPath: String
        do {
            targetPath = "$directory/$dateStr-$index.log.gz"
            index++
        } while (fileExists(targetPath))

        compressToGzip(latestLogPath(), targetPath)
        remove(latestLogPath())
        enforceMaxArchivedFiles()
    }

    private fun compressToGzip(
        sourcePath: String,
        destPath: String,
    ) {
        val source = fopen(sourcePath, "rb") ?: return
        val dest =
            gzopen(destPath, "wb9") ?: run {
                fclose(source)
                return
            }

        val bufferSize = 8192
        val buffer = ByteArray(bufferSize)
        try {
            while (true) {
                val bytesRead =
                    buffer.usePinned { pinned ->
                        fread(pinned.addressOf(0), 1u, bufferSize.toULong(), source)
                    }
                if (bytesRead.toLong() <= 0L) break
                buffer.usePinned { pinned ->
                    gzwrite(dest, pinned.addressOf(0), bytesRead.toUInt())
                }
            }
        } finally {
            fclose(source)
            gzclose(dest)
        }
    }

    private fun enforceMaxArchivedFiles() {
        val archived =
            listArchivedFiles()
                .sortedWith(compareBy({ it.substringBeforeLast('-') }, { archiveIndex(it) }))
        if (archived.size <= maxArchivedFiles) return

        val toDelete = archived.size - maxArchivedFiles
        archived.take(toDelete).forEach { fileName ->
            remove("$directory/$fileName")
        }
    }

    private fun archiveIndex(fileName: String): Int = fileName.removeSuffix(".log.gz").substringAfterLast('-').toIntOrNull() ?: 0

    private fun listArchivedFiles(): List<String> {
        val dir = opendir(directory) ?: return emptyList()
        val result = mutableListOf<String>()
        try {
            while (true) {
                val entry = readdir(dir) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name.endsWith(".log.gz")) {
                    result.add(name)
                }
            }
        } finally {
            closedir(dir)
        }
        return result
    }

    private fun fileExists(path: String): Boolean = access(path, F_OK) == 0

    private fun fileModifiedDate(path: String): LocalDate? =
        memScoped {
            val st = alloc<stat>()
            if (stat(path, st.ptr) != 0) return@memScoped null
            Instant
                .fromEpochSeconds(st.st_mtim.tv_sec)
                .toLocalDateTime(timeZone)
                .date
        }

    private fun reportError(message: String) {
        fputs("[RollingFileWriter] $message\n", stderr)
    }
}
