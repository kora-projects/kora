package ru.tinkoff.kora.http.server.symbol.procesor.exception

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode

class HttpProcessorException(message: String, private val ksNode: KSNode) : RuntimeException(message, null) {
    fun printError(logger: KSPLogger) {
        logger.error(message!!, ksNode)
    }
}
