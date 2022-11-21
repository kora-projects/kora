package ru.tinkoff.kora.scheduling.ksp

import com.squareup.kotlinpoet.asClassName
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.ksp.common.symbolProcess
import ru.tinkoff.kora.scheduling.ksp.controller.*
import kotlin.reflect.KClass

internal class SchedulingKspTest {
    @Test
    internal fun test() {
        process(ScheduledJdkAtFixedDelayTest::class)
        process(ScheduledJdkAtFixedRateTest::class)
        process(ScheduledJdkOnceTest::class)
        process(ScheduledWithCron::class)
        process(ScheduledWithTrigger::class)
    }

    @Throws(Exception::class)
    fun <T : Any> process(type: KClass<T>) {
        val cl = symbolProcess(type, SchedulingKspProvider())

        val module = cl.loadClass(type.asClassName().packageName + ".$" + type.simpleName + "_SchedulingModule")
    }
}
