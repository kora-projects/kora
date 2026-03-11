package io.koraframework.resilient.symbol.processor.aop.testdata

import io.koraframework.common.Component
import io.koraframework.common.annotation.Root
import io.koraframework.resilient.timeout.annotation.Timeout

@Component
@Root
open class TimeoutTarget {

    @Timeout("custom1")
    open fun getValueSync(): String {
        Thread.sleep(2000)
        return "OK"
    }

    @Timeout("custom2")
    open fun getValueSyncVoid() {
        Thread.sleep(2000)
    }

    // Throws here is an alias for kotlin.jvm.Throws
    // Method should compile normally
    @Throws(IllegalStateException::class)
    @Timeout("custom5")
    open fun getValueSyncThrows(): String {
        Thread.sleep(2000)
        return "OK"
    }
}
