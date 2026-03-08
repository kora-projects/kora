package io.koraframework.resilient.symbol.processor.aop.testdata

import org.slf4j.LoggerFactory
import io.koraframework.common.Component
import io.koraframework.common.annotation.Root
import io.koraframework.resilient.retry.annotation.Retry
import java.util.concurrent.atomic.AtomicInteger

@Component
@Root
open class RetryTarget {

    private val logger = LoggerFactory.getLogger(RetryTarget::class.java)
    private val stopFailAfterAttempts = AtomicInteger()
    private val retryAttempts = AtomicInteger()

    @Retry("custom1")
    open fun retrySyncVoid(arg: String) {
        logger.info("Retry Void executed for: {}", arg)
        check(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get()) { "Ops" }
    }

    @Retry("custom2")
    open fun retrySync(arg: String): String {
        logger.info("Retry Sync executed for: {}", arg)
        check(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get()) { "Ops" }
        return arg
    }

    // Throws here is an alias for kotlin.jvm.Throws
    // Method should compile normally
    @Throws(IllegalStateException::class)
    @Retry("throws")
    open fun throws(arg: String) {
        logger.info("Retry Void executed for: {}", arg)
        check(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get()) { "Ops" }
    }

    @Retry("customZeroAttempts")
    open fun retrySyncZeroAttempt(arg: String): String {
        logger.info("Retry Sync executed for: {}", arg)
        check(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get()) { "Ops" }
        return arg
    }

    @Retry("customZeroAttempts")
    open suspend fun retrySuspendZeroAttempt(arg: String): String {
        logger.info("Retry Suspend executed for: {}", arg)
        check(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get()) { "Ops" }
        return arg
    }

    @Retry("customDisabled")
    open fun retrySyncDisabled(arg: String): String {
        logger.info("Retry Sync executed for: {}", arg)
        check(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get()) { "Ops" }
        return arg
    }

    @Retry("customDisabled")
    open suspend fun retrySuspendDisabled(arg: String): String {
        logger.info("Retry Suspend executed for: {}", arg)
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
