package ru.tinkoff.kora.kafka.annotation.processor;

import com.typesafe.config.Config;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.kafka.common.config.KafkaConsumerConfig;
import ru.tinkoff.kora.kafka.common.containers.KafkaConsumerContainer;
import ru.tinkoff.kora.kafka.common.telemetry.KafkaConsumerTelemetry;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.stream.Collectors;

import static ru.tinkoff.kora.annotation.processor.common.MethodAssertUtils.assertHasMethod;

public class KafkaAnnotationProcessorTest {

    @Test
    void testKafkaAnnotationProcessor() throws Exception {
        var module = createModule(KafkaConsumersComponent.class);
        assertConsumer(module, "processRecord", String.class, String.class);
        assertConsumer(module, "processValue", byte[].class, String.class);
        assertConsumer(module, "processRecords", String.class, String.class);
        assertConsumer(module, "processRecordWithConsumer", String.class, String.class);
        assertConsumer(module, "processRecordsWithConsumer", String.class, String.class);
        assertConsumer(module, "processValueWithException", byte[].class, String.class);
        assertConsumer(module, "processKeyValueWithException", String.class, String.class);
    }

    private void assertConsumer(Class<?> module, String methodName, Type key, Type value) {
        var configName = "kafkaConsumersComponent" + methodName.substring(0, 1).toUpperCase() + methodName.substring(1) + "Config";
        var consumerName = "kafkaConsumersComponent" + methodName.substring(0, 1).toUpperCase() + methodName.substring(1);

        assertHasMethod(module, configName, KafkaConsumerConfig.class,
            Config.class,
            TypeRef.of(ConfigValueExtractor.class, TypeRef.of(KafkaConsumerConfig.class))
        );
        assertHasMethod(module, consumerName, TypeRef.of(KafkaConsumerContainer.class, key, value),
            KafkaConsumersComponent.class,
            KafkaConsumerConfig.class,
            TypeRef.of(Deserializer.class, key),
            TypeRef.of(Deserializer.class, value),
            TypeRef.of(KafkaConsumerTelemetry.class, key, value)
        );
    }

    private String getMethodParameters(Method method) {
        var params = method.getParameters();
        var genericParametersTypes = method.getGenericParameterTypes();
        var result = new StringBuilder("(");
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                result.append(", ");
            }
            var param = params[i];
            var genericParam = genericParametersTypes[i];
            var type = genericParam.getTypeName();
            var prefix = tagsPrefix(param);
            result.append(prefix).append(type);
        }
        return result.append(")").toString();
    }

    private String tagsPrefix(Parameter param) {
        var tag = param.getAnnotation(Tag.class);
        if (tag == null) {
            return "";
        }

        return Arrays.stream(tag.value())
            .map(Class::getSimpleName)
            .collect(Collectors.joining(",", "@", " "));
    }

    private Class<?> createModule(Class<?> targetClass) throws Exception {
        try {
            var classLoader = TestUtils.annotationProcess(targetClass, new KafkaAnnotationProcessor());
            return classLoader.loadClass(targetClass.getName() + "Module");
        } catch (Exception e) {
            if (e.getCause() != null) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }
}

// kafkaConsumersComponentProcessValue(ru.tinkoff.kora.kafka.annotation.processor.KafkaConsumersComponent, @KafkaConsumersComponentProcessValueTag ru.tinkoff.kora.kafka.common.config.KafkaConsumerConfig, org.apache.kafka.common.serialization.Deserializer<byte[]>, org.apache.kafka.common.serialization.Deserializer<byte[]>, ru.tinkoff.kora.kafka.common.telemetry.KafkaConsumerTelemetry<byte[], java.lang.String>):ru.tinkoff.kora.kafka.common.containers.KafkaConsumerContainer<byte[], java.lang.String>
// kafkaConsumersComponentProcessValue(ru.tinkoff.kora.kafka.annotation.processor.KafkaConsumersComponent, @KafkaConsumersComponentProcessValueTag ru.tinkoff.kora.kafka.common.config.KafkaConsumerConfig, org.apache.kafka.common.serialization.Deserializer<byte[]>, org.apache.kafka.common.serialization.Deserializer<java.lang.String>, ru.tinkoff.kora.kafka.common.telemetry.KafkaConsumerTelemetry<byte[], java.lang.String>):ru.tinkoff.kora.kafka.common.containers.KafkaConsumerContainer<byte[], java.lang.String>",
