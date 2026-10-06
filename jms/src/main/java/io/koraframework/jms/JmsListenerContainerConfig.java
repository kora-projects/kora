package io.koraframework.jms;

import io.koraframework.common.util.Size;
import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.jms.telemetry.JmsConsumerTelemetryConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

@ConfigMapper
public interface JmsListenerContainerConfig {

    Size DEFAULT_MAX_BODY_SIZE = Size.of(16, Size.Type.MiB);

    /**
     * @return Name of the JMS queue the listener consumes messages from.
     */
    String queueName();

    /**
     * @return Number of consumer threads listening to the queue, zero disables the listener.
     */
    int threads();

    /**
     * Maximum wait for every worker's first successful receive; null starts asynchronously.
     */
    @Nullable
    default Duration initializationFailTimeout() {
        return null;
    }

    /**
     * Maximum duration of a receive call, without an idle transaction commit.
     */
    default Duration pollTimeout() {
        return Duration.ofSeconds(1);
    }

    /**
     * Initial reconnect delay and delay after a rolled-back handler failure.
     */
    default Duration backoffTimeout() {
        return Duration.ofSeconds(1);
    }

    /**
     * Maximum reconnect delay; repeated connection failures use exponential backoff with jitter.
     */
    default Duration maxBackoffTimeout() {
        return Duration.ofMinutes(1);
    }

    /**
     * Total time available for graceful worker/resource shutdown.
     */
    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }

    /**
     * Readiness requires all configured consumers to be connected when enabled.
     */
    default boolean readinessProbe() {
        return false;
    }

    /**
     * UTF-8 byte limit used by JmsUtils conversions within this listener's synchronous handler.
     */
    default Size maxBodySize() {
        return DEFAULT_MAX_BODY_SIZE;
    }

    /**
     * @return Telemetry configuration for logging, metrics and tracing of consumed messages.
     */
    JmsConsumerTelemetryConfig telemetry();
}
