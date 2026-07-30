package io.koraframework.resilient.annotation.processor.aop;

import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import io.koraframework.resilient.annotation.processor.CircuitBreakerAnnotationProcessor;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

abstract class ResilientAopTestSupport extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import com.typesafe.config.ConfigFactory;
            import io.koraframework.common.annotation.Component;
            import io.koraframework.common.annotation.KoraApp;
            import io.koraframework.common.annotation.Root;
            import io.koraframework.config.common.Config;
            import io.koraframework.config.common.mapper.ConfigValueMapperModule;
            import io.koraframework.config.common.origin.SimpleConfigOrigin;
            import io.koraframework.config.hocon.HoconConfigFactory;
            import io.koraframework.resilient.ResilientModule;
            import io.koraframework.resilient.fallback.annotation.Fallback;
            import io.koraframework.resilient.ratelimiter.annotation.RateLimited;
            import io.koraframework.resilient.ratelimiter.annotation.RateLimiterSpec;
            import io.koraframework.resilient.retry.annotation.Retryable;
            import io.koraframework.resilient.retry.annotation.RetrySpec;
            import io.koraframework.resilient.timeout.annotation.Timeout;
            import io.koraframework.resilient.timeout.annotation.TimeoutSpec;
            import java.io.IOException;
            """;
    }

    protected final Object compileApp(String config, String spec, String target) {
        compile(List.of(new KoraAppProcessor(), new CircuitBreakerAnnotationProcessor(), new AopAnnotationProcessor()), app(config), spec, target);
        compileResult.assertSuccess();
        return loadService("TestTarget");
    }

    protected final void compileFailed(String... sources) {
        compile(List.of(new KoraAppProcessor(), new CircuitBreakerAnnotationProcessor(), new AopAnnotationProcessor()), sources);
    }

    protected final Object loadService(String className) {
        var graphDraw = loadGraph();
        var graph = graphDraw.init();
        var serviceClass = loadClass(className);
        return graphDraw.getNodes().stream()
            .map(graph::get)
            .filter(serviceClass::isInstance)
            .findFirst()
            .orElseThrow();
    }

    protected final Object invokeTarget(Object target, String name, Object... params) throws Throwable {
        try {
            for (var method : target.getClass().getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == params.length) {
                    return method.invoke(target, params);
                }
            }
            throw new IllegalArgumentException("Method " + name + " wasn't found");
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    protected final ApplicationGraphDraw loadGraph() {
        return loadGraphDraw("AppWithConfig");
    }

    protected final String app(String config) {
        return """
            @KoraApp
            public interface AppWithConfig extends ConfigValueMapperModule, ResilientModule {
                default Config config() {
                    return HoconConfigFactory.fromHocon(new SimpleConfigOrigin("test"), ConfigFactory.parseString(
                        \"""
                            resilient.telemetry {
                              circuitBreaker {}
                              retry {}
                              timeout {}
                              fallback {}
                              rateLimiter {}
                            }
                            %s
                            \"""
                    ).resolve());
                }
            }
            """.formatted(config);
    }
}
