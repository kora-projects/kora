package ru.tinkoff.kora.kafka.annotation.processor;

import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.kafka.annotation.processor.producer.KafkaPublisherAnnotationProcessor;
import ru.tinkoff.kora.kafka.common.producer.PublisherConfig;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerTelemetryFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaPublisherTest extends AbstractAnnotationProcessorTest {
    @Test
    public void testProducer() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @ru.tinkoff.kora.kafka.common.annotation.KafkaPublisher("test")
            public interface TestProducer extends org.apache.kafka.clients.producer.Producer<byte[] ,byte[]> {}
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_Implementation");
        assertThat(clazz).isNotNull();
        clazz.getConstructor(KafkaProducerTelemetryFactory.class, PublisherConfig.class, Serializer.class, Serializer.class);
    }

    @Test
    public void testProducerWithKeyTag() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @ru.tinkoff.kora.kafka.common.annotation.KafkaPublisher("test")
            public interface TestProducer extends org.apache.kafka.clients.producer.Producer<@Tag(String.class) String ,byte[]> {}
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_Implementation");
        assertThat(clazz).isNotNull();
        clazz.getConstructor(KafkaProducerTelemetryFactory.class, PublisherConfig.class, Serializer.class, Serializer.class);
    }

    @Test
    public void testTransactionalProducer() throws NoSuchMethodException {
        this.compile(List.of(new KafkaPublisherAnnotationProcessor()), """
            @ru.tinkoff.kora.kafka.common.annotation.KafkaPublisher("test")
            public interface TestProducer extends ru.tinkoff.kora.kafka.common.producer.TransactionalProducer<byte[] ,byte[]> {}
            """);
        this.compileResult.assertSuccess();
        var clazz = this.compileResult.loadClass("$TestProducer_Implementation");
        assertThat(clazz).isNotNull();
        clazz.getConstructor(KafkaProducerTelemetryFactory.class, PublisherConfig.class, Serializer.class, Serializer.class);
    }
}
