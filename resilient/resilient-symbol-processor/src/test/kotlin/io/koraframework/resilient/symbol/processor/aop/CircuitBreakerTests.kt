package io.koraframework.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import io.koraframework.application.graph.ApplicationGraphDraw
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.resilient.circuitbreaker.CircuitBreaker
import io.koraframework.resilient.circuitbreaker.exception.CallNotPermittedException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.lang.reflect.InvocationTargetException
import java.util.function.Supplier

@KspExperimental
class CircuitBreakerTests : AbstractSymbolProcessorTest() {

    private val processors = AppRunner().getProcessors()

    override fun commonImports(): String {
        return """
            import com.typesafe.config.ConfigFactory
            import io.koraframework.common.annotation.Component
            import io.koraframework.common.annotation.KoraApp
            import io.koraframework.common.annotation.Root
            import io.koraframework.config.common.Config
            import io.koraframework.config.common.mapper.ConfigValueMapperModule
            import io.koraframework.config.common.origin.SimpleConfigOrigin
            import io.koraframework.config.hocon.HoconConfigFactory
            import io.koraframework.resilient.circuitbreaker.CircuitBreakerModule
            import io.koraframework.resilient.circuitbreaker.annotation.CircuitBreaker

        """.trimIndent()
    }

    @Test
    fun sameConfigPathUsesSingleCircuitBreakerComponent() {
        compile0(
            processors,
            app("""
                resilient {
                  circuitbreaker {
                    custom1 {
                      slidingWindowSize = 1
                      minimumRequiredCalls = 1
                      failureRateThreshold = 100
                      permittedCallsInHalfOpenState = 1
                      waitDurationInOpenState = 1s
                    }
                  }
                }
            """),
            """
            @Component
            @Root
            open class TestTarget1 {
                @CircuitBreaker("resilient.circuitbreaker.custom1")
                open fun getValue(): String = "1"
            }
            """,
            """
            @Component
            @Root
            open class TestTarget2 {
                @CircuitBreaker("resilient.circuitbreaker.custom1")
                open fun getValue(): String = "2"
            }
            """
        )
        compileResult.assertSuccess()

        val graphDraw = loadGraph()
        val graph = graphDraw.init()
        val circuitBreakers = graphDraw.nodes
            .map { graph.get(it) }
            .filterIsInstance<CircuitBreaker>()

        assertThat(circuitBreakers).hasSize(1)
    }

    @Test
    fun rootConfigPathIsAllowed() {
        compile0(
            processors,
            app("""
                payment {
                  slidingWindowSize = 1
                  minimumRequiredCalls = 1
                  failureRateThreshold = 100
                  permittedCallsInHalfOpenState = 1
                  waitDurationInOpenState = 1s
                }
            """),
            """
            @Component
            @Root
            open class TestTarget {
                @CircuitBreaker("payment")
                open fun getValue(): String {
                    throw IllegalStateException("Failed")
                }
            }
            """
        )
        compileResult.assertSuccess()

        val graphDraw = loadGraph()
        val graph = graphDraw.init()
        val service = graphDraw.nodes
            .map { graph.get(it) }
            .first { loadClass("TestTarget").isInstance(it) }

        assertCircuitBreaker(service, "getValue")
    }

    @Test
    fun syncCircuitBreaker() {
        compile0(
            processors,
            app(circuitBreakerConfig("custom1")),
            """
            @Component
            @Root
            open class TestTarget {
                @CircuitBreaker("custom1")
                open fun getValue(): String {
                    throw IllegalStateException("Failed")
                }
            }
            """
        )
        compileResult.assertSuccess()

        val service = loadService("TestTarget")
        assertCircuitBreaker(service, "getValue")
    }

    @Test
    fun voidCircuitBreaker() {
        compile0(
            processors,
            app(circuitBreakerConfig("custom2")),
            """
            @Component
            @Root
            open class TestTarget {
                @CircuitBreaker("custom2")
                open fun getValue() {
                    throw IllegalStateException("Failed")
                }
            }
            """
        )
        compileResult.assertSuccess()

        val service = loadService("TestTarget")
        assertCircuitBreaker(service, "getValue")
    }

    @Test
    fun throwsAnnotationCircuitBreaker() {
        compile0(
            processors,
            app(circuitBreakerConfig("customThrows")),
            """
            @Component
            @Root
            open class TestTarget {
                @Throws(IllegalStateException::class)
                @CircuitBreaker("customThrows")
                open fun getValue(): String {
                    throw IllegalStateException("Failed")
                }
            }
            """
        )
        compileResult.assertSuccess()

        val service = loadService("TestTarget")
        assertCircuitBreaker(service, "getValue")
    }

    @Test
    fun typealiasAnnotationCircuitBreaker() {
        compile0(
            processors,
            app(circuitBreakerConfig("custom1")),
            """
            typealias CB = CircuitBreaker

            @Component
            @Root
            open class TestTarget {
                @CB("custom1")
                open fun getValue(): String {
                    throw IllegalStateException("Failed")
                }
            }
            """
        )
        compileResult.assertSuccess()

        val service = loadService("TestTarget")
        assertCircuitBreaker(service, "getValue")
    }

