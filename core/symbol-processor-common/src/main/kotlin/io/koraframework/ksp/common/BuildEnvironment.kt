package io.koraframework.ksp.common

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets

object BuildEnvironment {

    private const val OPTION_LOG_LEVEL = "koraLogLevel"

    private val logger = LoggerFactory.getLogger("io.koraframework")
    private var users = 0

    @Synchronized
    fun init(environment: SymbolProcessorEnvironment) {
        if (users++ > 0) {
            return
        }
        initLog(environment)
    }

    @Synchronized
    fun close() {
        if (users == 0 || --users > 0) {
            return
        }
        val ctx = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return
        val kora = ctx.getLogger("io.koraframework")
        logger.info("Logger shutdown...")
        kora.detachAndStopAllAppenders()
    }

    // KSP doesn't expose the build directory, so unlike annotation processors logs are written to the console only
    private fun initLog(environment: SymbolProcessorEnvironment) {
        val ctx = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return
        val kora = ctx.getLogger("io.koraframework")
        kora.isAdditive = false
        kora.detachAndStopAllAppenders()

        val encoder = PatternLayoutEncoder()
        encoder.pattern = "%d{HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n"
        encoder.charset = StandardCharsets.UTF_8
        encoder.context = ctx
        encoder.start()

        val consoleAppender = ConsoleAppender<ILoggingEvent>()
        consoleAppender.encoder = encoder
        consoleAppender.context = ctx
        consoleAppender.start()
        kora.addAppender(consoleAppender)

        kora.level = Level.valueOf(environment.options.getOrDefault(OPTION_LOG_LEVEL, "INFO"))
    }
}
