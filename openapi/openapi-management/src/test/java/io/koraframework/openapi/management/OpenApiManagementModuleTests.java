package io.koraframework.openapi.management;

import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class OpenApiManagementModuleTests extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import io.koraframework.application.graph.All;
            import io.koraframework.config.common.*;
            import io.koraframework.config.common.impl.SimpleConfigValueOrigin;
            import io.koraframework.config.common.origin.SimpleConfigOrigin;
            import io.koraframework.config.common.mapper.ConfigValueMapper;
            import io.koraframework.http.server.common.request.HttpServerRequestHandler;
            import io.koraframework.openapi.management.*;
            import java.util.List;
            import java.util.stream.StreamSupport;
            import static org.junit.jupiter.api.Assertions.*;
            import static org.mockito.Mockito.*;
            """;
    }

    @Test
    void defaultFactoryRegistration() throws Exception {
        compile(List.of(new KoraAppProcessor()), """
            @KoraApp
            public interface App extends OpenApiManagementModule, TestModule {
                @Root
                default Object root(All<HttpServerRequestHandler> handlers) {
                    assertEquals(List.of("/openapi.management", "/openapi.management/scalar",
                        "/openapi.management/swagger-ui", "/openapi.management/swagger-ui/oauth2-redirect"),
                        StreamSupport.stream(handlers.spliterator(), false)
                            .map(HttpServerRequestHandler::routeTemplate).sorted().toList());
                    return new Object();
                }
            }
            """, TEST_MODULE);
        assertSuccess();
        try (var graph = loadGraph("App")) {
            assertNotNull(graph.findByType(OpenApiManagementFactoryModule.class));
        }
    }

    @Test
    void taggedFactoriesUseIndependentConfigsAndHandlers() throws Exception {
        compile(List.of(new KoraAppProcessor()), """
            @KoraApp
            public interface App extends TestModule {
                class PublicApi {}
                class SystemApi {}

                @FactoryModule
                @Tag(PublicApi.class)
                default OpenApiManagementFactoryModule publicOpenApi() {
                    return new OpenApiManagementFactoryModule("public.openapi");
                }

                @FactoryModule
                @Tag(SystemApi.class)
                default OpenApiManagementFactoryModule systemOpenApi() {
                    return new OpenApiManagementFactoryModule("system.openapi");
                }

                @Root
                default Object root(@Tag(PublicApi.class) All<HttpServerRequestHandler> publicHandlers,
                                    @Tag(SystemApi.class) All<HttpServerRequestHandler> systemHandlers,
                                    All<HttpServerRequestHandler> untaggedHandlers) {
                    assertEquals(4, StreamSupport.stream(publicHandlers.spliterator(), false)
                        .peek(handler -> assertTrue(handler.routeTemplate().startsWith("/public.openapi")))
                        .peek(handler -> assertTrue(handler.enabled())).count());
                    assertEquals(4, StreamSupport.stream(systemHandlers.spliterator(), false)
                        .peek(handler -> assertTrue(handler.routeTemplate().startsWith("/system.openapi")))
                        .peek(handler -> assertFalse(handler.enabled())).count());
                    assertFalse(untaggedHandlers.iterator().hasNext());
                    return new Object();
                }
            }
            """, TEST_MODULE);
        assertSuccess();
        try (var graph = loadGraph("App")) {
            assertEquals(2, graph.findAllByType(OpenApiManagementFactoryModule.class).size());
        }
    }

    private static final String TEST_MODULE = """
        public interface TestModule {
            default Config config() {
                var config = mock(Config.class);
                for (var path : List.of("openapi.management", "public.openapi", "system.openapi")) {
                    var origin = new SimpleConfigValueOrigin(new SimpleConfigOrigin("test"), ConfigValuePath.parse(path));
                    doReturn(new ConfigValue.StringValue(origin, path)).when(config).get(path);
                }
                return config;
            }

            default ConfigValueMapper<OpenApiManagementConfig> mapper() {
                return value -> new TestConfig("/" + value.asString(), !value.asString().equals("system.openapi"));
            }

            record TestConfig(String path, boolean enabled) implements OpenApiManagementConfig {
                @Override
                public List<String> files() {
                    return List.of("openapi1.yaml");
                }

                @Override
                public SwaggerUIConfig swaggerui() {
                    return new SwaggerUIConfig() {
                        public boolean enabled() { return TestConfig.this.enabled(); }
                        public String path() { return TestConfig.this.path() + "/swagger-ui"; }
                    };
                }

                @Override
                public ScalarConfig scalar() {
                    return new ScalarConfig() {
                        public boolean enabled() { return TestConfig.this.enabled(); }
                        public String path() { return TestConfig.this.path() + "/scalar"; }
                    };
                }
            }
        }
        """;
}
