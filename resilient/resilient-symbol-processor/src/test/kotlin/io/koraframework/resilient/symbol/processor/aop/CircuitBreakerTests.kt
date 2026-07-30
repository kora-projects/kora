package io.koraframework.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.application.graph.ApplicationGraphDraw
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.resilient.circuitbreaker.CircuitBreaker
import io.koraframework.resilient.circuitbreaker.exception.CallNotPermittedException
import io.koraframework.resilient.symbol.processor.CircuitBreakerSymbolProcessorProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.lang.reflect.InvocationTargetException
import java.util.function.Supplier

@KspExperimental
class CircuitBreakerTests : AbstractSymbolProcessorTest() {

    private val processors = listOf(
        KoraAppProcessorProvider(),
        CircuitBreakerSymbolProcessorProvider(),
        AopSymbolProcessorProvider(),
    )

    override fun commonImports(): String {
        return """
            import com.typesafe.config.ConfigFactory
            import io.koraframework.common.annotation.Component
            import io.koraframework.common.annotation.KoraApp
            import io.koraframework.common.annotation.Module
            import io.koraframework.common.annotation.Root
            import io.koraframework.common.annotation.Tag
            import io.koraframework.config.common.Config
            import io.koraframework.config.common.mapper.ConfigValueMapperModule
            import io.koraframework.config.common.origin.SimpleConfigOrigin
            import io.koraframework.config.hocon.HoconConfigFactory
            import io.koraframework.resilient.ResilientModule
            import io.koraframework.resilient.circuitbreaker.CircuitBreakerPredicate
            import io.koraframework.resilient.circuitbreaker.annotation.CircuitBreakerSpec
            import io.koraframework.resilient.circuitbreaker.annotation.CircuitBreakable

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
                      countBased {
                        windowSize = 1
                      }
                      minimumRequiredCalls = 1
                      failureRateThreshold = 100
                      permittedCallsInHalfOpenState = 1
                      waitDurationInOpenState = 1s
                    }
                  }
                }
            """),
            """
            @CircuitBreakerSpec("resilient.circuitbreaker.custom1")
            interface TestCircuitBreaker : io.koraframework.resilient.circuitbreaker.CircuitBreaker
            """,
            """
            @Component
            @Root
            open class TestTarget1 {
                @CircuitBreakable(TestCircuitBreaker::class)
                open fun getValue(): String = "1"
            }
            """,
            """
            @Component
            @Root
            open class TestTarget2 {
                @CircuitBreakable(TestCircuitBreaker::class)
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
                  countBased {
                    windowSize = 1
                  }
                  minimumRequiredCalls = 1
                  failureRateThreshold = 100
                  permittedCallsInHalfOpenState = 1
                  waitDurationInOpenState = 1s
                }
            """),
            """
            @CircuitBreakerSpec("payment")
            interface TestCircuitBreaker : io.koraframework.resilient.circuitbreaker.CircuitBreaker
            """,
            """
            @Component
            @Root
            open class TestTarget {
                @CircuitBreakable(TestCircuitBreaker::class)
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
    fun circuitBreakerInterfaceTestIsUsedWhenPredicateIsAbsent() {
        compile0(
            processors,
            app(circuitBreakerConfig("custom1")),
            """
            @CircuitBreakerSpec("custom1")
            interface TestCircuitBreaker : io.koraframework.resilient.circuitbreaker.CircuitBreaker {
                override fun test(throwable: Throwable): Boolean = false
            }
            """,
            """
            @Component
            @Root
            open class TestTarget {
                @CircuitBreakable(TestCircuitBreaker::class)
                open fun getValue(): String {
                    throw IllegalStateException("Failed")
                }
            }
            """
        )
        compileResult.assertSuccess()

        val service = loadService("TestTarget")
        assertThatThrownBy { service.javaClass.getMethod("getValue").invoke(service) }
            .isInstanceOf(InvocationTargetException::class.java)
            .extracting("targetException")
            .isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { service.javaClass.getMethod("getValue").invoke(service) }
            .isInstanceOf(InvocationTargetException::class.java)
            .extracting("targetException")
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun taggedPredicateOverridesCircuitBreakerInterfaceTest() {
        compile0(
            processors,
            appWithPredicateModule(circuitBreakerConfig("custom1")),
            """
            @CircuitBreakerSpec("custom1")
            interface TestCircuitBreaker : io.koraframework.resilient.circuitbreaker.CircuitBreaker {
                override fun test(throwable: Throwable): Boolean = false
            }
            """,
            """
            @Component
            @Root
            open class TestTarget {
                @CircuitBreakable(TestCircuitBreaker::class)
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
    fun syncCircuitBreaker() {
        compile0(
            processors,
            app(circuitBreakerConfig("custom1")),
            circuitBreakerInterface(),
            """
            @Component
            @Root
            open class TestTarget {
                @CircuitBreakable(TestCircuitBreaker::class)
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
            app(circuitBreakerConfig("custom1")),
            circuitBreakerInterface(),
            """
            @Component
            @Root
            open class TestTarget {
                @CircuitBreakable(TestCircuitBreaker::class)
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
            app(circuitBreakerConfig("custom1")),
            circuitBreakerInterface(),
            """
            @Component
            @Root
            open class TestTarget {
                @Throws(IllegalStateException::class)
                @CircuitBreakable(TestCircuitBreaker::class)
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
            circuitBreakerInterface(),
            """
            typealias CB = CircuitBreakable

            @Component
            @Root
            open class TestTarget {
                @CB(TestCircuitBreaker::class)
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
            circuitBreakerInterface(),
            """
            @Component
            @Root
            open class TestTarget {
                @CircuitBreakable(TestCircuitBreaker::class)
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
    fun circuitBreakerInterfaceMustExtendRuntimeCircuitBreaker() {
        assertThatThrownBy {
            compile0(
                processors,
                app(circuitBreakerConfig("custom1")),
                """
                @CircuitBreakerSpec("custom1")
                interface TestCircuitBreaker
                """
            )
        }
            .isInstanceOf(ProcessingErrorException::class.java)
            .hasMessageContaining("must extend io.koraframework.resilient.circuitbreaker.CircuitBreaker")
    }

    @Test
    fun blankCircuitBreakerConfigPathFails() {
        assertThatThrownBy {
            compile0(
                processors,
                app(circuitBreakerConfig("custom1")),
                """
                @CircuitBreakerSpec("")
                interface TestCircuitBreaker : io.koraframework.resilient.circuitbreaker.CircuitBreaker
                """
            )
        }
            .isInstanceOf(ProcessingErrorException::class.java)
            .hasMessageContaining("config path can't be blank")
    }

    private fun app(config: String): String {
        return """
            @KoraApp
            interface AppWithConfig : ConfigValueMapperModule, ResilientModule {
                fun config(): Config {
                    return HoconConfigFactory.fromHocon(SimpleConfigOrigin("test"), ConfigFactory.parseString(${"\"\"\""}
                        resilient.telemetry {
                          circuitBreaker {}
                          retry {}
                          timeout {}
                          fallback {}
                          rateLimiter {}
                        }
                        $config
                    ${"\"\"\""}).resolve())
                }
            }
        """
    }

    private fun appWithPredicateModule(config: String): String {
        return """
            @KoraApp
            interface AppWithConfig : ConfigValueMapperModule, ResilientModule {
                @Tag(TestCircuitBreaker::class)
                fun testCircuitBreakerPredicate(): CircuitBreakerPredicate = CircuitBreakerPredicate { true }

                fun config(): Config {
                    return HoconConfigFactory.fromHocon(SimpleConfigOrigin("test"), ConfigFactory.parseString(${"\"\"\""}
                        resilient.telemetry {
                          circuitBreaker {}
                          retry {}
                          timeout {}
                          fallback {}
                          rateLimiter {}
                        }
                        $config
                    ${"\"\"\""}).resolve())
                }
            }
        """
    }

    private fun circuitBreakerConfig(configPath: String): String {
        return """
            $configPath {
              countBased {
                windowSize = 1
              }
              minimumRequiredCalls = 1
              failureRateThreshold = 100
              permittedCallsInHalfOpenState = 1
              waitDurationInOpenState = 1s
            }
        """
    }

    private fun circuitBreakerInterface(): String {
        return """
            @CircuitBreakerSpec("custom1")
            interface TestCircuitBreaker : io.koraframework.resilient.circuitbreaker.CircuitBreaker
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
