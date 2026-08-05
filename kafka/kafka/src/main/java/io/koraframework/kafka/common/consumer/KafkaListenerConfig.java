package io.koraframework.kafka.common.consumer;

import org.jspecify.annotations.Nullable;
import io.koraframework.common.Either;
import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerTelemetryConfig;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

@ConfigMapper
public interface KafkaListenerConfig {

    /**
     * @return Official Kafka Consumer properties.
     */
    Properties driverProperties();

    /**
     * @return List of topics the Consumer subscribes to, either topics or topicsPattern must be specified.
     */
    @Nullable
    List<String> topics();

    /**
     * @return Topic pattern the Consumer subscribes to, either topics or topicsPattern must be specified.
     */
    @Nullable
    Pattern topicsPattern();

    /**
     * @return List of partitions used only for consumer name construction when group.id, topics and topicsPattern are not specified.
     */
    @Nullable
    List<String> partitions();

    /**
     * @return Initial read position for the assign strategy when group.id is not specified: earliest, latest or a Duration to shift back from the latest offset.
     */
    default Either<Duration, Offset> offset() {
        return Either.right(Offset.latest);
    }

    /**
     * @return Maximum time to wait for messages from a topic within one poll() call.
     */
    default Duration pollTimeout() {
        return Duration.ofSeconds(5);
    }

    /**
     * @return Initial delay before consumer restart after an unexpected processing error, growing up to 60s on repeated errors.
     */
    default Duration backoffTimeout() {
        return Duration.ofSeconds(15);
    }

    /**
     * @return Number of threads the consumer starts on, 0 means the consumer is not started.
     */
    default int threads() {
        return 1;
    }

    /**
     * @return Partition list refresh period for the assign strategy.
     */
    default Duration partitionRefreshInterval() {
        return Duration.ofMinutes(1);
    }

    /**
     * @return Time to wait for record processing to complete before stopping the consumer during graceful shutdown.
     */
    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }

    /**
     * @return Whether to call the listener with an empty ConsumerRecords batch when the signature accepts ConsumerRecords.
     */
    default boolean allowEmptyRecords() {
        return false;
    }

    @Nullable
    Duration initializationFailTimeout();

    /**
     * @return Telemetry configuration of the consumer: logging, metrics and tracing.
     */
    KafkaConsumerTelemetryConfig telemetry();

    enum Offset {
        latest, earliest
    }

    default KafkaListenerConfig withDriverPropertiesOverrides(Map<String, Object> overrides) {
        var props = new Properties();
        props.putAll(driverProperties());
        props.putAll(overrides);
        return new $KafkaListenerConfig_ConfigValueMapper.KafkaListenerConfig_Impl(
            props,
            topics(),
            topicsPattern(),
            partitions(),
            offset(),
            pollTimeout(),
            backoffTimeout(),
            threads(),
            partitionRefreshInterval(),
            shutdownWait(),
            allowEmptyRecords(),
            initializationFailTimeout(),
            telemetry()
        );
    }
}
