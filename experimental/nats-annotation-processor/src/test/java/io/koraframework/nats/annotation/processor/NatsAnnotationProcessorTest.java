package io.koraframework.nats.annotation.processor;

import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.common.annotation.Tag;
import io.koraframework.nats.common.NatsClient;
import io.koraframework.nats.common.consumer.NatsConsumerContainer;
import io.koraframework.nats.common.consumer.NatsListenerConfig;
import io.koraframework.nats.common.consumer.NatsMessage;
import io.koraframework.nats.common.consumer.NatsMessageHandler;
import io.koraframework.nats.common.consumer.deserializer.NatsDeserializer;
import io.koraframework.nats.common.producer.AtomicBatchPublisher;
import io.koraframework.nats.common.producer.NatsAtomicBatchConfig;
import io.koraframework.nats.common.producer.NatsAtomicBatchSink;
import io.koraframework.nats.common.producer.NatsPublisherConfig;
import io.koraframework.nats.common.producer.serializer.NatsSerializer;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherRecordObservation;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherTelemetry;
import io.nats.client.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NatsAnnotationProcessorTest extends AbstractAnnotationProcessorTest {
    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import io.koraframework.nats.common.annotation.*;
            import io.koraframework.nats.common.producer.*;
            import io.koraframework.nats.common.consumer.*;
            import io.koraframework.nats.common.consumer.telemetry.NatsConsumerPollObservation;
            import io.koraframework.nats.common.exceptions.NatsSerializationException;
            import io.nats.client.*;
            import io.nats.client.api.PublishAck;
            import io.nats.client.impl.Headers;
            import java.util.concurrent.CompletableFuture;
            import java.util.concurrent.CompletionStage;
            """;
    }

    private void compileNats(String source) {
        compile(List.of(new NatsAnnotationProcessor()), source);
        compileResult.assertSuccess();
    }

    @Test
    void publisherSignatures() {
        compileNats("""
            @NatsPublisher("publisher")
            public interface Events {
                @NatsPublisher.Subject("subjects.a") void send(String value, Headers headers);
                @NatsPublisher.Subject("subjects.b") PublishAck sendAck(Long value, PublishOptions options);
                CompletableFuture<PublishAck> sendAsync(NatsProducerMessage<String> record);
                CompletionStage<Void> sendCompletion(Message message);
                void sendCallback(Message message, NatsPublishCallback callback);
                @NatsPublisher.Subject("subjects.c") @NatsPublisher.Request
                String request(String value, java.time.Duration timeout);
                @NatsPublisher.Request CompletableFuture<Message> requestRaw(Message value);
                default String helper() { return "helper"; }
            }
            """);
        assertThat(compileResult.loadClass("$Events_Impl").getInterfaces()).contains(compileResult.loadClass("Events"));
        assertThat(compileResult.loadClass("Events_PublisherModule").getMethods()).hasSize(8);
    }

    @Test
    void generatedPublisherCallsNativeClient() throws Exception {
        compileNats("""
            @NatsPublisher("publisher")
            public interface Events {
                @NatsPublisher.Subject("events") void send(String value);
            }
            """);
        var client = mock(NatsClient.class);
        var connection = mock(io.nats.client.Connection.class);
        when(client.connection()).thenReturn(connection);
        var config = mock(NatsPublisherConfig.class, CALLS_REAL_METHODS);
        var telemetry = mock(NatsPublisherTelemetry.class);
        var observation = mock(NatsPublisherRecordObservation.class, CALLS_REAL_METHODS);
        when(observation.span()).thenReturn(io.opentelemetry.api.trace.Span.getInvalid());
        when(telemetry.observeSend(anyString())).thenReturn(observation);
        NatsSerializer<String> serializer = (subject, headers, value) -> value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        NatsPublisherConfig.SubjectConfig subject = () -> "events.created";
        var impl = compileResult.loadClass("$Events_Impl")
            .getConstructor(NatsClient.class, NatsPublisherConfig.class, NatsPublisherTelemetry.class,
                NatsSerializer.class, NatsPublisherConfig.SubjectConfig.class, NatsAtomicBatchSink.class)
            .newInstance(client, config, telemetry, serializer, subject, null);
        impl.getClass().getMethod("send", String.class).invoke(impl, "payload");
        var captor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(connection).publish(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("events.created");
        assertThat(captor.getValue().getData()).isEqualTo("payload".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        verify(observation).end(null);
    }

    @Test
    void tagsOnPayloadAndResponse() throws Exception {
        compileNats("""
            @NatsPublisher("publisher")
            public interface Events {
                @NatsPublisher.Subject("events") @NatsPublisher.Request @Tag(Long.class)
                String send(@Tag(Integer.class) String value);
            }
            """);
        var module = compileResult.loadClass("Events_PublisherModule");
        var factory = java.util.Arrays.stream(module.getMethods()).filter(m -> m.getName().endsWith("PublisherImpl")).findFirst().orElseThrow();
        assertThat(factory.getParameters()[3].getAnnotation(Tag.class).value()).isEqualTo(Integer.class);
        assertThat(factory.getParameters()[5].getAnnotation(Tag.class).value()).isEqualTo(Long.class);
    }

    @Test
    void serializerFailureEndsPublisherObservationBeforeAnySend() throws Exception {
        compileNats("""
            @NatsPublisher("cluster.publisher")
            public interface Events {
                @NatsPublisher.Subject(".subjects.created") void send(String value);
            }
            """);
        var telemetry = mock(NatsPublisherTelemetry.class);
        var observation = mock(NatsPublisherRecordObservation.class, CALLS_REAL_METHODS);
        when(observation.span()).thenReturn(io.opentelemetry.api.trace.Span.getInvalid());
        when(telemetry.observeSend("events")).thenReturn(observation);
        var failure = new IllegalArgumentException("serialization");
        NatsSerializer<String> serializer = (subject, headers, value) -> {
            assertThat(io.koraframework.common.telemetry.Observation.VALUE.get()).isSameAs(observation);
            throw failure;
        };
        var client = mock(NatsClient.class);
        var config = mock(NatsPublisherConfig.class, CALLS_REAL_METHODS);
        var impl = compileResult.loadClass("$Events_Impl")
            .getConstructor(NatsClient.class, NatsPublisherConfig.class, NatsPublisherTelemetry.class, NatsSerializer.class, NatsPublisherConfig.SubjectConfig.class, NatsAtomicBatchSink.class)
            .newInstance(client, config, telemetry, serializer, (NatsPublisherConfig.SubjectConfig) () -> "events", null);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> impl.getClass().getMethod("send", String.class).invoke(impl, "payload"))
            .hasCause(failure);
        verify(observation).end(failure);
        verifyNoInteractions(client);
    }

    @Test
    void listenerSignaturesAndReply() {
        compileNats("""
            public class Listeners {
                @NatsListener("listeners.body") public void body(String value, NatsSerializationException error, Message message, Headers headers, Connection connection, NatsConsumerPollObservation poll) {}
                @NatsListener("listeners.record") public void record(NatsMessage<@Tag(String.class) String> record, Exception error) {}
                @NatsListener(value = "listeners.batch", tag = Long.class) public void batch(NatsMessages<String> records, Connection connection, NatsConsumerPollObservation poll) {}
                @NatsListener("listeners.raw") public void raw(Message message) {}
                @NatsListener("listeners.reply") @Tag(Long.class) public String reply(@Tag(Integer.class) String value) { return value; }
            }
            """);
        assertThat(compileResult.loadClass("Listeners_NatsListenerModule").getMethods()).hasSize(20);
    }

    @Test
    void generatedListenerReceivesDecodeErrorAndPollObservation() throws Exception {
        compileNats("""
            public class Listeners {
                public String value;
                public NatsSerializationException error;
                public NatsConsumerPollObservation poll;
                @NatsListener("listener") public void handle(String value, NatsSerializationException error, NatsConsumerPollObservation poll) {
                    this.value = value; this.error = error; this.poll = poll;
                }
            }
            """);
        var controller = compileResult.loadClass("Listeners").getConstructor().newInstance();
        var moduleClass = compileResult.loadClass("Listeners_NatsListenerModule");
        var module = java.lang.reflect.Proxy.newProxyInstance(moduleClass.getClassLoader(), new Class<?>[]{moduleClass},
            (proxy, method, args) -> java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args));
        var client = mock(NatsClient.class);
        var config = mock(NatsListenerConfig.class, CALLS_REAL_METHODS);
        when(config.subjects()).thenReturn(List.of("events"));
        NatsDeserializer<String> decoder = message -> {
            throw new IllegalArgumentException("bad payload");
        };
        var factory = java.util.Arrays.stream(moduleClass.getMethods()).filter(method -> method.getName().endsWith("Container")).findFirst().orElseThrow();
        var container = factory.invoke(module, controller, client, config,
            io.koraframework.nats.common.consumer.telemetry.impl.NoopNatsConsumerTelemetry.INSTANCE, null, decoder);
        var handlerField = NatsConsumerContainer.class.getDeclaredField("recordHandler");
        handlerField.setAccessible(true);
        @SuppressWarnings("unchecked") var handler = (NatsMessageHandler<String>) handlerField.get(container);
        var poll = io.koraframework.nats.common.consumer.telemetry.impl.NoopNatsConsumerPollObservation.INSTANCE;
        var message = io.nats.client.impl.NatsMessage.builder().subject("events").data(new byte[]{1}).build();
        handler.handle(poll, new NatsMessage<>(message, decoder));
        assertThat(controller.getClass().getField("value").get(controller)).isNull();
        assertThat(controller.getClass().getField("error").get(controller))
            .isInstanceOf(io.koraframework.nats.common.exceptions.NatsSerializationException.class);
        assertThat(controller.getClass().getField("poll").get(controller)).isSameAs(poll);
    }

    @Test
    void primitivePayloadCannotBindDecodeError() {
        compile(List.of(new NatsAnnotationProcessor()), """
            public class Listeners {
                @NatsListener("listener") public void handle(int value, NatsSerializationException error) {}
            }
            """);
        assertThat(compileResult.errors()).anyMatch(error -> error.getMessage(null).contains("nullable/boxed"));
    }

    @Test
    void nestedPublishersAndListeners() {
        compileNats("""
            public class Outer {
                @NatsPublisher("publisher")
                public interface Events { void send(Message message); }
                public static class Listeners {
                    @NatsListener("listener") public void onMessage(Message message) {}
                }
            }
            """);
        assertThat(compileResult.loadClass("$Outer_Events_Impl")).isNotNull();
        assertThat(compileResult.loadClass("Outer_Listeners_NatsListenerModule")).isNotNull();
    }

    @Test
    void invalidPublisherNeedsSubject() {
        compile(List.of(new NatsAnnotationProcessor()), """
            @NatsPublisher("publisher")
            public interface Events { void send(String value); }
            """);
        assertThat(compileResult.errors()).anyMatch(error -> error.getMessage(null).contains("requires @NatsPublisher.Subject"));
    }

    @Test
    void invalidPublisherReturnType() {
        compile(List.of(new NatsAnnotationProcessor()), """
            @NatsPublisher("publisher")
            public interface Events { @NatsPublisher.Subject("events") String send(String value); }
            """);
        assertThat(compileResult.errors()).anyMatch(error -> error.getMessage(null).contains("annotate request/reply with @Request"));
    }

    @Test
    void invalidBatchMetadata() {
        compile(List.of(new NatsAnnotationProcessor()), """
            public class Listeners {
                @NatsListener("listener") public void batch(NatsMessages<String> records, Message message) {}
            }
            """);
        assertThat(compileResult.errors()).anyMatch(error -> error.getMessage(null).contains("Batch NATS listener"));
    }

    @Test
    void invalidOverloadedListeners() {
        compile(List.of(new NatsAnnotationProcessor()), """
            public class Listeners {
                @NatsListener("a") public void handle(String value) {}
                @NatsListener("b") public void handle(byte[] value) {}
            }
            """);
        assertThat(compileResult.errors()).anyMatch(error -> error.getMessage(null).contains("distinct names"));
    }

    @Test
    void applicationGraphResolvesPublisherAndListener() {
        compile(List.of(new NatsAnnotationProcessor(),
            new io.koraframework.config.annotation.processor.processor.ConfigParserAnnotationProcessor(),
            new io.koraframework.kora.app.annotation.processor.KoraAppProcessor()), """
            @KoraApp
            public interface Application extends io.koraframework.nats.common.NatsModule,
                io.koraframework.config.common.mapper.ConfigValueMapperModule {
                default io.koraframework.config.common.Config config() {
                    return io.koraframework.config.common.util.ConfigMappingUtils.fromMap(java.util.Map.of());
                }
                @Root default Object root(AtomicEvents events, OtherEvents other) { return events; }
                @Tag(Events.class)
                default io.koraframework.common.Configurer<Options.Builder> optionsConfigurer() {
                    return options -> options.connectionName("cluster-a");
                }
                @Component final class Listeners {
                    @NatsListener("listener") public void onEvent(String value) {}
                }
                @Component final class OtherListeners {
                    @NatsListener("otherListener") public void onEvent(String value) {}
                }
                @NatsPublisher("clusterA.publisher") interface Events {
                    @NatsPublisher.Subject("events") void send(String value);
                }
                @NatsPublisher("clusterA.atomic") interface AtomicEvents extends AtomicBatchPublisher<Events> {}
                @NatsPublisher("clusterB.publisher") interface OtherEvents {
                    @NatsPublisher.Subject("otherEvents") void send(String value);
                }
            }
            """);
        compileResult.assertSuccess();
        assertThat(compileResult.loadClass("ApplicationGraph")).isNotNull();
        var clients = loadGraphDraw("Application").getNodes().stream().filter(node -> node.type().equals(NatsClient.class)).toList();
        assertThat(clients).hasSize(4);
        assertThat(clients).extracting(io.koraframework.application.graph.Node::tag).doesNotContainNull().doesNotHaveDuplicates();
    }

    @Test
    void publisherAopProxyIsWired() {
        compile(List.of(new NatsAnnotationProcessor(),
            new io.koraframework.aop.annotation.processor.AopAnnotationProcessor()), """
            @NatsPublisher("publisher")
            public interface Events {
                @io.koraframework.logging.common.annotation.Log
                @NatsPublisher.Subject("events") void send(String value);
            }
            """);
        compileResult.assertSuccess();
        assertThat(compileResult.loadClass("$Events_Impl__AopProxy")).isNotNull();
        assertThat(compileResult.loadClass("Events_PublisherModule")).isNotNull();
    }

    @Test
    void explicitMappingSelectsConcreteMapper() throws Exception {
        compileNats("""
            @NatsPublisher("publisher")
            public interface Events {
                @NatsPublisher.Subject("events") void send(@Mapping(Encoder.class) String value);
                final class Encoder implements io.koraframework.nats.common.producer.serializer.NatsSerializer<String> {
                    public byte[] serialize(String subject, Headers headers, String value) { return value.getBytes(); }
                }
            }
            """);
        var factory = java.util.Arrays.stream(compileResult.loadClass("Events_PublisherModule").getMethods())
            .filter(method -> method.getName().endsWith("PublisherImpl")).findFirst().orElseThrow();
        assertThat(factory.getParameterTypes()[3]).isEqualTo(compileResult.loadClass("Events$Encoder"));
    }

    @Test
    void generatedAtomicPublisherReusesTypedCodecAndReturnsBatchAck() throws Exception {
        compileNats("""
            public class Contracts {
                @NatsPublisher("publisher") public interface Events {
                    @NatsPublisher.Subject("events") void send(String value);
                }
                @NatsPublisher("atomic") public interface AtomicEvents extends AtomicBatchPublisher<Events> {}
            }
            """);
        var moduleClass = compileResult.loadClass("Contracts_Events_PublisherModule");
        var module = java.lang.reflect.Proxy.newProxyInstance(moduleClass.getClassLoader(), new Class<?>[]{moduleClass},
            (proxy, method, args) -> java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args));
        var client = mock(NatsClient.class);
        var connection = mock(io.nats.client.Connection.class);
        when(client.connection()).thenReturn(connection);
        var management = mock(io.nats.client.JetStreamManagement.class, RETURNS_DEEP_STUBS);
        when(client.jetStreamManagement()).thenReturn(management);
        when(management.getStreamInfo("EVENTS").getConfiguration().getAllowAtomicPublish()).thenReturn(true);
        when(connection.request(any(Message.class), any(java.time.Duration.class))).thenAnswer(invocation -> {
            Message sent = invocation.getArgument(0);
            var json = sent.getHeaders().containsKey("Nats-Batch-Commit")
                ? "{\"stream\":\"EVENTS\",\"seq\":2,\"batch\":\"" + sent.getHeaders().getFirst("Nats-Batch-Id") + "\",\"count\":2}" : "";
            return io.nats.client.impl.NatsMessage.builder().subject("reply").data(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)).build();
        });
        var config = mock(NatsPublisherConfig.class, CALLS_REAL_METHODS);
        when(config.mode()).thenReturn(NatsPublisherConfig.Mode.JETSTREAM);
        var telemetry = io.koraframework.nats.common.producer.telemetry.impl.NoopNatsPublisherTelemetry.INSTANCE;
        NatsSerializer<String> encoder = (subject, headers, value) -> value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var factoryMethod = java.util.Arrays.stream(moduleClass.getMethods()).filter(method -> method.getName().endsWith("AtomicPublisherFactory")).findFirst().orElseThrow();
        var factory = factoryMethod.invoke(module, client, config, telemetry, encoder, (NatsPublisherConfig.SubjectConfig) () -> "events.created");
        var batchConfig = mock(NatsAtomicBatchConfig.class, CALLS_REAL_METHODS);
        when(batchConfig.stream()).thenReturn("EVENTS");
        var atomic = (AtomicBatchPublisher<?>) compileResult.loadClass("$Contracts_AtomicEvents_Impl")
            .getConstructor(NatsClient.class, NatsPublisherConfig.class, NatsAtomicBatchConfig.class, NatsPublisherTelemetry.class, java.util.function.Function.class)
            .newInstance(client, config, batchConfig, telemetry, factory);
        try (var batch = atomic.begin()) {
            var send = compileResult.loadClass("Contracts$Events").getMethod("send", String.class);
            send.invoke(batch.publisher(), "one");
            send.invoke(batch.publisher(), "two");
            verifyNoInteractions(connection);
            assertThat(batch.commit().getBatchSize()).isEqualTo(2);
        }
        var sent = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(connection, times(2)).request(sent.capture(), any(java.time.Duration.class));
        assertThat(sent.getAllValues().getFirst().getData()).isEqualTo("one".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(sent.getAllValues()).allSatisfy(message -> assertThat(message.getSubject()).isEqualTo("events.created"));
    }

    @Test
    void atomicPublisherRejectsPerMessageAcknowledgements() {
        compile(List.of(new NatsAnnotationProcessor()), """
            public class Contracts {
                @NatsPublisher("publisher") public interface Events { PublishAck send(Message message); }
                @NatsPublisher("atomic") public interface AtomicEvents extends AtomicBatchPublisher<Events> {}
            }
            """);
        assertThat(compileResult.errors()).anyMatch(error -> error.getMessage(null).contains("Atomic batch publisher methods must return void"));
    }

    @Test
    void atomicPublisherFactoryRetainsPublisherAop() {
        compile(List.of(new NatsAnnotationProcessor(), new io.koraframework.aop.annotation.processor.AopAnnotationProcessor()), """
            public class Contracts {
                @NatsPublisher("publisher") public interface Events {
                    @io.koraframework.logging.common.annotation.Log
                    @NatsPublisher.Subject("events") void send(String value);
                }
                @NatsPublisher("atomic") public interface AtomicEvents extends AtomicBatchPublisher<Events> {}
            }
            """);
        compileResult.assertSuccess();
        var methods = List.of(compileResult.loadClass("Contracts_Events_PublisherModule").getMethods());
        var normal = methods.stream().filter(method -> method.getName().endsWith("PublisherImpl")).findFirst().orElseThrow();
        var atomic = methods.stream().filter(method -> method.getName().endsWith("AtomicPublisherFactory")).findFirst().orElseThrow();
        assertThat(atomic.getParameterTypes()).containsExactly(normal.getParameterTypes());
        assertThat(atomic.getParameterCount()).isGreaterThan(5);
    }
}
