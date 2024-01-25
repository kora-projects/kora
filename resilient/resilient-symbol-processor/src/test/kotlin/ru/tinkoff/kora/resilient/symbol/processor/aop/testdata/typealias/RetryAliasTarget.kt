package ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.`typealias`

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
    private val retryAttempts = AtomicInteger()

    @RetryAlias("custom1")
    open fun retrySync(arg: String): String {
        logger.info("Retry Sync executed for: {}", arg)
        check(retryAttempts.getAndDecrement() <= 0) { "Ops" }
        return arg
    }

    open fun setRetryAttempts(attempts: Int) {
        retryAttempts.set(attempts)
    }

    open fun reset() {
        retryAttempts.set(2)
    }

}
