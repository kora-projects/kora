package ru.tinkoff.kora.scheduling.ksp

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.squareup.kotlinpoet.asClassName
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.quartz.DisallowConcurrentExecution
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import ru.tinkoff.kora.ksp.common.symbolProcess
import ru.tinkoff.kora.scheduling.ksp.controller.*
import kotlin.reflect.KClass

internal class SchedulingKspTest : AbstractSymbolProcessorTest() {
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
        val cl = symbolProcess(listOf(SchedulingKspProvider()), listOf(type))

        val module = cl.loadClass(type.asClassName().packageName + ".$" + type.simpleName + "_SchedulingModule")
    }


    @Test
    fun testDisallowConcurrentExecutionOnClass() {
        val cr = compile0(
            listOf<SymbolProcessorProvider>(SchedulingKspProvider()), """
            @org.quartz.DisallowConcurrentExecution
            class TestClass {
                @ru.tinkoff.kora.scheduling.quartz.ScheduleWithTrigger(TestClass::class)
                fun job() {}
            }
            
            """.trimIndent()
        )
        cr.assertSuccess()
        val clazz = loadClass("\$TestClass_job_Job")
        Assertions.assertThat(clazz).hasAnnotation(DisallowConcurrentExecution::class.java)
    }

    @Test
    fun testDisallowConcurrentExecutionOnMethod() {
        val cr = compile0(
            listOf<SymbolProcessorProvider>(SchedulingKspProvider()), """
            class TestClass {
                @ru.tinkoff.kora.scheduling.quartz.ScheduleWithTrigger(TestClass::class)
                @ru.tinkoff.kora.scheduling.quartz.DisallowConcurrentExecution
                fun job() {}
            }
            
            """.trimIndent()
        )
        cr.assertSuccess()
        val clazz = loadClass("\$TestClass_job_Job")
        Assertions.assertThat(clazz).hasAnnotation(DisallowConcurrentExecution::class.java)
    }

}
