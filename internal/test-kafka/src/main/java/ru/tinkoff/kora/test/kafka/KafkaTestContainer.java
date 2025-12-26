package ru.tinkoff.kora.test.kafka;

import org.apache.kafka.clients.admin.DeleteTopicsOptions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class KafkaTestContainer implements AfterEachCallback, TestInstancePostProcessor, BeforeEachCallback {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(KafkaTestContainer.class);
    private static volatile KafkaContainer container = null;
    private static volatile KafkaParams params = null;
    private static volatile Exception initException;

    private static synchronized void init() throws Exception {
        if (params != null) {
            return;
        }
        if (initException != null) {
            throw initException;
        }
        try {
            var params = fromEnv();
            if (params != null) {
                awaitForReady(params);
                KafkaTestContainer.params = params;
                return;
            }
            if (container == null) {
                container = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"))
                    .withExposedPorts(9092, 9093)
                    .waitingFor(Wait.forListeningPort())
                ;
                container.start();
            }

            KafkaTestContainer.params = new KafkaParams(container.getBootstrapServers(), "", new HashSet<>());
        } catch (Exception e) {
            initException = e;
        }
    }

    private static void awaitForReady(KafkaParams params) {
        var start = System.currentTimeMillis();
        var lastEx = (Exception) null;
        while (System.currentTimeMillis() - start < 120_000) {
            try {
                params.withAdmin(a -> {
                    try {
                        a.listTopics().names().get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                return;
            } catch (Exception e) {
                lastEx = e;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    throw e;
                }
            }
        }
        throw new RuntimeException(lastEx);
    }

    public static KafkaParams getParams() {
        try {
            init();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return params;
    }

    @Nullable
    private static KafkaParams fromEnv() {
        var bootstrapServers = System.getenv("TEST_KAFKA_BOOTSTRAP_SERVERS");
        if (bootstrapServers == null) {
            return null;
        }
        return new KafkaParams(bootstrapServers, "", new HashSet<>());
    }


    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        var params = context.getStore(NAMESPACE).get(KafkaTestContainer.class, KafkaParams.class);
        if (params != null) {
            getParams().withAdmin(a -> {
                try {
                    a.deleteTopics(params.createdTopics(), new DeleteTopicsOptions().timeoutMs(1000)).all().get();
                } catch (InterruptedException | ExecutionException e) {
                }
            });
            context.getStore(NAMESPACE).remove(context.getRequiredTestMethod(), KafkaParams.class);
        }
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
        for (var declaredField : testInstance.getClass().getDeclaredFields()) {
            if (declaredField.getType().equals(KafkaParams.class)) {
                declaredField.setAccessible(true);
                var p = context.getStore(NAMESPACE).getOrComputeIfAbsent(KafkaTestContainer.class, k -> {
                    var params = getParams();
                    return params.withTopicPrefix(UUID.randomUUID().toString().replace("-", ""));
                }, KafkaParams.class);
                declaredField.set(testInstance, p);
            }
        }
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        extensionContext.getStore(NAMESPACE).getOrComputeIfAbsent(KafkaTestContainer.class, p -> {
            var params = getParams();
            return params.withTopicPrefix(UUID.randomUUID().toString().replace("-", ""));
        }, KafkaParams.class);
    }
}
