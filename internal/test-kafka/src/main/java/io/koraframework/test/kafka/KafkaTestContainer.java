package io.koraframework.test.kafka;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;

public class KafkaTestContainer implements BeforeAllCallback, BeforeEachCallback, ParameterResolver {

    private static String bootstrapServers;
    private static volatile KafkaContainer kafkaContainer;

    @Override
    public void beforeAll(ExtensionContext context) {
        synchronized (KafkaTestContainer.class) {
            if (kafkaContainer == null) {
                var container = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.3.1"))
                    .withCreateContainerCmdModifier(cmd -> {
                        var hostConfig = cmd.getHostConfig();
                        if (hostConfig != null) {
                            hostConfig.withMemory(256 * 1024 * 1024L);
                            hostConfig.withMemorySwap(0L);
                        }
                    })
                    .withEnv("KAFKA_NUM_PARTITIONS", "1")
                    .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
                    .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                    .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
                    .withEnv("KAFKA_LOG_FLUSH_INTERVAL_MESSAGES", "9223372036854775807")
                    .withEnv("KAFKA_LOG_FLUSH_INTERVAL_MS", "10000")
                    .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                    .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
                ;

                container.start();
                kafkaContainer = container;

                bootstrapServers = container.getBootstrapServers();
                System.setProperty("kafka.bootstrap.servers", bootstrapServers);
            }
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        var testClass = context.getRequiredTestClass();
        var paramsField = findParamsField(testClass);
        if (paramsField != null) {
            paramsField.setAccessible(true);
            var targetInstance = context.getRequiredTestInstance();
            String uniqueId = UUID.randomUUID().toString().substring(0, 8);
            String topicPrefix = "fork-" + uniqueId + "-";

            var params = new KafkaParams(bootstrapServers, topicPrefix, Collections.synchronizedSet(new HashSet<>()));
            paramsField.set(targetInstance, params);
        }
    }

    private Field findParamsField(Class<?> clazz) {
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType() == KafkaParams.class) {
                    return field;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == KafkaParams.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String topicPrefix = "fork-" + System.getProperty("org.gradle.test.worker", "1") + "-" + uniqueId + "-";
        return new KafkaParams(bootstrapServers, topicPrefix, Collections.synchronizedSet(new HashSet<>()));
    }
}
