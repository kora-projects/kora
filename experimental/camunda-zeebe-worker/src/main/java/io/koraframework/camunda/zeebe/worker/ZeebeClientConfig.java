package io.koraframework.camunda.zeebe.worker;

import io.koraframework.camunda.zeebe.worker.telemetry.ZeebeWorkerTelemetryConfig;
import io.koraframework.common.util.Size;
import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.grpc.client.telemetry.GrpcClientTelemetryConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@ConfigValueExtractor
public interface ZeebeClientConfig {

    default int executionThreads() {
        return Math.max(Runtime.getRuntime().availableProcessors() * 2, 2);
    }

    default Duration keepAlive() {
        return Duration.ofSeconds(45);
    }

    @Nullable
    String certificatePath();

    @Nullable
    Duration initializationFailTimeout();

    @Nullable
    GrpcConfig grpc();

    RestConfig rest();

    DeploymentConfig deployment();

    ZeebeWorkerTelemetryConfig telemetry();

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

        GrpcClientTelemetryConfig telemetry();
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
