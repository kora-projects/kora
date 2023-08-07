package ru.tinkoff.kora.resilient.symbol.processor.aop.testdata

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.common.annotation.Root
import ru.tinkoff.kora.resilient.retry.annotation.Retry
import java.util.concurrent.atomic.AtomicInteger

@Component
@Root
open class RetryTarget {

    private val logger = LoggerFactory.getLogger(RetryTarget::class.java)
    private val retryAttempts = AtomicInteger()

    @Retry("custom1")
    open fun retrySyncVoid(arg: String) {
        logger.info("Retry Void executed for: {}", arg)
        check(retryAttempts.getAndDecrement() <= 0) { "Ops" }
    }

    @Retry("custom1")
    open fun retrySync(arg: String): String {
        logger.info("Retry Sync executed for: {}", arg)
        check(retryAttempts.getAndDecrement() <= 0) { "Ops" }
        return arg
    }

    @Retry("custom1")
    open suspend fun retrySuspend(arg: String): String {
        logger.info("Retry Suspend executed for: {}", arg)
        check(retryAttempts.getAndDecrement() <= 0) { "Ops" }
        return arg
    }

    @Retry("custom1")
    open fun retryFlow(arg: String): Flow<String> {
        return flow {
            logger.info("Retry Flow executed for: {}", arg)
            check(retryAttempts.getAndDecrement() <= 0) { "Ops" }
            emit(arg)
        }
    }

    open fun setRetryAttempts(attempts: Int) {
        retryAttempts.set(attempts)
    }

    open fun reset() {
        retryAttempts.set(2)
    }
}
