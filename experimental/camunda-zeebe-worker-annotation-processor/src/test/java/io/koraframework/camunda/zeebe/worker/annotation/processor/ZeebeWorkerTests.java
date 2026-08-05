package io.koraframework.camunda.zeebe.worker.annotation.processor;

import io.camunda.client.api.command.ThrowErrorCommandStep1;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.camunda.zeebe.worker.KoraJobWorker;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ZeebeWorkerTests extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import io.koraframework.camunda.zeebe.worker.annotation.*;
            import io.koraframework.camunda.zeebe.worker.*;
            import io.koraframework.camunda.zeebe.worker.exception.*;
            import io.koraframework.application.graph.All;
            """;
    }

    @Test
    public void workerNoVars() {
        this.compile(List.of(new ZeebeWorkerAnnotationProcessor()), """
            @Component
            public final class Handler {
                @JobWorker("worker")
                void handle() {
                    // do something
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
                void handle(@JobVariables SomeVariables vars) {
                    // do something
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
                void handle(@JobVariable String var1, @Nullable @JobVariable("var12345") String var2) {
                    // do something
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
                SomeResponse handle() {
                    return new SomeResponse("1", "2");
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
                void handle(JobContext context) {
                    // do something
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
    public void workerWithPublicMethodIsResolvedInGraph() {
        this.compile(List.of(new KoraAppProcessor(), new AopAnnotationProcessor(), new ZeebeWorkerAnnotationProcessor()), """
            @KoraApp
            public interface ExampleApplication {

                @Root
                default Object root(All<KoraJobWorker> workers) {
                    return workers;
                }

                default ZeebeWorkerConfig zeebeWorkerConfig() {
                    return null;
                }

                default io.koraframework.json.common.JsonReader<String> stringJsonReader() {
                    return null;
                }
            }
            """, """
            @Component
            public final class Handler {
                @JobWorker("worker")
                public void handle(@JobVariable String var1) {
                    // do something
                }
            }
            """);

        this.compileResult.assertSuccess();
    }

    @Test
    public void workerJobWorkerExceptionIsTurnedIntoBpmnError() {
        this.compile(List.of(new ZeebeWorkerAnnotationProcessor()), """
            @Component
            public final class Handler {
                @JobWorker("worker")
                void handle() {
                    throw new JobWorkerException("DOESNT_WORK");
                }
            }
            """);

        this.compileResult.assertSuccess();

        var worker = (KoraJobWorker) newObject("$Handler_handle_KoraJobWorker", newObject("Handler"));

        var errorStep2 = Mockito.mock(ThrowErrorCommandStep1.ThrowErrorCommandStep2.class);
        Mockito.when(errorStep2.errorMessage(Mockito.anyString())).thenReturn(errorStep2);
        var errorStep1 = Mockito.mock(ThrowErrorCommandStep1.class);
        Mockito.when(errorStep1.errorCode("DOESNT_WORK")).thenReturn(errorStep2);
        var job = Mockito.mock(ActivatedJob.class);
        var client = Mockito.mock(JobClient.class);
        Mockito.when(client.newThrowErrorCommand(job)).thenReturn(errorStep1);

        var command = worker.handle(client, job);

        assertThat(command).isSameAs(errorStep2);
    }
}
