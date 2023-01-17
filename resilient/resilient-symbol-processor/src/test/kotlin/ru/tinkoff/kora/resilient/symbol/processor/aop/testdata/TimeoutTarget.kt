package ru.tinkoff.kora.resilient.symbol.processor.aop.testdata

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.resilient.timeout.annotation.Timeout

@Component
open class TimeoutTarget : MockLifecycle {

    @Timeout("custom1")
    open fun getValueSync(): String {
        Thread.sleep(2000)
        return "OK"
    }

    @Timeout("custom1")
    open suspend fun getValueSuspend(): String {
        delay(2000)
        return "OK"
    }

    @Timeout("custom3")
    open fun getValueFLow(): Flow<String> {
        return flow {
            delay(2000)
            emit("OK")
        }
    }
}
