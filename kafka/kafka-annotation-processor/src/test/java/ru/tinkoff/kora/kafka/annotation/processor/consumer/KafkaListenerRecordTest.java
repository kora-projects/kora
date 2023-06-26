package ru.tinkoff.kora.kafka.annotation.processor.consumer;

import org.apache.kafka.common.serialization.Deserializer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.kafka.common.config.KafkaConsumerConfig;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.KafkaRecordHandler;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaListenerRecordTest extends AbstractKafkaListenerAnnotationProcessorTest {
    @Test
    public void testProcessRecord() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(ConsumerRecord<String, String> event) {
                }
            }
            """)
            .handler(String.class, String.class);

        handler.handle(record("test", "test-value"), i -> i
            .assertRecord(0)
            .hasKey("test")
            .hasValue("test-value"));
    }

    @Test
    public void testProcessRecordWithTag() throws NoSuchMethodException {
        compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(ConsumerRecord<@Tag(KafkaListenerClass.class) String, @Tag(String.class) String> event) {
                }
            }
            """);

        compileResult.assertSuccess();
        var module = compileResult.loadClass("KafkaListenerClassModule");
        var container = module.getMethod("kafkaListenerClassProcessContainer", KafkaConsumerConfig.class, KafkaRecordHandler.class, Deserializer.class, Deserializer.class, KafkaConsumerTelemetry.class);
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
    public void testProcessRecordAnyKeyType() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(ConsumerRecord<?, String> event) {
                }
            }
            """)
            .handler(byte[].class, String.class);

        handler.handle(record("test".getBytes(), "test-value"), i -> i
            .assertRecord(0)
            .hasKey("test".getBytes())
            .hasValue("test-value")
        );
        handler.handle(errorKey("test-value"), i -> i
            .assertRecord(0)
            .hasKeyError()
            .hasValue("test-value")
        );
        handler.handle(errorValue("test".getBytes()), i -> i
            .assertRecord(0)
            .hasKey("test".getBytes())
            .hasValueError());
    }

    @Test
    public void testProcessRecordAndConsumer() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(Consumer<String, String> consumer, ConsumerRecord<String, String> event) {
                }
            }
            """)
            .handler(String.class, String.class);

        handler.handle(record("test", "test-value"), i -> i
            .assertConsumer(0)
            .assertRecord(1)
            .hasKey("test")
            .hasValue("test-value")
        );
        handler.handle(errorKey("test-value"), i -> i
            .assertConsumer(0)
            .assertRecord(1)
            .hasKeyError()
            .hasValue("test-value")
        );
        handler.handle(errorValue("test"), i -> i
            .assertConsumer(0)
            .assertRecord(1)
            .hasKey("test")
            .hasValueError());
    }

    @Test
    public void testProcessRecordAndKeyParseException() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(ConsumerRecord<String, String> event, RecordKeyDeserializationException exception) {
                }
            }
            """)
            .handler(String.class, String.class);

        handler.handle(record("test", "test-value"), i -> i
            .assertNoException(1)
            .assertRecord(0)
            .hasKey("test")
            .hasValue("test-value")
        );
        handler.handle(errorKey("test-value"), i -> i
            .assertKeyException(1)
            .assertRecord(0)
            .hasKeyError()
            .hasValue("test-value")
        );
        handler.handle(errorValue("test"), i -> i
            .assertNoException(1)
            .assertRecord(0)
            .hasKey("test")
            .hasValueError());
    }

    @Test
    public void testProcessRecordAndValueParseException() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(ConsumerRecord<String, String> event, RecordValueDeserializationException exception) {
                }
            }
            """)
            .handler(String.class, String.class);

        handler.handle(record("test", "test-value"), i -> i
            .assertNoException(1)
            .assertRecord(0)
            .hasKey("test")
            .hasValue("test-value")
        );
        handler.handle(errorKey("test-value"), i -> i
            .assertNoException(1)
            .assertRecord(0)
            .hasKeyError()
            .hasValue("test-value")
        );
        handler.handle(errorValue("test"), i -> i
            .assertValueException(1)
            .assertRecord(0)
            .hasKey("test")
            .hasValueError());
    }

    @Test
    public void testProcessRecordAndParseException() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(ConsumerRecord<String, String> event, Exception exception) {
                }
            }
            """)
            .handler(String.class, String.class);

        handler.handle(record("test", "test-value"), i -> i
            .assertNoException(1)
            .assertRecord(0)
            .hasKey("test")
            .hasValue("test-value")
        );
        handler.handle(errorKey("test-value"), i -> i
            .assertKeyException(1)
            .assertRecord(0)
            .hasKeyError()
            .hasValue("test-value")
        );
        handler.handle(errorValue("test"), i -> i
            .assertValueException(1)
            .assertRecord(0)
            .hasKey("test")
            .hasValueError()
        );
    }

    @Test
    public void testProcessRecordAndParseThrowable() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(ConsumerRecord<String, String> event, Throwable exception) {
                }
            }
            """)
            .handler(String.class, String.class);

        handler.handle(record("test", "test-value"), i -> i
            .assertNoException(1)
            .assertRecord(0)
            .hasKey("test")
            .hasValue("test-value")
        );
        handler.handle(errorKey("test-value"), i -> i
            .assertKeyException(1)
            .assertRecord(0)
            .hasKeyError()
            .hasValue("test-value")
        );
        handler.handle(errorValue("test"), i -> i
            .assertValueException(1)
            .assertRecord(0)
            .hasKey("test")
            .hasValueError()
        );
    }

}
