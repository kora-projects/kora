package ru.tinkoff.kora.camunda.zeebe.worker;

import jakarta.annotation.Nullable;
import ru.tinkoff.grpc.client.telemetry.GrpcClientTelemetryConfig;
import ru.tinkoff.kora.common.util.Size;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@ConfigValueExtractor
public interface ZeebeClientConfig {

    default int executionThreads() {
        return Math.max(Runtime.getRuntime().availableProcessors(), 2);
    }

    default boolean tls() {
        return true;
    }

    default Duration keepAlive() {
        return Duration.ofSeconds(45);
    }

    @Nullable
    String certificatePath();

    @Nullable
    Duration initializationFailTimeout();

    GrpcConfig grpc();

    @Nullable
    RestConfig rest();

    DeploymentConfig deployment();

    GrpcClientTelemetryConfig telemetry();

    @ConfigValueExtractor
    interface RestConfig {

        String url();
    }

    @ConfigValueExtractor
    interface GrpcConfig {

        String url();

        default Duration ttl() {
            return Duration.ofHours(1);
        }

        default Size maxMessageSize() {
            return Size.of(4, Size.Type.MiB);
        }

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
