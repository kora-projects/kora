package ru.tinkoff.kora.test.kafka;

import org.apache.kafka.clients.admin.DeleteTopicsOptions;
import org.junit.jupiter.api.extension.*;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class KafkaTestContainer implements ParameterResolver, AfterEachCallback {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(KafkaTestContainer.class);
    private static volatile KafkaContainer container = null;
    private static volatile KafkaParams params = null;

    private static synchronized void init() {
        if (params != null) {
            return;
        }
        params = fromEnv();
        if (params != null) {
            awaitForReady(params);
            return;
        }
        if (container == null) {
            container = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"))
                .withExposedPorts(9092, 9093);;

            container.start();
        }

        params = new KafkaParams(container.getBootstrapServers(), "", new HashSet<>());
    }

    private static void awaitForReady(KafkaParams params) {
        var start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 60000) {
            try {
                params.withAdmin(a -> {});
                return;
            } catch (Exception e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    throw e;
                }
            }
        }
    }

    public static KafkaParams getParams() {
        init();
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
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getDeclaringExecutable() instanceof Method && parameterContext.getParameter().getType().equals(KafkaParams.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return extensionContext.getStore(NAMESPACE).getOrComputeIfAbsent(extensionContext.getRequiredTestMethod(), p -> {
            var params = getParams();
            return params.withTopicPrefix(UUID.randomUUID().toString().replace("-", ""));
        }, KafkaParams.class);
    }


    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        var params = context.getStore(NAMESPACE).get(context.getRequiredTestMethod(), KafkaParams.class);
        if (params != null) {
            getParams().withAdmin(a -> {
                try {
                    a.deleteTopics(params.createdTopics(), new DeleteTopicsOptions().timeoutMs(1000)).all().get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
            context.getStore(NAMESPACE).remove(context.getRequiredTestMethod(), KafkaParams.class);
        }
    }
}
