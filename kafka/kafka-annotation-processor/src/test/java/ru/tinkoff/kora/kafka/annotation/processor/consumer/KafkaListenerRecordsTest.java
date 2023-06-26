package ru.tinkoff.kora.kafka.annotation.processor.consumer;

import org.apache.kafka.common.serialization.Deserializer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.kafka.common.config.KafkaConsumerConfig;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.KafkaRecordsHandler;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaListenerRecordsTest extends AbstractKafkaListenerAnnotationProcessorTest {
    @Test
    public void testProcessRecords() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(ConsumerRecords<byte[], String> event) {
                }
            }
            """)
            .recordsHandler(byte[].class, String.class);

        handler.handle(record("test".getBytes(), "test-value"), i -> i
            .assertRecords(0)
            .hasSize(1)
            .hasRecord(0, v -> v
                .hasKey("test".getBytes())
                .hasValue("test-value")
            )
        );
        handler.handle(errorKey("test-value"), i -> i
            .assertRecords(0)
            .hasSize(1)
            .hasRecord(0, v -> v
                .hasKeyError()
                .hasValue("test-value")
            )
        );
        handler.handle(errorValue("test".getBytes()), i -> i
            .assertRecords(0)
            .hasSize(1)
            .hasRecord(0, v -> v
                .hasKey("test".getBytes())
                .hasValueError()
            )
        );
    }

    @Test
    public void testProcessRecordsWithTag() throws NoSuchMethodException {
        compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(ConsumerRecords<@Tag(KafkaListenerClass.class) byte[], @Tag(String.class) String> event) {
                }
            }
            """)
            .recordsHandler(byte[].class, String.class);

        compileResult.assertSuccess();
        var module = compileResult.loadClass("KafkaListenerClassModule");
        var container = module.getMethod("kafkaListenerClassProcessContainer", KafkaConsumerConfig.class, KafkaRecordsHandler.class, Deserializer.class, Deserializer.class, KafkaConsumerTelemetry.class);
        var keyDeserializer = container.getParameters()[2];
        var valueDeserializer = container.getParameters()[3];

        var keyTag = keyDeserializer.getAnnotation(Tag.class);
        var valueTag = valueDeserializer.getAnnotation(Tag.class);

        assertThat(keyTag).isNotNull()
            .extracting(Tag::value, InstanceOfAssertFactories.array(Class[].class))
            .isEqualTo(new Class<?>[]{compileResult.loadClass("KafkaListenerClass")});
        assertThat(valueTag).isNotNull()
            .extracting(Tag::value, InstanceOfAssertFactories.array(Class[].class))
            .isEqualTo(new Class<?>[]{String.class});
    }

    @Test
    public void testProcessRecordsAnyKeyType() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(ConsumerRecords<?, String> event) {
                }
            }
            """)
            .recordsHandler(byte[].class, String.class);

        handler.handle(record("test".getBytes(), "test-value"), i -> i
            .assertRecords(0)
            .hasSize(1)
            .hasRecord(0, v -> v
                .hasKey("test".getBytes())
                .hasValue("test-value")
            )
        );
    }

    @Test
    public void testProcessRecordsAndConsumer() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(Consumer<?, ?> consumer, ConsumerRecords<String, String> event) {
                }
            }
            """)
            .recordsHandler(String.class, String.class);

        handler.handle(record("test", "test-value"), i -> i
            .assertConsumer(0)
            .assertRecords(1)
            .hasSize(1)
            .hasRecord(0, v -> v
                .hasKey("test")
                .hasValue("test-value")
            )
        );
    }

    @Test
    public void testProcessRecordsAndConsumerAndTelemetry() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(Consumer<?, ?> consumer, ConsumerRecords<byte[], String> event, KafkaConsumerTelemetry.KafkaConsumerRecordsTelemetryContext<?, ?> telemetry) {
                }
            }
            """)
            .recordsHandler(byte[].class, String.class);

        handler.handle(record("test".getBytes(), "test-value"), i -> i
            .assertConsumer(0)
            .assertTelemetry(2)
            .assertRecords(1)
            .hasSize(1)
            .hasRecord(0, v -> v
                .hasKey("test".getBytes())
                .hasValue("test-value")
            )
        );
    }

    @Test
    public void testProcessRecordsAndTelemetry() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(KafkaConsumerTelemetry.KafkaConsumerRecordsTelemetryContext<?, ?> telemetry, ConsumerRecords<byte[], String> event) {
                }
            }
            """)
            .recordsHandler(byte[].class, String.class);

        handler.handle(record("test".getBytes(), "test-value"), i -> i
            .assertTelemetry(0)
            .assertRecords(1)
            .hasSize(1)
            .hasRecord(0, v -> v
                .hasKey("test".getBytes())
                .hasValue("test-value")
            )
        );
    }
}
