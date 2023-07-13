package ru.tinkoff.kora.kafka.annotation.processor.consumer;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Deserializer;
import org.assertj.core.api.ThrowingConsumer;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.annotation.processor.common.CompileResult;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.kafka.common.config.KafkaConsumerConfig;
import ru.tinkoff.kora.kafka.common.consumer.containers.ConsumerRecordWrapper;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.KafkaRecordHandler;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.KafkaRecordsHandler;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;
import ru.tinkoff.kora.kafka.common.exceptions.RecordKeyDeserializationException;
import ru.tinkoff.kora.kafka.common.exceptions.RecordValueDeserializationException;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public abstract class AbstractKafkaListenerAnnotationProcessorTest extends AbstractAnnotationProcessorTest {
    protected Consumer consumer = Mockito.mock(Consumer.class);
    protected KafkaConsumerTelemetry.KafkaConsumerRecordTelemetryContext recordTelemetry = Mockito.mock(KafkaConsumerTelemetry.KafkaConsumerRecordTelemetryContext.class);
    protected KafkaConsumerTelemetry.KafkaConsumerRecordsTelemetryContext recordsTelemetry = Mockito.mock(KafkaConsumerTelemetry.KafkaConsumerRecordsTelemetryContext.class);

    @BeforeEach
    void setUp() {
        when(recordsTelemetry.get(Mockito.any())).thenReturn(recordTelemetry);
    }

    protected <K, V> ConsumerRecord<K, V> record(K key, V value) {
        return new ConsumerRecord<>("test", 1, 1, key, value);
    }

    protected <K, V> ConsumerRecord<K, V> errorValue() {
        var errorDeser = Mockito.mock(Deserializer.class);
        when(errorDeser.deserialize(any(), any(), any())).thenThrow(IllegalArgumentException.class);
        when(errorDeser.deserialize(any(), any())).thenThrow(IllegalArgumentException.class);
        Deserializer<K> deser = (topic, data) -> null;

        return new ConsumerRecordWrapper<>(new ConsumerRecord<>("test", 1, 1, "test".getBytes(), "test".getBytes()), deser, errorDeser);
    }

    protected <K, V> ConsumerRecord<K, V> errorValue(K key) {
        var errorDeser = Mockito.mock(Deserializer.class);
        when(errorDeser.deserialize(any(), any(), any())).thenThrow(IllegalArgumentException.class);
        when(errorDeser.deserialize(any(), any())).thenThrow(IllegalArgumentException.class);
        Deserializer<K> deser = (topic, data) -> key;

        return new ConsumerRecordWrapper<>(new ConsumerRecord<>("test", 1, 1, "test".getBytes(), "test".getBytes()), deser, errorDeser);
    }

    protected <K, V> ConsumerRecord<K, V> errorKey(V value) {
        var errorDeser = Mockito.mock(Deserializer.class);
        when(errorDeser.deserialize(any(), any(), any())).thenThrow(IllegalArgumentException.class);
        when(errorDeser.deserialize(any(), any())).thenThrow(IllegalArgumentException.class);
        Deserializer<V> deser = (topic, data) -> value;

        return new ConsumerRecordWrapper<>(new ConsumerRecord<>("test", 1, 1, "test".getBytes(), "test".getBytes()), errorDeser, deser);
    }

    @SuppressWarnings("unchecked")
    protected static <K, V> RecordAssert<K, V> assertRecord(InvocationOnMock invocation, int i) {
        return new RecordAssert<>((ConsumerRecord<K, V>) invocation.getArgument(i, ConsumerRecord.class));
    }

    @SuppressWarnings("unchecked")
    protected static <K, V> RecordsAssert<K, V> assertRecords(InvocationOnMock invocation, int i) {
        return new RecordsAssert<>((ConsumerRecords<K, V>) invocation.getArgument(i, ConsumerRecords.class));
    }

    protected static class RecordsAssert<K, V> {
        private final ConsumerRecords<K, V> records;

        protected RecordsAssert(ConsumerRecords<K, V> records) {
            this.records = records;
        }

        public RecordsAssert<K, V> hasSize(int size) {
            assertThat(records).hasSize(size);
            return this;
        }

        public RecordsAssert<K, V> hasRecord(int index, java.util.function.Consumer<RecordAssert<K, V>> verify) {
            var iterator = records.iterator();
            var i = 0;
            while (iterator.hasNext()) {
                var next = iterator.next();
                if (i == index) {
                    verify.accept(new RecordAssert<>(next));
                    return this;
                }
            }

            return this;
        }
    }

    protected static class RecordAssert<K, V> {
        private final ConsumerRecord<K, V> record;

        public RecordAssert(ConsumerRecord<K, V> record) {
            assertThat(record).isNotNull();
            this.record = record;
        }

        public RecordAssert<K, V> hasKey(K key) {
            assertThat(record.key()).isNotNull().isEqualTo(key);
            return this;
        }

        public RecordAssert<K, V> hasValue(V value) {
            assertThat(record.value()).isNotNull().isEqualTo(value);
            return this;
        }

        public RecordAssert<K, V> hasKeyError() {
            assertThatThrownBy(record::key).isInstanceOf(RecordKeyDeserializationException.class);
            return this;
        }

        public RecordAssert<K, V> hasValueError() {
            assertThatThrownBy(record::value).isInstanceOf(RecordValueDeserializationException.class);
            return this;
        }
    }

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import ru.tinkoff.kora.kafka.common.annotation.KafkaListener;
            import org.apache.kafka.clients.consumer.ConsumerRecords;
            import org.apache.kafka.clients.consumer.ConsumerRecord;
            import org.apache.kafka.clients.consumer.Consumer;
            import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;
            import ru.tinkoff.kora.kafka.common.exceptions.RecordKeyDeserializationException;
            import ru.tinkoff.kora.kafka.common.exceptions.RecordValueDeserializationException;
            """;
    }

    protected ListenerModule compile(@Language("java") String... sources) {
        super.compile(List.of(new KafkaListenerAnnotationProcessor()), sources);
        compileResult.assertSuccess();
        return new ListenerModule(compileResult);
    }

    protected class ListenerModule {
        private final CompileResult compileResult;
        private final Class<?> controllerClass;
        private final Class<?> moduleClass;
        private final Class<?>[] tagValue;

        protected ListenerModule(CompileResult compileResult) {
            this.compileResult = compileResult;
            this.controllerClass = Objects.requireNonNull(compileResult.loadClass("KafkaListenerClass"));
            this.moduleClass = Objects.requireNonNull(compileResult.loadClass("KafkaListenerClassModule"));
            this.tagValue = new Class<?>[]{compileResult.loadClass("KafkaListenerClassModule$KafkaListenerClassProcessTag")};
        }

        protected <K, V> ListenerModuleAssertions<K, V>.RecordHandlerAssertions handler(Class<K> keyType, Class<V> valueType) {
            return new ListenerModuleAssertions<>(keyType, valueType)
                .verifyConfig()
                .verifyRecordContainer()
                .verifyRecordHandler();
        }

        protected <K, V> ListenerModuleAssertions<K, V>.RecordsHandlerAssertions recordsHandler(Class<K> keyType, Class<V> valueType) {
            return new ListenerModuleAssertions<>(keyType, valueType)
                .verifyConfig()
                .verifyRecordsContainer()
                .verifyRecordsHandler();
        }

        protected class ListenerModuleAssertions<K, V> {
            private final Type keyType;
            private final Type valueType;

            public ListenerModuleAssertions(Class<K> keyType, Class<V> valueType) {
                this.keyType = keyType;
                this.valueType = valueType;
            }


            public ListenerModuleAssertions<K, V> verifyConfig() {
                var configMethod = Arrays.stream(moduleClass.getMethods()).filter(m -> m.getName().equals("kafkaListenerClassProcessConfig")).findFirst().orElseThrow();
                assertThat(configMethod.getReturnType()).isEqualTo(KafkaConsumerConfig.class);
                assertThat(configMethod.getParameters()[0].getType()).isEqualTo(Config.class);
                assertThat(configMethod.getParameters()[1].getParameterizedType()).isEqualTo(TypeRef.of(ConfigValueExtractor.class, KafkaConsumerConfig.class));
                assertThat(configMethod.getAnnotation(Tag.class).value()).isEqualTo(tagValue);

                return this;
            }

            private Method assertContainer() {
                var containerMethod = Arrays.stream(moduleClass.getMethods()).filter(m -> m.getName().equals("kafkaListenerClassProcessContainer")).findFirst().orElseThrow();
                assertThat(containerMethod.getReturnType()).isEqualTo(Lifecycle.class);
                assertThat(containerMethod.getParameters()[0].getType()).isEqualTo(KafkaConsumerConfig.class);
                assertThat(containerMethod.getParameters()[0].getAnnotation(Tag.class).value()).isEqualTo(tagValue);
                assertThat(containerMethod.getParameters()[2].getParameterizedType()).isEqualTo(TypeRef.of(Deserializer.class, keyType));
                assertThat(containerMethod.getParameters()[3].getParameterizedType()).isEqualTo(TypeRef.of(Deserializer.class, valueType));
                assertThat(containerMethod.getParameters()[4].getParameterizedType()).isEqualTo(TypeRef.of(KafkaConsumerTelemetry.class, keyType, valueType));

                return containerMethod;
            }

            public ListenerModuleAssertions<K, V> verifyRecordContainer() {
                var containerMethod = assertContainer();
                assertThat(containerMethod.getParameters()[1].getParameterizedType()).isEqualTo(TypeRef.of(KafkaRecordHandler.class, keyType, valueType));
                assertThat(containerMethod.getParameters()[1].getAnnotation(Tag.class).value()).isEqualTo(tagValue);

                return this;
            }

            public ListenerModuleAssertions<K, V> verifyRecordsContainer() {
                var containerMethod = assertContainer();
                assertThat(containerMethod.getParameters()[1].getParameterizedType()).isEqualTo(TypeRef.of(KafkaRecordsHandler.class, keyType, valueType));
                assertThat(containerMethod.getParameters()[1].getAnnotation(Tag.class).value()).isEqualTo(tagValue);

                return this;
            }

            public RecordHandlerAssertions verifyRecordHandler() {
                return new RecordHandlerAssertions(controllerClass, moduleClass);
            }

            public RecordsHandlerAssertions verifyRecordsHandler() {
                return new RecordsHandlerAssertions(controllerClass, moduleClass);
            }

            protected class AbstractHandlerAssertions {
                protected final Object mock;
                protected final Method handlerMethod;
                protected final Object module;
                protected volatile InvocationOnMock invocation;

                public AbstractHandlerAssertions(Class<?> controllerClass, Class<?> moduleClass) {
                    this.mock = Mockito.mock(controllerClass, new Answer() {
                        @Override
                        public Object answer(InvocationOnMock invocation) throws Throwable {
                            AbstractHandlerAssertions.this.invocation = invocation;
                            return invocation.callRealMethod();
                        }
                    });
                    this.handlerMethod = Arrays.stream(moduleClass.getMethods()).filter(m -> m.getName().equals("kafkaListenerClassProcessHandler")).findFirst().orElseThrow();
                    assertThat(handlerMethod.getAnnotation(Tag.class).value()).isEqualTo(tagValue);
                    this.module = Proxy.newProxyInstance(moduleClass.getClassLoader(), new Class[]{moduleClass}, (proxy, method, args) -> MethodHandles.privateLookupIn(moduleClass, MethodHandles.lookup())
                        .in(moduleClass)
                        .unreflectSpecial(method, moduleClass)
                        .bindTo(proxy)
                        .invokeWithArguments(args));
                }
            }

            @SuppressWarnings("unchecked")
            protected class RecordHandlerAssertions extends AbstractHandlerAssertions {
                private final KafkaRecordHandler<K, V> moduleHandler;

                @SuppressWarnings("unchecked")
                protected RecordHandlerAssertions(Class<?> controllerClass, Class<?> moduleClass) {
                    super(controllerClass, moduleClass);
                    try {
                        this.moduleHandler = (KafkaRecordHandler<K, V>) handlerMethod.invoke(module, this.mock);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                public void handle(ConsumerRecord<K, V> record, ThrowingConsumer<InvocationAssertions<K, V>> verifier) {
                    moduleHandler.handle(consumer, recordTelemetry, record);
                    assertThat(invocation).isNotNull().satisfies(i -> verifier.accept(new InvocationAssertions<K, V>(i)));
                }

                public void handle(ConsumerRecord<K, V> record, Class<? extends Throwable> expectedError) {
                    assertThatThrownBy(() -> moduleHandler.handle(consumer, recordTelemetry, record)).isInstanceOf(expectedError);
                }
            }

            @SuppressWarnings("unchecked")
            protected class RecordsHandlerAssertions extends AbstractHandlerAssertions {
                private final KafkaRecordsHandler<K, V> moduleHandler;

                @SuppressWarnings("unchecked")
                protected RecordsHandlerAssertions(Class<?> controllerClass, Class<?> moduleClass) {
                    super(controllerClass, moduleClass);
                    try {
                        this.moduleHandler = (KafkaRecordsHandler<K, V>) handlerMethod.invoke(module, this.mock);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                public void handle(ConsumerRecords<K, V> record) {
                    moduleHandler.handle(consumer, recordsTelemetry, record);
                }

                public void handle(ConsumerRecord<K, V> record, ThrowingConsumer<InvocationAssertions<K, V>> verifier) {
                    moduleHandler.handle(consumer, recordsTelemetry, new ConsumerRecords<>(Map.of(
                        new TopicPartition("test", 1),
                        List.of(record)
                    )));
                    assertThat(invocation).isNotNull().satisfies(i -> verifier.accept(new InvocationAssertions<K, V>(i)));
                }

                public void handle(ConsumerRecord<K, V> record, Class<? extends Throwable> expectedError) {
                    assertThatThrownBy(() -> {
                        moduleHandler.handle(consumer, recordsTelemetry, new ConsumerRecords<>(Map.of(
                            new TopicPartition("test", 1),
                            List.of(record)
                        )));
                    }).isInstanceOf(expectedError);
                }
            }

            protected static class InvocationAssertions<K, V> {

                private final InvocationOnMock invocation;

                public InvocationAssertions(InvocationOnMock i) {
                    this.invocation = i;
                }

                public RecordAssert<K, V> assertRecord(int i) {
                    return AbstractKafkaListenerAnnotationProcessorTest.assertRecord(invocation, i);
                }

                @SuppressWarnings("unchecked")
                public RecordsAssert<K, V> assertRecords(int i) {
                    var records = (ConsumerRecords<K, V>) invocation.getArgument(i, ConsumerRecords.class);
                    return AbstractKafkaListenerAnnotationProcessorTest.assertRecords(invocation, i);
                }

                public InvocationAssertions<K, V> assertConsumer(int i) {
                    assertThat(invocation.getArgument(i, Consumer.class)).isNotNull();

                    return this;
                }

                private InvocationAssertions<K, V> assertException(int i, Class<? extends Throwable> exceptionClass) {
                    assertThat(invocation.getArgument(i, Throwable.class)).isNotNull().isInstanceOf(exceptionClass);

                    return this;
                }

                public InvocationAssertions<K, V> assertKeyException(int i) {
                    return assertException(i, RecordKeyDeserializationException.class);
                }

                public InvocationAssertions<K, V> assertValueException(int i) {
                    return assertException(i, RecordValueDeserializationException.class);
                }

                public InvocationAssertions<K, V> assertNoException(int i) {
                    assertThat(invocation.getArgument(i, Throwable.class)).isNull();

                    return this;
                }

                public InvocationAssertions<K, V> assertKey(int i, K key) {
                    assertThat(invocation.getArgument(i, Object.class)).isNotNull().isEqualTo(key);

                    return this;
                }

                public InvocationAssertions<K, V> assertNoKey(int i) {
                    assertThat(invocation.getArgument(i, Object.class)).isNull();

                    return this;
                }

                public InvocationAssertions<K, V> assertValue(int i, V value) {
                    assertThat(invocation.getArgument(i, Object.class)).isNotNull().isEqualTo(value);

                    return this;
                }

                public InvocationAssertions<K, V> assertNoValue(int i) {
                    assertThat(invocation.getArgument(i, Object.class)).isNull();

                    return this;
                }

                public InvocationAssertions<K, V> assertTelemetry(int i) {
                    assertThat(invocation.getArgument(i, KafkaConsumerTelemetry.KafkaConsumerRecordsTelemetryContext.class)).isNotNull();

                    return this;
                }
            }

        }
    }
}
