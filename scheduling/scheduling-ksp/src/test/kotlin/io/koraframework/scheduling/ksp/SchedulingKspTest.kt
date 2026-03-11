package io.koraframework.scheduling.ksp

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.squareup.kotlinpoet.asClassName
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.quartz.DisallowConcurrentExecution
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import io.koraframework.ksp.common.symbolProcess
import io.koraframework.scheduling.ksp.controller.ScheduledJdkAtFixedDelayTest
import io.koraframework.scheduling.ksp.controller.ScheduledJdkAtFixedRateTest
import io.koraframework.scheduling.ksp.controller.ScheduledJdkOnceTest
import io.koraframework.scheduling.ksp.controller.ScheduledWithCron
import io.koraframework.scheduling.ksp.controller.ScheduledWithTrigger
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
    fun testDisallowConcurrentExecutionOnMethod() {
        val cr = compile0(
            listOf<SymbolProcessorProvider>(SchedulingKspProvider()), """
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
