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

    @Retry("custom2")
    open fun retrySync(arg: String): String {
        logger.info("Retry Sync executed for: {}", arg)
        check(retryAttempts.getAndDecrement() <= 0) { "Ops" }
        return arg
    }

    @Retry("custom3")
    open suspend fun retrySuspend(arg: String): String {
        logger.info("Retry Suspend executed for: {}", arg)
        check(retryAttempts.getAndDecrement() <= 0) { "Ops" }
        return arg
    }

    @Retry("custom4")
    open fun retryFlow(arg: String): Flow<String> {
        return flow {
            logger.info("Retry Flow executed for: {}", arg)
            check(retryAttempts.getAndDecrement() <= 0) { "Ops" }
            emit(arg)
        }
    }

    // Throws here is an alias for kotlin.jvm.Throws
    // Method should compile normally
    @Throws(IllegalStateException::class)
    @Retry("throws")
    open fun throws(arg: String) {
        logger.info("Retry Void executed for: {}", arg)
        check(retryAttempts.getAndDecrement() <= 0) { "Ops" }
    }

    open fun setRetryAttempts(attempts: Int) {
        retryAttempts.set(attempts)
    }

    open fun reset() {
        retryAttempts.set(2)
    }
}
