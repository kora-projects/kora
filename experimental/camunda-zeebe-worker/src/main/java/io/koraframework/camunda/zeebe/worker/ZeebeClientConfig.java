package io.koraframework.camunda.zeebe.worker;

import io.koraframework.camunda.zeebe.worker.telemetry.ZeebeWorkerTelemetryConfig;
import io.koraframework.common.util.Size;
import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.grpc.client.telemetry.GrpcClientTelemetryConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@ConfigMapper
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

    @ConfigMapper
    interface RestConfig {

        String url();
    }

    @ConfigMapper
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

    @ConfigMapper
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

    @ConfigMapper
    interface DeploymentConfig {

        default Duration timeout() {
            return Duration.ofSeconds(45);
        }

        default List<String> resources() {
            return Collections.emptyList();
        }
    }
}
