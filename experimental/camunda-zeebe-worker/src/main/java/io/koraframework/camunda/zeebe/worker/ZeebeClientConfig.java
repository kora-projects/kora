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

    /**
     * @return Maximum number of threads for job workers.
     */
    default int executionThreads() {
        return Math.max(Runtime.getRuntime().availableProcessors() * 2, 2);
    }

    /**
     * @return Time without read activity before sending a KeepAlive check.
     */
    default Duration keepAlive() {
        return Duration.ofSeconds(45);
    }

    /**
     * @return File path to the connection certificate, when not specified the system certificate is used.
     */
    @Nullable
    String certificatePath();

    /**
     * @return Maximum time to wait for the topology availability check on client startup.
     */
    @Nullable
    Duration initializationFailTimeout();

    @Nullable
    /**
     * @return gRPC connection configuration.
     */
    GrpcConfig grpc();

    RestConfig rest();

    /**
     * @return Resource deployment configuration.
     */
    DeploymentConfig deployment();

    ZeebeWorkerTelemetryConfig telemetry();

    @ConfigMapper
    interface RestConfig {

        /**
         * @return URL for connecting to the Zeebe REST address.
         */
        String url();
    }

    @ConfigMapper
    interface GrpcConfig {

        String url();

        /**
         * @return How long a message sent through gRPC is kept on the broker.
         */
        default Duration ttl() {
            return Duration.ofHours(1);
        }

        /**
         * @return Maximum inbound message size for gRPC.
         */
        default Size maxMessageSize() {
            return Size.of(4, Size.Type.MiB);
        }

        /**
         * @return Retry policy configuration of the gRPC connection.
         */
        GrpcRetryConfig retryPolicy();

        GrpcClientTelemetryConfig telemetry();
    }

    @ConfigMapper
    interface GrpcRetryConfig {

        /**
         * @return Whether the retry policy for the gRPC connection is enabled.
         */
        default boolean enabled() {
            return true;
        }

        /**
         * @return Number of retry attempts.
         */
        default int attempts() {
            return 5;
        }

        /**
         * @return Initial delay between attempts.
         */
        default Duration delay() {
            return Duration.ofMillis(100);
        }

        /**
         * @return Maximum delay between attempts.
         */
        default Duration delayMax() {
            return Duration.ofSeconds(5);
        }

        /**
         * @return Delay multiplier between attempts.
         */
        default Double step() {
            return 3.0;
        }
    }

    @ConfigMapper
    interface DeploymentConfig {

        /**
         * @return Maximum time to wait for resource upload.
         */
        default Duration timeout() {
            return Duration.ofSeconds(45);
        }

        /**
         * @return Paths where resources uploaded to the orchestrator after startup are searched for, only the classpath: prefix is supported.
         */
        default List<String> resources() {
            return Collections.emptyList();
        }
    }
}
