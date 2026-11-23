package io.koraframework.camunda.zeebe.worker.symbol.processor

import io.camunda.client.api.command.ThrowErrorCommandStep1
import io.camunda.client.api.response.ActivatedJob
import io.camunda.client.api.worker.JobClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import io.koraframework.camunda.zeebe.worker.KoraJobWorker
import io.koraframework.json.common.JsonReader
import io.koraframework.json.common.JsonWriter
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import java.lang.reflect.Method
import java.util.*

class ZeebeWorkerTests : AbstractSymbolProcessorTest() {

    override fun commonImports(): String {
        return super.commonImports() + """
            import io.koraframework.camunda.zeebe.worker.annotation.*
            import io.koraframework.camunda.zeebe.worker.*
            import io.koraframework.camunda.zeebe.worker.exception.*
        """.trimIndent()
    }

    @Test
    fun workerNoVars() {
        compile0(listOf(ZeebeWorkerSymbolProcessorProvider()),
            """
            @Component
            class Handler {
                        
                @JobWorker("worker")
                fun handle() {
                    // do something
                }
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val clazz = loadClass("\$Handler_handle_KoraJobWorker")
        assertThat(clazz).isNotNull()
        assertThat(Arrays.stream(clazz.interfaces).anyMatch { i -> i.isAssignableFrom(KoraJobWorker::class.java) }).isTrue()
        assertThat(Arrays.stream(clazz.methods).anyMatch { m: Method -> m.name == "fetchVariables" }).isTrue()
        assertThat(Arrays.stream(clazz.methods).anyMatch { m: Method -> m.name == "type" }).isTrue()
        assertThat(Arrays.stream(clazz.methods).anyMatch { m: Method -> m.name == "handle" }).isTrue()
    }

    @Test
    fun workerVars() {
        compile0(listOf(ZeebeWorkerSymbolProcessorProvider()),
            """
            @Component
            class Handler {
                        
                data class SomeVariables(val name: String, val id: String)
                        
                @JobWorker("worker")
                fun handle(@JobVariables vars: SomeVariables) {
                    // do something
                }
            }
            
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val clazz = loadClass("\$Handler_handle_KoraJobWorker")
        assertThat(clazz).isNotNull()
        assertThat(Arrays.stream(clazz.interfaces).anyMatch { i -> i.isAssignableFrom(KoraJobWorker::class.java) }).isTrue()
        assertThat(Arrays.stream(clazz.methods).anyMatch { m: Method -> m.name == "fetchVariables" }).isTrue()
        assertThat(Arrays.stream(clazz.methods).anyMatch { m: Method -> m.name == "type" }).isTrue()
        assertThat(Arrays.stream(clazz.methods).anyMatch { m: Method -> m.name == "handle" }).isTrue()
    }

    @Test
    fun workerVar() {
        compile0(listOf(ZeebeWorkerSymbolProcessorProvider()),
            """
            @Component
            class Handler {
                        
                @JobWorker("worker")
                fun handle(@JobVariable var1: String, @JobVariable("var12345") var2: String?) {
                    // do something
                }
            }
            
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val clazz = loadClass("\$Handler_handle_KoraJobWorker")
        assertThat(clazz).isNotNull()
        assertThat(Arrays.stream(clazz.interfaces).anyMatch { i -> i.isAssignableFrom(KoraJobWorker::class.java) }).isTrue()
        assertThat(Arrays.stream(clazz.methods).anyMatch { m: Method -> m.name == "fetchVariables" }).isTrue()
        assertThat(Arrays.stream(clazz.methods).anyMatch { m: Method -> m.name == "type" }).isTrue()
        assertThat(Arrays.stream(clazz.methods).anyMatch { m: Method -> m.name == "handle" }).isTrue()
    }

    @Test
    fun workerReturnVars() {
        compile0(listOf(ZeebeWorkerSymbolProcessorProvider()),
            """
            @Component
            class Handler {
                        
                data class SomeResponse(val name: String, val id: String)
                
                @JobWorker("worker")
                fun handle(): SomeResponse  {
                    return SomeResponse("1", "2")
                }
            }
            
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val clazz = loadClass("\$Handler_handle_KoraJobWorker")
        assertThat(clazz).isNotNull()
        assertThat(Arrays.stream(clazz.interfaces).anyMatch { i -> i.isAssignableFrom(KoraJobWorker::class.java) }).isTrue()
        assertThat(Arrays.stream(clazz.methods).anyMatch { m: Method -> m.name == "fetchVariables" }).isTrue()
        assertThat(Arrays.stream(clazz.methods).anyMatch { m: Method -> m.name == "type" }).isTrue()
        assertThat(Arrays.stream(clazz.methods).anyMatch { m: Method -> m.name == "handle" }).isTrue()
    }

    @Test
    fun workerContext() {
        compile0(listOf(ZeebeWorkerSymbolProcessorProvider()),
            """
            @Component
            class Handler {
                        
                @JobWorker("worker")
                fun handle(context: JobContext) {
                    // do something
                }
            }
            
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val clazz = loadClass("\$Handler_handle_KoraJobWorker")
        assertThat(clazz).isNotNull()
        assertThat(Arrays.stream(clazz.interfaces).anyMatch { i -> i.isAssignableFrom(KoraJobWorker::class.java) }).isTrue()
        assertThat(Arrays.stream(clazz.methods).anyMatch { m: Method -> m.name == "fetchVariables" }).isTrue()
        assertThat(Arrays.stream(clazz.methods).anyMatch { m: Method -> m.name == "type" }).isTrue()
        assertThat(Arrays.stream(clazz.methods).anyMatch { m: Method -> m.name == "handle" }).isTrue()
    }

    @Test
    fun workerJobWorkerExceptionIsTurnedIntoBpmnError() {
        compile0(listOf(ZeebeWorkerSymbolProcessorProvider()),
            """
            @Component
            class Handler {

                @JobWorker("worker")
                fun handle() {
                    throw JobWorkerException("DOESNT_WORK")
                }
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()

        val worker = new("\$Handler_handle_KoraJobWorker", new("Handler")) as KoraJobWorker

        val errorStep2 = Mockito.mock(ThrowErrorCommandStep1.ThrowErrorCommandStep2::class.java)
        Mockito.`when`(errorStep2.errorMessage(Mockito.anyString())).thenReturn(errorStep2)
        val errorStep1 = Mockito.mock(ThrowErrorCommandStep1::class.java)
        Mockito.`when`(errorStep1.errorCode("DOESNT_WORK")).thenReturn(errorStep2)
        val job = Mockito.mock(ActivatedJob::class.java)
        val client = Mockito.mock(JobClient::class.java)
        Mockito.`when`(client.newThrowErrorCommand(job)).thenReturn(errorStep1)

        val command = worker.handle(client, job)

        assertThat(command).isSameAs(errorStep2)
    }

    @Test
    fun workerSingleCharVariableNames() {
        compile0(listOf(ZeebeWorkerSymbolProcessorProvider()),
            """
            @Component
            class Handler {
                @JobVariable("y")
                @JobWorker("worker")
                fun handle(@JobVariable x: String): String = x
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val worker = new("\$Handler_handle_KoraJobWorker", new("Handler"), Mockito.mock(JsonWriter::class.java), Mockito.mock(JsonReader::class.java)) as KoraJobWorker
        assertThat(worker.fetchVariables()).containsExactly("x")
    }

    @Test
    fun workerVarsAndVarFetchesAllVariables() {
        compile0(listOf(ZeebeWorkerSymbolProcessorProvider()),
            """
            @Component
            class Handler {
                data class Vars(val id: String, val name: String)

                @JobWorker("worker")
                fun handle(@JobVariables all: Vars, @JobVariable id: String) {}
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val worker = new("\$Handler_handle_KoraJobWorker", new("Handler"), Mockito.mock(JsonReader::class.java), Mockito.mock(JsonReader::class.java)) as KoraJobWorker
        assertThat(worker.fetchVariables()).isEmpty()
    }

    @Test
    fun workerOverloadedMethods() {
        compile0(listOf(ZeebeWorkerSymbolProcessorProvider()),
            """
            @Component
            class Handler {
                @JobWorker("a")
                fun handle(@JobVariable id: String) {}

                @JobWorker("b")
                fun handle(ctx: JobContext) {}
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()
        assertThat(loadClass("\$Handler_handle_KoraJobWorker")).isNotNull()
        assertThat(loadClass("\$Handler_handle_1_KoraJobWorker")).isNotNull()
    }
}
