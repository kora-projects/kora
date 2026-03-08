package io.koraframework.resilient.symbol.processor.aop.testdata.`typealias`

import org.slf4j.LoggerFactory
import io.koraframework.common.Component
import io.koraframework.common.annotation.Root
import io.koraframework.resilient.retry.annotation.Retry
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
