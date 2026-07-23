package ru.tinkoff.kora.camunda.zeebe.worker;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.util.Size;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@ConfigValueExtractor
public interface ZeebeClientConfig {

    /**
     * @return Maximum number of threads for job workers.
     */
    default int executionThreads() {
        return Math.max(Runtime.getRuntime().availableProcessors(), 2);
    }

    /**
     * @return Whether TLS is used for the connection.
     */
    default boolean tls() {
        return true;
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

    /**
     * @return gRPC connection configuration.
     */
    GrpcConfig grpc();

    /**
     * @return REST connection configuration, when specified the client prefers REST over gRPC for supported operations.
     */
    @Nullable
    RestConfig rest();

    /**
     * @return Resource deployment configuration.
     */
    DeploymentConfig deployment();

    /**
     * @return Telemetry configuration of the module.
     */
    TelemetryConfig telemetry();

    @ConfigValueExtractor
    interface RestConfig {

        /**
         * @return URL for connecting to the Zeebe REST address.
         */
        String url();
    }

    @ConfigValueExtractor
    interface GrpcConfig {

        /**
         * @return URL for connecting to Zeebe through gRPC.
         */
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
    }

    @ConfigValueExtractor
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

    @ConfigValueExtractor
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
