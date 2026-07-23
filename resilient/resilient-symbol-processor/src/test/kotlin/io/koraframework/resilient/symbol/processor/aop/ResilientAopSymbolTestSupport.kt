package io.koraframework.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import io.koraframework.application.graph.ApplicationGraphDraw
import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import io.koraframework.resilient.symbol.processor.CircuitBreakerSymbolProcessorProvider
import java.util.function.Supplier

@KspExperimental
abstract class ResilientAopSymbolTestSupport : AbstractSymbolProcessorTest() {

    protected val processors = listOf(
        KoraAppProcessorProvider(),
        CircuitBreakerSymbolProcessorProvider(),
        AopSymbolProcessorProvider(),
    )

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
            import io.koraframework.resilient.ResilientModule
            import io.koraframework.resilient.fallback.annotation.Fallback
            import io.koraframework.resilient.ratelimiter.annotation.RateLimited
            import io.koraframework.resilient.ratelimiter.annotation.RateLimiterSpec
            import io.koraframework.resilient.retry.annotation.Retryable
            import io.koraframework.resilient.retry.annotation.RetrySpec
            import io.koraframework.resilient.timeout.annotation.Timeout
            import io.koraframework.resilient.timeout.annotation.TimeoutSpec
            import java.io.IOException
        """.trimIndent()
    }

    protected fun compileApp(config: String, spec: String, target: String): Any {
        compile0(processors, app(config), spec, target).assertSuccess()
        return loadService("TestTarget")
    }

    protected fun compileFailed(vararg sources: String) {
        compile0(processors, *sources)
    }

    protected fun loadGraph(): ApplicationGraphDraw {
        val clazz = compileResult.assertSuccess().classLoader.loadClass(testPackage() + ".AppWithConfigGraph")
        @Suppress("UNCHECKED_CAST")
        return (clazz.constructors.first().newInstance() as Supplier<ApplicationGraphDraw>).get()
    }

    protected fun loadService(className: String): Any {
        val graphDraw = loadGraph()
        val graph = graphDraw.init()
        val serviceClass = loadClass(className)
        return graphDraw.nodes
            .map { graph.get(it) }
            .first { serviceClass.isInstance(it) }
    }

    protected fun call(service: Any, method: String, vararg args: Any?): Any? {
        val javaMethod = service.javaClass.methods.first { it.name == method && it.parameterCount == args.size }
        return try {
            javaMethod.invoke(service, *args)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }

    protected fun app(config: String): String {
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
}
