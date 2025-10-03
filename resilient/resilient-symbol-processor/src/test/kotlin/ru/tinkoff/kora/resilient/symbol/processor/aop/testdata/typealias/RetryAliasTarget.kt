package ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.`typealias`

import org.slf4j.LoggerFactory
import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.common.annotation.Root
import ru.tinkoff.kora.resilient.retry.annotation.Retry
import java.util.concurrent.atomic.AtomicInteger

typealias RetryAlias = Retry

@Component
@Root
open class RetryAliasTarget {

    private val logger = LoggerFactory.getLogger(RetryAliasTarget::class.java)
    private val stopFailAfterAttempts = AtomicInteger()
    private val retryAttempts = AtomicInteger()

    @RetryAlias("custom1")
    open fun retrySync(arg: String): String {
        logger.info("Retry Sync executed for: {}", arg)
        check(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get()) { "Ops" }
        return arg
    }

    open fun setFailAttempts(attempts: Int) {
        retryAttempts.set(-1)
        stopFailAfterAttempts.set(attempts)
    }

    fun getRetryAttempts(): Int {
        return retryAttempts.get()
    }
}
