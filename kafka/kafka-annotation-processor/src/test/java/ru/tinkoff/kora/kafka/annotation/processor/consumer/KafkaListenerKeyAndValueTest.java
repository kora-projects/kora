package ru.tinkoff.kora.kafka.annotation.processor.consumer;

import org.apache.kafka.common.serialization.Deserializer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.kafka.common.consumer.ConsumerAwareRebalanceListener;
import ru.tinkoff.kora.kafka.common.consumer.KafkaListenerConfig;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTelemetryFactory;
import ru.tinkoff.kora.kafka.common.exceptions.RecordKeyDeserializationException;
import ru.tinkoff.kora.kafka.common.exceptions.RecordValueDeserializationException;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaListenerKeyAndValueTest extends AbstractKafkaListenerAnnotationProcessorTest {

    @Test
    public void testProcessValue() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(String value) {
                }
            }
            """)
            .handler(byte[].class, String.class);

        handler.handle(record("test".getBytes(), "test-value"), i -> i.assertValue(0, "test-value"));

        handler.handle(errorKey("test-value"), i -> i.assertValue(0, "test-value"));

        handler.handle(errorValue(), RecordValueDeserializationException.class);
    }

    @Test
    public void testProcessValueWithTaggedListener() {
        var handler = compile(String.class, """
            public class KafkaListenerClass {
                @KafkaListener(value = "test.config.path", tag = String.class)
                public void process(String value) {
                }
            }
            """)
            .handler(byte[].class, String.class);

        handler.handle(record("test".getBytes(), "test-value"), i -> i.assertValue(0, "test-value"));

        handler.handle(errorKey("test-value"), i -> i.assertValue(0, "test-value"));

        handler.handle(errorValue(), RecordValueDeserializationException.class);
    }

    @Test
    public void testProcessValueWithTag() throws NoSuchMethodException {
        compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(@Tag(KafkaListenerClass.class) String value) {
                }
            }
            """);
        compileResult.assertSuccess();
        var module = compileResult.loadClass("KafkaListenerClassModule");
        var container = module.getMethod("kafkaListenerClassProcessContainer", KafkaListenerConfig.class, ValueOf.class, Deserializer.class, Deserializer.class, KafkaConsumerTelemetryFactory.class, ConsumerAwareRebalanceListener.class);
        var valueDeserializer = container.getParameters()[3];

        var valueTag = valueDeserializer.getAnnotation(Tag.class);

        assertThat(valueTag).isNotNull()
            .extracting(Tag::value, InstanceOfAssertFactories.array(Class[].class))
            .isEqualTo(new Class<?>[]{compileResult.loadClass("KafkaListenerClass")});
    }

    @Test
    public void testProcessKeyAndValue() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(String key, String value) {
                }
            }
            """)
            .handler(String.class, String.class);

        handler.handle(record("test", "test-value"), i -> {
            i.assertKey(0, "test");
            i.assertValue(1, "test-value");
        });

        handler.handle(errorKey(""), RecordKeyDeserializationException.class);
        handler.handle(errorValue(), RecordValueDeserializationException.class);
    }

    @Test
    public void testProcessKeyAndValueWithTag() throws NoSuchMethodException {
        compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(@Tag(KafkaListenerClass.class) String key, @Tag(String.class) String value) {
                }
            }
            """);
        var module = compileResult.loadClass("KafkaListenerClassModule");
        var container = module.getMethod("kafkaListenerClassProcessContainer", KafkaListenerConfig.class, ValueOf.class, Deserializer.class, Deserializer.class, KafkaConsumerTelemetryFactory.class, ConsumerAwareRebalanceListener.class);
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
    public void testProcessValueAndValueException() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(String value, RecordValueDeserializationException exception) {
                }
            }
            """)
            .handler(byte[].class, String.class);

        handler.handle(record("test".getBytes(), "test-value"), i -> {
            i.assertValue(0, "test-value");
            i.assertNoException(1);
        });

        handler.handle(errorKey("test-value"), i -> {
            i.assertValue(0, "test-value");
            i.assertNoException(1);
        });

        handler.handle(errorValue(), i -> {
            i.assertNoValue(0);
            i.assertValueException(1);
        });
    }

    @Test
    public void testProcessValueAndException() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(String value, Exception exception) {
                }
            }
            """)
            .handler(byte[].class, String.class);

        handler.handle(record("test".getBytes(), "test-value"), i -> {
            i.assertValue(0, "test-value");
            i.assertNoException(1);
        });

        handler.handle(errorKey("test-value"), i -> {
            i.assertValue(0, "test-value");
            i.assertNoException(1);
        });

        handler.handle(errorValue(), i -> {
            i.assertNoValue(0);
            i.assertValueException(1);
        });
    }

    @Test
    public void testProcessKeyValueAndException() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(String key, String value, Exception exception) {
                }
            }
            """)
            .handler(String.class, String.class);

        handler.handle(record("test", "test-value"), i -> {
            i.assertKey(0, "test");
            i.assertValue(1, "test-value");
            i.assertNoException(2);
        });

        handler.handle(errorKey("test-value"), i -> {
            i.assertNoKey(0);
            i.assertNoValue(1);
            i.assertKeyException(2);
        });

        handler.handle(errorValue(), i -> {
            i.assertNoKey(0);
            i.assertNoValue(1);
            i.assertValueException(2);
        });
    }

    @Test
    public void testProcessKeyAndValueAndHeaders() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(String key, String value, Headers headers) {
                }
            }
            """)
            .handler(String.class, String.class);

        handler.handle(record("test", "test-value"), i -> {
            i.assertKey(0, "test");
            i.assertValue(1, "test-value");
        });

        handler.handle(errorKey(""), RecordKeyDeserializationException.class);
        handler.handle(errorValue(), RecordValueDeserializationException.class);
    }

    @Test
    public void testProcessKeyValueHeaderAndException() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(String key, String value, Headers headers, Exception exception) {
                }
            }
            """)
            .handler(String.class, String.class);

        handler.handle(record("test", "test-value"), i -> {
            i.assertKey(0, "test");
            i.assertValue(1, "test-value");
            i.assertHeadersIsEmpty(2);
            i.assertNoException(3);
        });

        handler.handle(errorKey("test-value"), i -> {
            i.assertNoKey(0);
            i.assertNoValue(1);
            i.assertHeadersIsEmpty(2);
            i.assertKeyException(3);
        });

        handler.handle(errorValue(), i -> {
            i.assertNoKey(0);
            i.assertNoValue(1);
            i.assertHeadersIsEmpty(2);
            i.assertValueException(3);
        });
    }

    @Test
    public void testProcessValueAndConsumer() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(Consumer<?, ?> consumer, String value, Exception exception) {
                }
            }
            """)
            .handler(byte[].class, String.class);

        handler.handle(record("test".getBytes(), "test-value"), i -> {
            i.assertConsumer(0);
            i.assertValue(1, "test-value");
            i.assertNoException(2);
        });

        handler.handle(errorKey("test-value"), i -> {
            i.assertConsumer(0);
            i.assertValue(1, "test-value");
            i.assertNoException(2);
        });

        handler.handle(errorValue(), i -> {
            i.assertConsumer(0);
            i.assertNoValue(1);
            i.assertValueException(2);
        });
    }

    @Test
    public void testProcessKeyValueHeadersAndConsumer() {
        var handler = compile("""
            public class KafkaListenerClass {
                @KafkaListener("test.config.path")
                public void process(Consumer<?, ?> consumer, String key, String value, Headers headers, Exception exception) {
                }
            }
            """)
            .handler(String.class, String.class);

        handler.handle(record("test", "test-value"), i -> {
            i.assertConsumer(0);
            i.assertKey(1, "test");
            i.assertValue(2, "test-value");
            i.assertHeadersIsEmpty(3);
            i.assertNoException(4);
        });

        handler.handle(errorKey("test-value"), i -> {
            i.assertConsumer(0);
            i.assertNoKey(1);
            i.assertNoValue(2);
            i.assertHeadersIsEmpty(3);
            i.assertKeyException(4);
        });

        handler.handle(errorValue(), i -> {
            i.assertConsumer(0);
            i.assertNoKey(1);
            i.assertNoValue(2);
            i.assertHeadersIsEmpty(3);
            i.assertValueException(4);
        });
    }
}
