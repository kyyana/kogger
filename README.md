[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/platform-Kotlin%2FNative-lightgray?logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/native-overview.html)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kyyana/kogger)](https://central.sonatype.com/artifact/io.github.kyyana/kogger)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

[![Stars](https://img.shields.io/github/stars/kyyana/kogger)](https://github.com/kyyana/kogger/stargazers)
[![Last commit](https://img.shields.io/github/last-commit/kyyana/kogger)](https://github.com/kyyana/kogger/commits)

# kogger

A small logging library for Kotlin/Native. Console output by default, with optional
daily-rolling file logging and optional asynchronous (off-thread) dispatch.

## Installation

Available on Maven Central. Add the dependency to your Kotlin/Native source set:

```kotlin
repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        nativeMain {
            dependencies {
                implementation("io.github.kyyana:kogger:1.0.0")
            }
        }
    }
}
```

Supported targets: `linuxX64` and `linuxArm64`.

## Quick start

```kotlin
class Example {
    private val logger = logger() // inferred from the enclosing class

    fun run() {
        logger.info { "Starting..." }
        logger.debug { "address=$address" }
        logger.error(throwable) { "Failed to start" }
    }
}
```

Messages are lambdas (`() -> String`), so the string is only built when the level is
actually enabled.

## Setup

Configure once at startup, before logging:

```kotlin
Kogger.verifyArgs(args)                              // --trace, --debug, --no-colors
Kogger.enableFileLogging("logs", 30)                // optional
Kogger.enableAsyncLogging(1024, BufferOverflow.DROP_OLDEST) // optional
```

On shutdown, drain and flush pending logs:

```kotlin
Kogger.shutdown()
```

> `shutdown()` blocks until the async worker has drained the queue and the file is
> closed. Call it from a normal execution path (e.g. after the main loop returns),
> **not** from inside a POSIX signal handler — coroutine code is not async-signal-safe
> and will deadlock there. In a signal handler, only flip a flag / stop the loop.

## Log levels

`TRACE < DEBUG < INFO < WARN < ERROR < FATAL`. Messages below `minLevel`
(default `INFO`) are dropped before the message lambda runs.

Each level has its own ANSI color for console output (when `colors` is enabled):

| Level   | Color    |
|---------|----------|
| `TRACE` | gray     |
| `DEBUG` | cyan     |
| `INFO`  | green    |
| `WARN`  | yellow   |
| `ERROR` | red      |
| `FATAL` | dark red |

## Configuration (`Kogger`)

| Property         | Default          | Meaning                                                  |
|------------------|------------------|----------------------------------------------------------|
| `minLevel`       | `INFO`           | Minimum level that gets logged.                          |
| `colors`         | `true`           | ANSI colors in console output.                           |
| `timeFormatter`  | `HH:mm:ss.SSS`   | Timestamp format.                                        |
| `loggerProvider` | console (+ file) | The sink that receives every event passing the filter.   |

`verifyArgs` lowers `minLevel` to `TRACE` on `--trace` or `DEBUG` on `--debug`
(`--trace` takes precedence), and disables colors on `--no-colors`.

### Default output format

The default provider emits one line per event:

```
[12:34:56.789] [Example/INFO]: Example started
```

The timestamp follows `timeFormatter`, `Example` is the source class, and `INFO` is the
level. When `colors` is enabled, the line is wrapped in the level's ANSI color. A custom
provider defines its own format.

### Custom provider

`loggerProvider` is a plain `var`, so you can replace the default sink with your own —
e.g. to emit JSON, ship logs over the network, or route to another logging backend.
It is a `fun interface`, so a lambda works:

```kotlin
Kogger.loggerProvider = LoggerProvider { clazz, logType, throwable, instant, message ->
    // format and emit however you like
}
```

`enableAsyncLogging` wraps whatever provider is currently set, so assign your custom
provider *before* calling it if you want it dispatched off-thread. To reuse the default
file output from a custom provider, write to `Kogger.fileWriter` (populated by
`enableFileLogging`):

```kotlin
Kogger.fileWriter?.write(line)
```

`RollingFileWriter` is not thread-safe. A custom provider that writes to
`Kogger.fileWriter` from multiple threads must either serialize the writes itself or sit
behind `enableAsyncLogging`, whose single worker is then the only writer.

## Architecture

```
logger()  ->  KotlinLogger  ->  Kogger (config + provider)  ->  console (+ file)
```

- **`Kogger`** — the global singleton that holds all configuration (`minLevel`,
  `colors`, `timeFormatter`) and the active `LoggerProvider`. Configure it once at
  startup; every logger shares it.
- **`KotlinLogger`** — the user-facing API, tagged with its source class. Filters by
  `Kogger.minLevel`, then forwards the event to `Kogger.loggerProvider`.
- **`LoggerProvider`** — a `fun interface` that receives a formatted log event. The
  default provider formats the line, writes it to the file (if enabled), and prints
  it to the console.
- **`AsyncLoggerProvider`** — an optional decorator that wraps another provider and
  runs it off-thread.
- **`RollingFileWriter`** — handles file output and rotation.

### File logging (`RollingFileWriter`)

- Writes to `<directory>/latest.log`.
- Rotates **per day** and **at startup**: when the date changes, or whenever the process
  starts and finds a leftover `latest.log` (even one from the same day), the current file
  is gzip-archived as `<directory>/yyyy-MM-dd-N.log.gz` (`N` increments for multiple files
  on the same day, e.g. `2026-07-02-1.log.gz`, `2026-07-02-2.log.gz`, ...).
- Keeps only the most recent `maxArchivedFiles` archives, deleting the oldest.
- Single-writer only — not thread-safe. It is meant to run behind `AsyncLoggerProvider`,
  whose single worker is the only writer.

### Async logging (`AsyncLoggerProvider`)

Decouples the calling thread from slow log I/O using a classic producer/consumer:

- `log()` wraps the work in a `Runnable` and `trySend`s it to a `Channel` — non-blocking,
  so callers never wait on disk.
- A single coroutine on `Dispatchers.Default` consumes the channel and runs each task,
  delegating to the wrapped (downstream) provider.
- `BufferOverflow` decides what happens when the channel is full. `DROP_OLDEST`
  prioritizes the program's responsiveness over preserving every log line.
- `shutdown()` closes the channel (so the worker drains remaining entries) and joins the
  worker via `runBlocking`.

`enableAsyncLogging` must be called *after* a base provider exists (i.e. after the
default provider is set, optionally with file logging enabled); it wraps the current one.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).