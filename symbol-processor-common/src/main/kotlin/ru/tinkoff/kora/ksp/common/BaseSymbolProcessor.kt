package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.NonExistLocation
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val debug = false

private val logWriter: Writer by lazy {
    val fos = Files.newOutputStream(
        Path.of(System.getProperty("user.home"), "log.txt").toAbsolutePath(),
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.APPEND
    )

    OutputStreamWriter(fos)
}

private val lock = ReentrantLock()

private fun logToFile(message: String) = lock.withLock {
    logWriter.write("${LocalDateTime.now()} $message\n\n")
    logWriter.flush()
}

abstract class BaseSymbolProcessor(environment: SymbolProcessorEnvironment) : SymbolProcessor {
    val kspLogger: KSPLogger = if (!debug) environment.logger else object : KSPLogger {

        override fun error(message: String, symbol: KSNode?) {
            writeMessage("error", message, symbol)
        }

        override fun exception(e: Throwable) {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            e.printStackTrace(pw)
            pw.flush()
            sw.flush()
            writeMessage("exception", sw.toString(), null)
        }

        override fun info(message: String, symbol: KSNode?) {
            writeMessage("info", message, symbol)
        }

        override fun logging(message: String, symbol: KSNode?) {
            writeMessage("logging", message, symbol)
        }

        override fun warn(message: String, symbol: KSNode?) {
            writeMessage("warn", message, symbol)
        }

        private fun writeMessage(level: String, message: String, symbol: KSNode?) {

            val msg = when (val location = symbol?.location) {
                is FileLocation -> "[$level] ${location.filePath}:${location.lineNumber}\n$message"
                is NonExistLocation, null -> "[$level] $message"
            }

            logToFile(msg)
        }
    }


    final override fun process(resolver: Resolver): List<KSAnnotated> {
        try {
            KoraSymbolProcessingEnv.logger = kspLogger
            return processRound(resolver)
        } finally {
            KoraSymbolProcessingEnv.resetLogger()
        }
    }

    abstract fun processRound(resolver: Resolver): List<KSAnnotated>
}