    @Test
    fun suspendCircuitBreaker() {
        compile0(
            processors,
            app(circuitBreakerConfig("custom1")),
            """
            @Component
            @Root
            open class TestTarget {
                @CircuitBreaker("custom1")
                open suspend fun getValue(): String {
                    throw IllegalStateException("Failed")
                }

                open fun call(): String = kotlinx.coroutines.runBlocking {
                    getValue()
                }
            }
            """
        )
        compileResult.assertSuccess()

        val service = loadService("TestTarget")
        assertCircuitBreaker(service, "call")
    }

    @Test
    fun generatedTagNameCollisionFails() {
        assertThatThrownBy {
            compile0(
                processors,
                app("""
                    resilient {
                      circuitbreaker {
                        foo-bar {
                          slidingWindowSize = 1
                          minimumRequiredCalls = 1
                          failureRateThreshold = 100
                          permittedCallsInHalfOpenState = 1
                          waitDurationInOpenState = 1s
                        }
                        foo {
                          bar {
                            slidingWindowSize = 1
                            minimumRequiredCalls = 1
                            failureRateThreshold = 100
                            permittedCallsInHalfOpenState = 1
                            waitDurationInOpenState = 1s
                          }
                        }
                      }
                    }
                """),
                """
                @Component
                @Root
                open class TestTarget1 {
                    @CircuitBreaker("resilient.circuitbreaker.foo-bar")
                    open fun getValue(): String = "1"
                }
                """,
                """
                @Component
                @Root
                open class TestTarget2 {
                    @CircuitBreaker("resilient.circuitbreaker.foo.bar")
                    open fun getValue(): String = "2"
                }
                """
            )
        }
            .isInstanceOf(ProcessingErrorException::class.java)
            .hasMessageContaining("generate the same tag")
    }

    @Test
    fun reservedCircuitBreakerRootPathFails() {
        assertThatThrownBy {
            compile0(
                processors,
                app("resilient.circuitbreaker.custom1 {}"),
                """
                @Component
                @Root
                open class TestTarget {
                    @CircuitBreaker("resilient.circuitbreaker")
                    open fun getValue(): String = "1"
                }
                """
            )
        }
            .isInstanceOf(ProcessingErrorException::class.java)
            .hasMessageContaining("config path 'resilient.circuitbreaker' is reserved")
    }

    @Test
    fun reservedCircuitBreakerTelemetryPathFails() {
        assertThatThrownBy {
            compile0(
                processors,
                app("resilient.circuitbreaker.custom1 {}"),
                """
                @Component
                @Root
                open class TestTarget {
                    @CircuitBreaker("resilient.circuitbreaker.telemetry")
                    open fun getValue(): String = "1"
                }
                """
            )
        }
            .isInstanceOf(ProcessingErrorException::class.java)
            .hasMessageContaining("config path 'resilient.circuitbreaker.telemetry' is reserved")
    }

    private fun app(config: String): String {
        return """
            @KoraApp
            interface AppWithConfig : ConfigValueMapperModule, CircuitBreakerModule {
                fun config(): Config {
                    return HoconConfigFactory.fromHocon(SimpleConfigOrigin("test"), ConfigFactory.parseString(${"\"\"\""}
                        $config
                    ${"\"\"\""}).resolve())
                }
            }
        """
    }

    private fun circuitBreakerConfig(configPath: String): String {
        return """
            $configPath {
              slidingWindowSize = 1
              minimumRequiredCalls = 1
              failureRateThreshold = 100
              permittedCallsInHalfOpenState = 1
              waitDurationInOpenState = 1s
            }
        """
    }

    private fun loadGraph(): ApplicationGraphDraw {
        val clazz = compileResult.assertSuccess().classLoader.loadClass(testPackage() + ".AppWithConfigGraph")
        @Suppress("UNCHECKED_CAST")
        return (clazz.constructors.first().newInstance() as Supplier<ApplicationGraphDraw>).get()
    }

    private fun loadService(className: String): Any {
        val graphDraw = loadGraph()
        val graph = graphDraw.init()
        val serviceClass = loadClass(className)
        return graphDraw.nodes
            .map { graph.get(it) }
            .first { serviceClass.isInstance(it) }
    }

    private fun assertCircuitBreaker(service: Any, methodName: String) {
        try {
            service.javaClass.getMethod(methodName).invoke(service)
            fail("Should not happen")
        } catch (e: InvocationTargetException) {
            assertThat(e.targetException).isInstanceOf(IllegalStateException::class.java)
        }

        try {
            service.javaClass.getMethod(methodName).invoke(service)
            fail("Should not happen")
        } catch (e: InvocationTargetException) {
            assertThat(e.targetException).isInstanceOf(CallNotPermittedException::class.java)
        }
    }
}
