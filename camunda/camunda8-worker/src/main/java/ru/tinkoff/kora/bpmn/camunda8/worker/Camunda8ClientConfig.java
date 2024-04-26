package ru.tinkoff.kora.bpmn.camunda8.worker;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@ConfigValueExtractor
public interface Camunda8ClientConfig {

    default int executionThreads() {
        return 1;
    }

    default boolean useTls() {
        return true;
    }

    default Duration keepAlive() {
        return Duration.ofSeconds(45);
    }

    default Duration ttl() {
        return Duration.ofHours(1);
    }

    default int maxMessageSize() {
        return 1024 * 1024 * 4; // 4 Mb
    }

    @Nullable
    String certificatePath();

    @Nullable
    Duration initializationFailTimeout();

    GrpcConfig grpc();

    @Nullable
    RestConfig rest();

    TelemetryConfig telemetry();

    DeploymentConfig deployment();

    @ConfigValueExtractor
    interface RestConfig {

        String url();
    }

    @ConfigValueExtractor
    interface GrpcConfig {

        String url();

        GrpcRetryConfig retryPolicy();
    }

    @ConfigValueExtractor
    interface GrpcRetryConfig {

        default boolean enabled() {
            return true;
        }

        default int attempts() {
            return 5;
        }

        default Duration delay() {
            return Duration.ofMillis(100);
        }

        default Duration delayMax() {
            return Duration.ofSeconds(5);
        }

        default Double step() {
            return 3.0;
        }
    }

    @ConfigValueExtractor
    interface DeploymentConfig {

        default Duration timeout() {
            return Duration.ofSeconds(45);
        }

        default List<String> resources() {
            return Collections.emptyList();
        }
    }
}
