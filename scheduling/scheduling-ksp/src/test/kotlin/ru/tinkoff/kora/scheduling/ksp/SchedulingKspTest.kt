package ru.tinkoff.kora.scheduling.ksp

import com.squareup.kotlinpoet.asClassName
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.ksp.common.symbolProcess
import ru.tinkoff.kora.scheduling.ksp.controller.*
import kotlin.reflect.KClass

internal class SchedulingKspTest {
    @Test
    internal fun testScheduledJdkAtFixedDelayTest() {
        process(ScheduledJdkAtFixedDelayTest::class)
    }

    @Test
    internal fun testScheduledJdkAtFixedRateTest() {
        process(ScheduledJdkAtFixedRateTest::class)
    }

    @Test
    internal fun testScheduledJdkOnceTest() {
        process(ScheduledJdkOnceTest::class)
    }

    @Test
    internal fun testScheduledWithCron() {
        process(ScheduledWithCron::class)
    }

    @Test
    internal fun testScheduledWithTrigger() {
        process(ScheduledWithTrigger::class)
    }

    private fun <T : Any> process(type: KClass<T>) {
        val cl = symbolProcess(type, SchedulingKspProvider())

        val module = cl.loadClass(type.asClassName().packageName + ".$" + type.simpleName + "_SchedulingModule")
    }
}
