package io.koraframework.scheduling.symbol.processor

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.squareup.kotlinpoet.asClassName
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.quartz.DisallowConcurrentExecution
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import io.koraframework.ksp.common.symbolProcess
import io.koraframework.scheduling.symbol.processor.controller.ScheduledJdkAtFixedDelayTest
import io.koraframework.scheduling.symbol.processor.controller.ScheduledJdkAtFixedRateTest
import io.koraframework.scheduling.symbol.processor.controller.ScheduledJdkOnceTest
import io.koraframework.scheduling.symbol.processor.controller.ScheduledJdkWithCronTest
import io.koraframework.scheduling.symbol.processor.controller.ScheduledQuartzWithCron
import io.koraframework.scheduling.symbol.processor.controller.ScheduledQuartzWithTrigger
import kotlin.reflect.KClass

internal class SchedulingSymbolProcessorTest : AbstractSymbolProcessorTest() {
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
    internal fun testScheduledJdkWithCronTest() {
        process(ScheduledJdkWithCronTest::class)
    }

    @Test
    internal fun testScheduledQuartzWithCron() {
        process(ScheduledQuartzWithCron::class)
    }

    @Test
    internal fun testScheduledQuartzWithTrigger() {
        process(ScheduledQuartzWithTrigger::class)
    }

    private fun <T : Any> process(type: KClass<T>) {
        val cl = symbolProcess(listOf(SchedulingSymbolProcessorProvider()), listOf(type))

        val module = cl.loadClass(type.asClassName().packageName + ".$" + type.simpleName + "_SchedulingModule")
    }

    @Test
    fun testScheduledQuartzDisallowConcurrentExecutionOnClass() {
        val cr = compile0(
            listOf<SymbolProcessorProvider>(SchedulingSymbolProcessorProvider()), """
            @org.quartz.DisallowConcurrentExecution
            class TestClass {
                @io.koraframework.scheduling.quartz.ScheduleWithTrigger(TestClass::class)
                fun job() {}
            }
            
            """.trimIndent()
        )
        cr.assertSuccess()
        val clazz = loadClass("\$TestClass_job_Job")
        Assertions.assertThat(clazz).hasAnnotation(DisallowConcurrentExecution::class.java)
    }

    @Test
    fun testScheduledQuartzDisallowConcurrentExecutionOnMethod() {
        val cr = compile0(
            listOf<SymbolProcessorProvider>(SchedulingSymbolProcessorProvider()), """
            class TestClass {
                @io.koraframework.scheduling.quartz.ScheduleWithTrigger(TestClass::class)
                @io.koraframework.scheduling.quartz.DisallowConcurrentExecution
                fun job() {}
            }
            
            """.trimIndent()
        )
        cr.assertSuccess()
        val clazz = loadClass("\$TestClass_job_Job")
        Assertions.assertThat(clazz).hasAnnotation(DisallowConcurrentExecution::class.java)
    }

}
