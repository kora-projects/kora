package io.koraframework.nats.common.consumer;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.nats.common.NatsConnectionConfig;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerTelemetryConfig;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.ReplayPolicy;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

@ConfigMapper
public interface NatsListenerConfig extends NatsConnectionConfig {

    /**
     * Non-empty subjects consumed by one listener, including NATS wildcard filters.
     */
    List<String> subjects();

    @Nullable String queue();

    /**
     * Null selects Core NATS.
     */
    @Nullable JetStreamConfig jetStream();

    default int threads() {
        return 1;
    }

    default int batchSize() {
        return 100;
    }

    default Duration pollTimeout() {
        return Duration.ofSeconds(1);
    }

    default Duration backoffTimeout() {
        return Duration.ofSeconds(1);
    }

    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }

    /**
     * Null starts in the background; otherwise wait for all workers to subscribe within this timeout.
     */
    default @Nullable Duration initializationFailTimeout() {
        return null;
    }

    default boolean allowEmptyRecords() {
        return false;
    }

    default long pendingMessages() {
        return 65536;
    }

    default long pendingBytes() {
        return 64 * 1024 * 1024;
    }

    NatsConsumerTelemetryConfig telemetry();

    @ConfigMapper
    interface JetStreamConfig {

        String stream();

        @Nullable String durable();

        default Mode mode() {
            return Mode.PULL;
        }

        /**
         * Bind existing consumer without changing its configuration. Requires durable.
         */
        default boolean bind() {
            return false;
        }

        default Acknowledgement acknowledgement() {
            return Acknowledgement.AUTO;
        }

        default Duration ackTimeout() {
            return Duration.ofSeconds(5);
        }

        default Duration nakDelay() {
            return Duration.ofSeconds(1);
        }

        default AckPolicy ackPolicy() {
            return AckPolicy.Explicit;
        }

        default DeliverPolicy deliverPolicy() {
            return DeliverPolicy.All;
        }

        default ReplayPolicy replayPolicy() {
            return ReplayPolicy.Instant;
        }

        default Duration ackWait() {
            return Duration.ofSeconds(30);
        }

        default long maxDeliver() {
            return -1;
        }

        default long maxAckPending() {
            return 1000;
        }

        /**
         * Native ConsumerConfiguration JSON. Overrides typed consumer defaults.
         */
        @Nullable String consumerConfiguration();
    }

    enum Mode {
        PULL, PUSH
    }

    enum Acknowledgement {
        AUTO, SYNC, MANUAL
    }
}
