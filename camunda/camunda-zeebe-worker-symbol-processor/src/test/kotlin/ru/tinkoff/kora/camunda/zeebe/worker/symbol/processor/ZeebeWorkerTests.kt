package ru.tinkoff.kora.camunda.zeebe.worker.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.camunda.zeebe.worker.KoraJobWorker
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import java.lang.reflect.Method
import java.util.*

class ZeebeWorkerTests : AbstractSymbolProcessorTest() {

    override fun commonImports(): String {
        return super.commonImports() + """
            import ru.tinkoff.kora.camunda.zeebe.worker.annotation.*
            import ru.tinkoff.kora.camunda.zeebe.worker.*
        """.trimIndent()
    }

    @Test
    fun workerNoVars() {
        compile0(
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
        compile0(
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
        compile0(
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
        compile0(
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
        compile0(
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
}
