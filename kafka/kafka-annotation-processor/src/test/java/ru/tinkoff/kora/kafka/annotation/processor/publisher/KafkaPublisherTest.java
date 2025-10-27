package ru.tinkoff.kora.kafka.annotation.processor.publisher;

import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.kafka.annotation.processor.producer.KafkaPublisherAnnotationProcessor;
import ru.tinkoff.kora.kafka.common.producer.KafkaPublisherConfig;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaPublisherTelemetryConfig;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaPublisherTelemetryFactory;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaPublisherTest extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import ru.tinkoff.kora.kafka.common.producer.TransactionalPublisher;
            import ru.tinkoff.kora.kafka.common.annotation.KafkaPublisher;
            import ru.tinkoff.kora.kafka.common.annotation.KafkaPublisher.Topic;
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
            @KafkaPublisher("test")
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
        assertThat(m.getParameters()[3].getAnnotationsByType(Tag.class)[0].value()).isEqualTo(new Class<?>[]{String.class});
        assertThat(m.getParameters()[4].getAnnotationsByType(Tag.class)).isEmpty();
    }

    @Test
    public void testPublisherWithRecordWithValueTag() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @KafkaPublisher("test")
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
        assertThat(m.getParameters()[4].getAnnotationsByType(Tag.class)[0].value()).isEqualTo(new Class<?>[]{String.class});
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
            @KafkaPublisher("test")
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
        assertThat(m.getParameters()[3].getAnnotationsByType(Tag.class)[0].value()).isEqualTo(new Class<?>[]{String.class});
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
            @KafkaPublisher("test")
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
        assertThat(m.getParameters()[4].getAnnotationsByType(Tag.class)[0].value()).isEqualTo(new Class<?>[]{String.class});
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
              @ru.tinkoff.kora.logging.common.annotation.Log
              @Topic("test.sendTopic")
              void send(Long key, String value);
            }
            """);
    }
}
