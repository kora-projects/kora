package ru.tinkoff.grpc.client.annotation.processor;


import org.assertj.core.api.Assertions;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.util.Arrays;
import java.util.List;

class GrpcClientExtensionTest extends AbstractAnnotationProcessorTest {

    protected void compile(@Language("java") String... sources) throws Exception {
        var patchedSources = Arrays.copyOf(sources, sources.length + 1);
        patchedSources[sources.length] = """
            @ru.tinkoff.kora.common.Module
            public interface ConfigModule extends ru.tinkoff.grpc.client.GrpcClientModule, ru.tinkoff.kora.config.common.DefaultConfigExtractorsModule {
              default ru.tinkoff.kora.config.common.Config config() {
                return ru.tinkoff.kora.config.common.factory.MapConfigFactory.fromMap(java.util.Map.of(
                  "grpcClient", java.util.Map.of(
                    "Events", java.util.Map.of(
                      "url", "http://localhost:8080",
                      "timeout", "20s"
                    )
                  )
                ));
              }
            }
            """;

        super.compile(List.of(new KoraAppProcessor()), patchedSources);

        compileResult.assertSuccess();
        try (var g = loadGraph("TestApp")) {
            /*
              1. root config
              2. duration parser
              3. log config parser
              4. tracing config parser
              5. double config parser
              6. double array config parser
              7. metrics config parser
              8. telemetry config parser
              9. config parser
              10. parsed config
              11. telemetry factory
              12. NettyTransportConfig.EventLoop extractor
              13. NettyTransportConfig extractor
              14. NettyTransportConfig
              15. netty event loop group
              16. netty channel factory
              17. channel factory
              18. channel lifecycle
              19. the stub
              20. test root
             */
            Assertions.assertThat(g.draw().size()).isEqualTo(20);
        }
    }


    @Test
    public void testBlockingStub() throws Exception {
        compile("""
            @KoraApp
            public interface TestApp {
              @Root
              default String test(ru.tinkoff.kora.grpc.server.events.EventsGrpc.EventsBlockingStub stub) {
                return "";
              }
            }
            """);
    }

    @Test
    public void testFutureStub() throws Exception {
        compile("""
            @KoraApp
            public interface TestApp {
              @Root
              default String test(ru.tinkoff.kora.grpc.server.events.EventsGrpc.EventsFutureStub stub) {
                return "";
              }
            }
            """);
    }

    @Test
    public void testAsyncStub() throws Exception {
        compile("""
            @KoraApp
            public interface TestApp {
              @Root
              default String test(ru.tinkoff.kora.grpc.server.events.EventsGrpc.EventsStub stub) {
                return "";
              }
            }
            """);
    }
}
