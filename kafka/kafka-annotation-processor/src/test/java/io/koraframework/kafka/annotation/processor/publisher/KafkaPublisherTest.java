package io.koraframework.kafka.annotation.processor.publisher;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.common.annotation.Tag;
import io.koraframework.kafka.annotation.processor.producer.KafkaPublisherAnnotationProcessor;
import io.koraframework.kafka.common.producer.AbstractPublisher;
import io.koraframework.kafka.common.producer.KafkaPublisherConfig;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherRecordObservation;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetry;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetryConfig;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetryFactory;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTransactionObservation;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import org.mockito.Mockito;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class KafkaPublisherTest extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import io.koraframework.kafka.common.producer.TransactionalPublisher;
            import io.koraframework.kafka.common.annotation.KafkaPublisher;
            import io.koraframework.kafka.common.annotation.KafkaPublisher.Topic;
            import org.apache.kafka.clients.producer.ProducerRecord;
            import org.apache.kafka.common.header.Headers;
            import org.apache.kafka.common.header.Header;
            import org.apache.kafka.clients.producer.Callback;
            import org.apache.kafka.clients.producer.RecordMetadata;
            """;
    }

    @Test
    public void testPublisherWithRecord() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              void send(ProducerRecord<String, String> record);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_Impl");
        assertThat(clazz).isNotNull();
        clazz.getConstructor(KafkaPublisherTelemetryFactory.class, KafkaPublisherTelemetryConfig.class, Properties.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class);
    }

    @Test
    public void testPublisherWithDefault() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              void send(ProducerRecord<String, String> record);
              default void send0(ProducerRecord<String, String> r1, ProducerRecord<String, String> r2) {
              }
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_Impl");
        assertThat(clazz).isNotNull();
        clazz.getConstructor(KafkaPublisherTelemetryFactory.class, KafkaPublisherTelemetryConfig.class, Properties.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class);
    }

    @Test
    public void testPublisherWithRecordAndCallback() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              void send(ProducerRecord<String, String> record, Callback callback);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_Impl");
        assertThat(clazz).isNotNull();
        clazz.getConstructor(KafkaPublisherTelemetryFactory.class, KafkaPublisherTelemetryConfig.class, Properties.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class);
    }

    @Test
    public void testPublisherWithRecordWithKeyTag() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            import io.koraframework.common.annotation.Tag;@KafkaPublisher("test")
            public interface TestProducer {
              void send(ProducerRecord<@Tag(String.class) String, String> record);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_PublisherModule");
        assertThat(clazz).isNotNull();
        var m = clazz.getMethod("testProducer_PublisherFactory", KafkaPublisherTelemetryFactory.class, KafkaPublisherConfig.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class, Serializer.class);
        assertThat(m).isNotNull();
        assertThat(m.getParameters()[3].getAnnotationsByType(Tag.class)).isNotEmpty();
        assertThat(m.getParameters()[3].getAnnotationsByType(Tag.class)[0].value()).isEqualTo(String.class);
        assertThat(m.getParameters()[4].getAnnotationsByType(Tag.class)).isEmpty();
    }

    @Test
    public void testPublisherWithRecordWithValueTag() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            import io.koraframework.common.annotation.Tag;@KafkaPublisher("test")
            public interface TestProducer {
              void send(ProducerRecord<String, @Tag(String.class) String> record);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_PublisherModule");
        assertThat(clazz).isNotNull();
        var m = clazz.getMethod("testProducer_PublisherFactory", KafkaPublisherTelemetryFactory.class, KafkaPublisherConfig.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class, Serializer.class);
        assertThat(m).isNotNull();
        assertThat(m.getParameters()[3].getAnnotationsByType(Tag.class)).isEmpty();
        assertThat(m.getParameters()[4].getAnnotationsByType(Tag.class)).isNotEmpty();
        assertThat(m.getParameters()[4].getAnnotationsByType(Tag.class)[0].value()).isEqualTo(String.class);
    }

    @Test
    public void testPublisherWithValue() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              @Topic("test.sendTopic")
              void send(String value);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_Impl");
        assertThat(clazz).isNotNull();
        clazz.getConstructor(KafkaPublisherTelemetryFactory.class, KafkaPublisherTelemetryConfig.class, Properties.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class);
    }

    @Test
    public void testPublisherWithValueAndCallback() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              @Topic("test")
              void send(String value, Callback callback);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_Impl");
        assertThat(clazz).isNotNull();
        clazz.getConstructor(KafkaPublisherTelemetryFactory.class, KafkaPublisherTelemetryConfig.class, Properties.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class);
    }

    @Test
    public void testPublisherWithValueAndHeaders() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              @Topic("test.sendTopic")
              void send(String value, Headers headers);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_Impl");
        assertThat(clazz).isNotNull();
        clazz.getConstructor(KafkaPublisherTelemetryFactory.class, KafkaPublisherTelemetryConfig.class, Properties.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class);
    }

    @Test
    public void testPublisherWithValueWithTag() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            import io.koraframework.common.annotation.Tag;@KafkaPublisher("test")
            public interface TestProducer {
              @Topic("test.sendTopic")
              void send(@Tag(String.class) String value);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_PublisherModule");
        var m = clazz.getMethod("testProducer_PublisherFactory", KafkaPublisherTelemetryFactory.class, KafkaPublisherConfig.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class);
        assertThat(m).isNotNull();
        assertThat(m.getParameters()[3].getAnnotationsByType(Tag.class)).isNotEmpty();
        assertThat(m.getParameters()[3].getAnnotationsByType(Tag.class)[0].value()).isEqualTo(String.class);
    }

    @Test
    public void testPublisherWithKeyAndValue() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              @Topic("test.sendTopic")
              void send(Long key, String value);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_Impl");
        assertThat(clazz).isNotNull();
        clazz.getConstructor(KafkaPublisherTelemetryFactory.class, KafkaPublisherTelemetryConfig.class, Properties.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class, Serializer.class);
    }

    @Test
    public void testPublisherWithKeyAndValueAndHeaders() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              @Topic("test.sendTopic")
              void send(Long key, String value, Headers headers);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_Impl");
        assertThat(clazz).isNotNull();
        clazz.getConstructor(KafkaPublisherTelemetryFactory.class, KafkaPublisherTelemetryConfig.class, Properties.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class, Serializer.class);
    }

    @Test
    public void testPublisherWithKeyAndValueWithTag() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            import io.koraframework.common.annotation.Tag;@KafkaPublisher("test")
            public interface TestProducer {
              @Topic("test.sendTopic")
              void send(Long key, @Tag(String.class) String value);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_PublisherModule");
        var m = clazz.getMethod("testProducer_PublisherFactory", KafkaPublisherTelemetryFactory.class, KafkaPublisherConfig.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class, Serializer.class);
        assertThat(m).isNotNull();
        assertThat(m.getParameters()[3].getAnnotationsByType(Tag.class)).isEmpty();
        assertThat(m.getParameters()[4].getAnnotationsByType(Tag.class)).isNotEmpty();
        assertThat(m.getParameters()[4].getAnnotationsByType(Tag.class)[0].value()).isEqualTo(String.class);
    }

    @Test
    public void testPublisherWithValueRelativeConfigPath() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              @Topic(".sendTopic")
              void send(String value);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_Impl");
        assertThat(clazz).isNotNull();
        clazz.getConstructor(KafkaPublisherTelemetryFactory.class, KafkaPublisherTelemetryConfig.class, Properties.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class);
    }

    @Test
    public void testTxPublisher() {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              @Topic("test.sendTopic")
              void send(Long key, String value);
            }
            """, """
            @KafkaPublisher("test")
            public interface TxProducer extends TransactionalPublisher<TestProducer> {
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TxProducer_Impl");
        assertThat(clazz).isNotNull();
    }

    @Test
    public void testReturnVoid() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              @Topic("test.sendTopic")
              void send(String value);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_Impl");
        assertThat(clazz).isNotNull();
        clazz.getConstructor(KafkaPublisherTelemetryFactory.class, KafkaPublisherTelemetryConfig.class, Properties.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class);
    }

    @Test
    public void testReturnFuture() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              @Topic("test.sendTopic")
              java.util.concurrent.Future<?> send(String value);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_Impl");
        assertThat(clazz).isNotNull();
        clazz.getConstructor(KafkaPublisherTelemetryFactory.class, KafkaPublisherTelemetryConfig.class, Properties.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class);
    }

    @Test
    public void testReturnStageFuture() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              @Topic("test.sendTopic")
              java.util.concurrent.CompletionStage<?> send(String value);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_Impl");
        assertThat(clazz).isNotNull();
        clazz.getConstructor(KafkaPublisherTelemetryFactory.class, KafkaPublisherTelemetryConfig.class, Properties.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class);
    }

    @Test
    public void testReturnCompletableFuture() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              @Topic("test.sendTopic")
              java.util.concurrent.CompletableFuture<?> send(String value);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_Impl");
        assertThat(clazz).isNotNull();
        clazz.getConstructor(KafkaPublisherTelemetryFactory.class, KafkaPublisherTelemetryConfig.class, Properties.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class);
    }

    @Test
    public void testReturnRecordMetadata() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              @Topic("test.sendTopic")
              RecordMetadata send(String value);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_Impl");
        assertThat(clazz).isNotNull();
        clazz.getConstructor(KafkaPublisherTelemetryFactory.class, KafkaPublisherTelemetryConfig.class, Properties.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class);
    }

    @Test
    public void testReturnRecordMetadataFuture() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              @Topic("test.sendTopic")
              java.util.concurrent.Future<RecordMetadata> send(String value);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_Impl");
        assertThat(clazz).isNotNull();
        clazz.getConstructor(KafkaPublisherTelemetryFactory.class, KafkaPublisherTelemetryConfig.class, Properties.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class);
    }

    @Test
    public void testReturnRecordMetadataStageFuture() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              @Topic("test.sendTopic")
              java.util.concurrent.CompletionStage<RecordMetadata> send(String value);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_Impl");
        assertThat(clazz).isNotNull();
        clazz.getConstructor(KafkaPublisherTelemetryFactory.class, KafkaPublisherTelemetryConfig.class, Properties.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class);
    }

    @Test
    public void testReturnRecordMetadataCompletableFuture() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              @Topic("test.sendTopic")
              java.util.concurrent.CompletableFuture<RecordMetadata> send(String value);
            }
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_Impl");
        assertThat(clazz).isNotNull();
        clazz.getConstructor(KafkaPublisherTelemetryFactory.class, KafkaPublisherTelemetryConfig.class, Properties.class, compileResult.loadClass("$TestProducer_TopicConfig"), Serializer.class);
    }

    @Test
    public void kafkaPublisherWithAop() throws Exception {
        compile(List.of(new KafkaPublisherAnnotationProcessor(), new AopAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              @io.koraframework.logging.common.annotation.Log
              @Topic("test.sendTopic")
              void send(Long key, String value);
            }
            """);
    }

    @Test
    public void testPublisherWithRecordMethodThenTopicMethod() {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              void send(ProducerRecord<String, String> record);
              @Topic("test.sendTopic")
              void send(String key, String value);
            }
            """);
        this.compileResult.assertSuccess();
        var topicConfig = compileResult.loadClass("$TestProducer_TopicConfig");
        assertThat(Arrays.stream(topicConfig.getRecordComponents()).map(RecordComponent::getName)).containsExactly("topic1");
    }

    @Test
    public void testPublisherWithTopicMethodThenRecordMethod() {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              @Topic("test.sendTopic")
              void send(String key, String value);
              void send(ProducerRecord<String, String> record);
            }
            """);
        this.compileResult.assertSuccess();
        var topicConfig = compileResult.loadClass("$TestProducer_TopicConfig");
        assertThat(Arrays.stream(topicConfig.getRecordComponents()).map(RecordComponent::getName)).containsExactly("topic0");
    }

    @Test
    public void testSerializationFailureEndsObservation() throws Exception {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              void send(ProducerRecord<String, String> record);
            }
            """);
        this.compileResult.assertSuccess();
        var calls = new CopyOnWriteArrayList<String>();
        Serializer<String> failing = (topic, data) -> { throw new IllegalArgumentException("cannot serialize"); };
        var publisher = newPublisher(calls, Mockito.mock(Producer.class), null, failing);

        var send = publisher.getClass().getMethod("send", ProducerRecord.class);
        assertThatThrownBy(() -> send.invoke(publisher, new ProducerRecord<>("topic", "k", "v")))
            .cause()
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("cannot serialize");
        assertThat(calls).containsSubsequence("observeError", "end").doesNotContain("onCompletion");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSyncSendFailureEndsObservation() throws Exception {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              @Topic("test.sendTopic")
              void send(String value);
            }
            """);
        this.compileResult.assertSuccess();
        var calls = new CopyOnWriteArrayList<String>();
        Producer<byte[], byte[]> producer = Mockito.mock(Producer.class);
        Mockito.when(producer.send(Mockito.any(), Mockito.any())).thenThrow(new IllegalStateException("Cannot perform operation after producer has been closed"));
        var publisher = newPublisher(calls, producer, newTopicConfig(1), new StringSerializer());

        var send = publisher.getClass().getMethod("send", String.class);
        assertThatThrownBy(() -> send.invoke(publisher, "v")).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(calls).containsSubsequence("observeError", "end");
        assertThat(calls).filteredOn("end"::equals).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAsyncSendFailureCompletesFutureExceptionally() throws Exception {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
            public interface TestProducer {
              @Topic("test.sendTopic")
              java.util.concurrent.CompletionStage<RecordMetadata> send(String value);
            }
            """);
        this.compileResult.assertSuccess();
        var calls = new CopyOnWriteArrayList<String>();
        Producer<byte[], byte[]> producer = Mockito.mock(Producer.class);
        Mockito.when(producer.send(Mockito.any(), Mockito.any())).thenThrow(new IllegalStateException("Cannot perform operation after producer has been closed"));
        var publisher = newPublisher(calls, producer, newTopicConfig(1), new StringSerializer());

        var send = publisher.getClass().getMethod("send", String.class);
        CompletionStage<?> result;
        try {
            result = (CompletionStage<?>) send.invoke(publisher, "v");
        } catch (InvocationTargetException e) {
            throw new AssertionError("send threw instead of completing the future exceptionally", e.getCause());
        }
        assertThat(result.toCompletableFuture()).isCompletedExceptionally();
        assertThat(calls).containsSubsequence("observeError", "end");
    }

    private Object newTopicConfig(int topics) throws Exception {
        KafkaPublisherConfig.TopicConfig topic = new KafkaPublisherConfig.TopicConfig() {
            @Override
            public String topic() {return "test-topic";}

            @Override
            public @Nullable Integer partition() {return null;}
        };
        var args = new Object[topics];
        Arrays.fill(args, topic);
        return compileResult.loadClass("$TestProducer_TopicConfig").getConstructors()[0].newInstance(args);
    }

    private Object newPublisher(List<String> calls, Producer<?, ?> producer, @Nullable Object topicConfig, Serializer<String> serializer) throws Exception {
        var observation = (KafkaPublisherRecordObservation) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{KafkaPublisherRecordObservation.class}, (p, m, a) -> {
            calls.add(m.getName());
            if (m.getName().equals("span")) return Span.getInvalid();
            return null;
        });
        var telemetry = new KafkaPublisherTelemetry() {
            @Override
            public MeterRegistry meterRegistry() {return null;}

            @Override
            public KafkaPublisherTransactionObservation observeTx() {return null;}

            @Override
            public KafkaPublisherRecordObservation observeSend(String topic) {return observation;}
        };
        KafkaPublisherTelemetryFactory factory = (a, b, c, d) -> telemetry;
        var telemetryConfig = Mockito.mock(KafkaPublisherTelemetryConfig.class, Mockito.RETURNS_DEEP_STUBS);
        var publisher = compileResult.loadClass("$TestProducer_Impl").getConstructors()[0].newInstance(factory, telemetryConfig, new Properties(), topicConfig, serializer);
        var delegate = AbstractPublisher.class.getDeclaredField("delegate");
        delegate.setAccessible(true);
        delegate.set(publisher, producer);
        return publisher;
    }
}
