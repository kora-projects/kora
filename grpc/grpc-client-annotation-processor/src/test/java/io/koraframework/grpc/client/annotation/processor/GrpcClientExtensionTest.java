package io.koraframework.grpc.client.annotation.processor;


import org.assertj.core.api.Assertions;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;

import java.util.Arrays;
import java.util.List;

class GrpcClientExtensionTest extends AbstractAnnotationProcessorTest {

    protected void compile(@Language("java") String... sources) throws Exception {
        var patchedSources = Arrays.copyOf(sources, sources.length + 1);
        patchedSources[sources.length] = """
            @io.koraframework.common.Module
            public interface ConfigModule extends io.koraframework.grpc.client.GrpcClientModule, io.koraframework.config.common.DefaultConfigExtractorsModule {
              default io.koraframework.config.common.Config config() {
                return io.koraframework.config.common.factory.MapConfigFactory.fromMap(java.util.Map.of(
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
            Assertions.assertThat(g.draw().size()).isEqualTo(17);
        }
    }


    @Test
    public void testBlockingStub() throws Exception {
        compile("""
            @KoraApp
            public interface TestApp {
              @Root
              default String test(io.koraframework.grpc.server.events.EventsGrpc.EventsBlockingStub stub) {
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
              default String test(io.koraframework.grpc.server.events.EventsGrpc.EventsFutureStub stub) {
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
              default String test(io.koraframework.grpc.server.events.EventsGrpc.EventsStub stub) {
                return "";
              }
            }
            """);
    }
}
