package ru.tinkoff.kora.camunda.zeebe.worker.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.camunda.zeebe.worker.KoraJobWorker;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ZeebeWorkerMonoTests extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import ru.tinkoff.kora.camunda.zeebe.worker.annotation.*;
            import ru.tinkoff.kora.camunda.zeebe.worker.*;
            import java.util.concurrent.CompletionStage;
            import java.util.concurrent.CompletableFuture;
            import reactor.core.publisher.Mono;
            """;
    }

    @Test
    public void workerNoVars() {
        this.compile(List.of(new ZeebeWorkerAnnotationProcessor()), """
            @Component
            public final class Handler {
                        
                @JobWorker("worker")
                Mono<Void> handle() {
                    return Mono.empty();
                }
            }
            """);

        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Handler_handle_KoraJobWorker");
        assertThat(clazz).isNotNull();
        assertThat(Arrays.stream(clazz.getInterfaces()).anyMatch(i -> i.isAssignableFrom(KoraJobWorker.class))).isTrue();
        assertThat(Arrays.stream(clazz.getMethods()).anyMatch(m -> m.getName().equals("fetchVariables"))).isTrue();
        assertThat(Arrays.stream(clazz.getMethods()).anyMatch(m -> m.getName().equals("type"))).isTrue();
        assertThat(Arrays.stream(clazz.getMethods()).anyMatch(m -> m.getName().equals("handle"))).isTrue();
    }

    @Test
    public void workerVars() {
        this.compile(List.of(new ZeebeWorkerAnnotationProcessor()), """
            @Component
            public final class Handler {
                        
                public record SomeVariables(String name, String id) {}
                        
                @JobWorker("worker")
                Mono<Void> handle(@JobVariables SomeVariables vars) {
                    return Mono.empty();
                }
            }
            """);

        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Handler_handle_KoraJobWorker");
        assertThat(clazz).isNotNull();
        assertThat(Arrays.stream(clazz.getInterfaces()).anyMatch(i -> i.isAssignableFrom(KoraJobWorker.class))).isTrue();
        assertThat(Arrays.stream(clazz.getMethods()).anyMatch(m -> m.getName().equals("fetchVariables"))).isTrue();
        assertThat(Arrays.stream(clazz.getMethods()).anyMatch(m -> m.getName().equals("type"))).isTrue();
        assertThat(Arrays.stream(clazz.getMethods()).anyMatch(m -> m.getName().equals("handle"))).isTrue();
    }

    @Test
    public void workerVar() {
        this.compile(List.of(new ZeebeWorkerAnnotationProcessor()), """
            @Component
            public final class Handler {
                        
                @JobWorker("worker")
                Mono<Void> handle(@JobVariable String var1, @Nullable @JobVariable("var12345") String var2) {
                    return Mono.empty();
                }
            }
            """);

        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Handler_handle_KoraJobWorker");
        assertThat(clazz).isNotNull();
        assertThat(Arrays.stream(clazz.getInterfaces()).anyMatch(i -> i.isAssignableFrom(KoraJobWorker.class))).isTrue();
        assertThat(Arrays.stream(clazz.getMethods()).anyMatch(m -> m.getName().equals("fetchVariables"))).isTrue();
        assertThat(Arrays.stream(clazz.getMethods()).anyMatch(m -> m.getName().equals("type"))).isTrue();
        assertThat(Arrays.stream(clazz.getMethods()).anyMatch(m -> m.getName().equals("handle"))).isTrue();
    }

    @Test
    public void workerReturnVars() {
        this.compile(List.of(new ZeebeWorkerAnnotationProcessor()), """
            @Component
            public final class Handler {
                        
                public record SomeResponse(String name, String id) {}
                
                @JobWorker("worker")
                Mono<SomeResponse> handle() {
                    return Mono.just(new SomeResponse("1", "2"));
                }
            }
            """);

        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Handler_handle_KoraJobWorker");
        assertThat(clazz).isNotNull();
        assertThat(Arrays.stream(clazz.getInterfaces()).anyMatch(i -> i.isAssignableFrom(KoraJobWorker.class))).isTrue();
        assertThat(Arrays.stream(clazz.getMethods()).anyMatch(m -> m.getName().equals("fetchVariables"))).isTrue();
        assertThat(Arrays.stream(clazz.getMethods()).anyMatch(m -> m.getName().equals("type"))).isTrue();
        assertThat(Arrays.stream(clazz.getMethods()).anyMatch(m -> m.getName().equals("handle"))).isTrue();
    }

    @Test
    public void workerContext() {
        this.compile(List.of(new ZeebeWorkerAnnotationProcessor()), """
            @Component
            public final class Handler {
                        
                @JobWorker("worker")
                Mono<Void> handle(JobContext context) {
                    return Mono.empty();
                }
            }
            """);

        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$Handler_handle_KoraJobWorker");
        assertThat(clazz).isNotNull();
        assertThat(Arrays.stream(clazz.getInterfaces()).anyMatch(i -> i.isAssignableFrom(KoraJobWorker.class))).isTrue();
        assertThat(Arrays.stream(clazz.getMethods()).anyMatch(m -> m.getName().equals("fetchVariables"))).isTrue();
        assertThat(Arrays.stream(clazz.getMethods()).anyMatch(m -> m.getName().equals("type"))).isTrue();
        assertThat(Arrays.stream(clazz.getMethods()).anyMatch(m -> m.getName().equals("handle"))).isTrue();
    }
}
