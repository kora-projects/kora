package io.koraframework.scheduling.symbol.processor

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.squareup.kotlinpoet.asClassName
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.quartz.DisallowConcurrentExecution
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.ksp.common.symbolProcess
import io.koraframework.scheduling.symbol.processor.jdk.ScheduledJdkAtFixedDelayTest
import io.koraframework.scheduling.symbol.processor.jdk.ScheduledJdkAtFixedRateTest
import io.koraframework.scheduling.symbol.processor.jdk.ScheduledJdkOnceTest
import io.koraframework.scheduling.symbol.processor.jdk.ScheduledJdkWithCronTest
import io.koraframework.scheduling.symbol.processor.db.ScheduledDbTest
import io.koraframework.scheduling.symbol.processor.quartz.ScheduledQuartzWithCron
import io.koraframework.scheduling.symbol.processor.quartz.ScheduledQuartzWithTrigger
import kotlin.reflect.KClass
import org.assertj.core.api.Assertions.assertThatThrownBy

internal class SchedulingSymbolProcessorTest : AbstractSymbolProcessorTest() {
    @Test
    fun testSuspendFunctionIsRejected() {
        assertThatThrownBy {
            compile0(
                listOf(SchedulingSymbolProcessorProvider()),
                """
                class TestClass {
                    @io.koraframework.scheduling.jdk.annotation.ScheduleJdkAtFixedRate(period = "1s")
                    suspend fun job() {}
                }
                """.trimIndent()
            )
        }.isInstanceOfSatisfying(ProcessingErrorException::class.java) {
            assertThat(it.message)
                .contains("Suspend methods are not supported by the scheduling generator")
                .contains("--enable-preview")
                .contains("StructuredTaskScope.open")
                .contains("remove suspend from the function")
        }
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|', textBlock = """
        io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithFixedDelay(delay = 1000)         | private fun job() {}
        io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithFixedDelay(delay = 1000)         | protected fun job() {}
        io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithFixedDelay(delay = 1000)         | fun job(arg: String) {}
        io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithFixedDelay(delay = 1000) | fun job(arg: String) {}
        io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithTrigger(TestClass::class)  | private fun job() {}
        io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithTrigger(TestClass::class)  | fun job(arg: String) {}"""
    )
    fun testInvalidScheduledFunctionIsRejected(annotation: String, function: String) {
        assertThatThrownBy {
            compile0(
                listOf(SchedulingSymbolProcessorProvider()),
                """
                open class TestClass {
                    @$annotation
                    $function
                }
                """.trimIndent()
            )
        }.isInstanceOfSatisfying(ProcessingErrorException::class.java) {
            assertThat(it.message)
                .contains("Invalid scheduled function")
                .contains("TestClass.job")
        }
    }

    @Test
    fun testScheduledFunctionWithDefaultArgumentsOrJobExecutionContext() {
        val cr = compile0(
            listOf<SymbolProcessorProvider>(SchedulingSymbolProcessorProvider()), """
            class TestClass {
                @io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithFixedDelay(delay = 1000)
                fun jdk(limit: Int = 100) {}

                @io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithTrigger(TestClass::class)
                fun quartz(context: org.quartz.JobExecutionContext) {}
            }
            """.trimIndent()
        )
        cr.assertSuccess()
    }

    @Test
    fun testNestedClassesWithSameSimpleNameGetDistinctModules() {
        val cr = compile0(
            listOf<SymbolProcessorProvider>(SchedulingSymbolProcessorProvider()), """
            class OrderService {
                class Jobs {
                    @io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithFixedDelay(delay = 1000)
                    fun cleanup() {}
                }
            }
            """.trimIndent(), """
            class UserService {
                class Jobs {
                    @io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithFixedDelay(delay = 1000)
                    fun cleanup() {}
                }
            }
            """.trimIndent(), """
            class Jobs {
                @io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithTrigger(Jobs::class)
                fun job() {}
            }
            """.trimIndent()
        )
        cr.assertSuccess()
        assertThat(loadClass("\$OrderService_Jobs_SchedulingModule")).isInterface()
        assertThat(loadClass("\$UserService_Jobs_SchedulingModule")).isInterface()
        assertThat(loadClass("\$Jobs_SchedulingModule")).isInterface()
    }

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

    @Test
    internal fun testScheduledDb() {
        process(ScheduledDbTest::class)
    }

    private fun <T : Any> process(type: KClass<T>) {
        val cl = symbolProcess(listOf(SchedulingSymbolProcessorProvider()), listOf(type))

        val module = cl.loadClass(type.asClassName().packageName + ".$" + type.simpleName + "_SchedulingModule")
    }

    @Test
    fun testJobOfConditionalComponentIsConditional() {
        val cr = compile0(
            listOf<SymbolProcessorProvider>(SchedulingSymbolProcessorProvider()), """
            @io.koraframework.common.annotation.Conditional(tag = TestClass::class)
            open class TestClass {
                @io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithFixedDelay(delay = 1000)
                fun jdk() {}

                @io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithTrigger(TestClass::class)
                fun quartz() {}
            }
            
            """.trimIndent()
        )
        cr.assertSuccess()
        val module = loadClass("\$TestClass_SchedulingModule")
        val jobs = module.declaredMethods.filter { it.name.endsWith("_Job") }
        assertThat(jobs).hasSize(2)
        for (job in jobs) {
            val conditional = job.getAnnotation(io.koraframework.common.annotation.Conditional::class.java)
            assertThat(conditional).`as`(job.name).isNotNull()
            assertThat(conditional.tag.java.simpleName).isEqualTo("TestClass")
        }
    }

    @Test
    fun testScheduledQuartzDisallowConcurrentExecutionOnClass() {
        val cr = compile0(
            listOf<SymbolProcessorProvider>(SchedulingSymbolProcessorProvider()), """
            @org.quartz.DisallowConcurrentExecution
            class TestClass {
                @io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithTrigger(TestClass::class)
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
                @io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithTrigger(TestClass::class)
                @io.koraframework.scheduling.quartz.annotation.DisallowConcurrentExecution
                fun job() {}
            }
            
            """.trimIndent()
        )
        cr.assertSuccess()
        val clazz = loadClass("\$TestClass_job_Job")
        Assertions.assertThat(clazz).hasAnnotation(DisallowConcurrentExecution::class.java)
    }

    @Test
    fun testScheduledDbNameLongerThanColumnIsRejected() {
        val name = "a".repeat(351)
        assertThatThrownBy {
            compile0(
                listOf(SchedulingSymbolProcessorProvider()),
                """
                class TestClass {
                    @io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithFixedDelay(delay = 1000, name = "$name")
                    fun job() {}
                }
                """.trimIndent()
            )
        }.isInstanceOfSatisfying(ProcessingErrorException::class.java) {
            assertThat(it.message).contains("maximum is 350")
        }
    }

    @Test
    fun testScheduledDbConfigHasNoName() {
        val cr = compile0(
            listOf<SymbolProcessorProvider>(SchedulingSymbolProcessorProvider()), """
            class TestClass {
                @io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithFixedDelay(delay = 1000, config = "jobs.job")
                fun job() {}
            }
            """.trimIndent()
        )
        cr.assertSuccess()
        val config = loadClass("\$TestClass_job_Config")
        assertThat(config.methods).noneMatch { it.name == "name" }
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|', textBlock = """
        io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithCron         | 0 0 12 L * ?  | ''       | JDK scheduler expects
        io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithCron         | 0 0 24 * * ?  | jobs.job | JDK scheduler expects
        io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithCron   | 0 0 12 * * *  | ''       | Quartz expects
        io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithCron   | 0 0 12 * *    | jobs.job | Quartz expects
        io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithCron | 0 0 12 * *    | ''       | database scheduler expects
        io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithCron | 0 0 12 ? * 8  | jobs.job | database scheduler expects"""
    )
    fun testInvalidCronIsRejected(annotation: String, cron: String, config: String, format: String) {
        assertThatThrownBy {
            compile0(
                listOf(SchedulingSymbolProcessorProvider()),
                """
                class TestClass {
                    @$annotation(value = "$cron", config = "$config")
                    fun job() {}
                }
                """.trimIndent()
            )
        }.isInstanceOfSatisfying(ProcessingErrorException::class.java) {
            assertThat(it.message)
                .contains("Invalid CRON expression '$cron'")
                .contains("TestClass#job()")
                .contains(format)
                .contains("┌───────────── second (0-59")
                .contains("│ │ │ │ │ ┌───────────── day of the week")
                .contains("Examples:")
                .contains("runs every day at 15:00")
                .contains("runs every hour from 9:00 through 17:00 on weekdays")
        }
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|', textBlock = """
        io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithCron         | 0 0 12 * * ?
        io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithCron         | 0 0 * * *
        io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithCron   | 0 0 12 ? * MON#2
        io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithCron   | 0 0 12 L * ? 2030
        io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithCron | 0 0 12 * * MON-FRI
        io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithCron | @daily
        io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithCron | -"""
    )
    fun testValidCronIsAccepted(annotation: String, cron: String) {
        val cr = compile0(
            listOf<SymbolProcessorProvider>(SchedulingSymbolProcessorProvider()), """
            class TestClass {
                @$annotation("$cron")
                fun job() {}
            }
            """.trimIndent()
        )
        cr.assertSuccess()
    }

}
