package ru.tinkoff.kora.kafka.common.config;

import org.apache.kafka.clients.CommonClientConfigs;
import ru.tinkoff.kora.common.util.Either;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

public record KafkaConsumerConfig(
    Properties driverProperties,
    @Nullable List<String> topics,
    @Nullable Pattern topicsPattern,
    @Nullable List<String> partitions,
    Either<Duration, String> offset,
    Duration pollTimeout,
    Duration backoffTimeout,
    int threads,
    Duration partitionRefreshInterval
) {
    private static final Duration DEFAULT_POLL_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_BACKOFF_TIMEOUT = Duration.ofSeconds(15);
    private static final int DEFAULT_THREADS_COUNT = 1;
    private static final Duration DEFAULT_REFRESH_TIMEOUT = Duration.ofMinutes(1);
    private static final Either<Duration, String> DEFAULT_OFFSET = Either.right("latest");

    public KafkaConsumerConfig(
        Properties driverProperties,
        @Nullable List<String> topics,
        @Nullable Pattern topicsPattern,
        @Nullable List<String> partitions,
        @Nullable Either<Duration, String> offset,
        @Nullable Duration pollTimeout,
        @Nullable Duration backoffTimeout,
        @Nullable Integer threads,
        @Nullable Duration partitionRefreshInterval
    ) {
        this(
            driverProperties,
            topics,
            topicsPattern,
            partitions,
            offset == null ? DEFAULT_OFFSET : offset,
            pollTimeout == null ? DEFAULT_POLL_TIMEOUT : pollTimeout,
            backoffTimeout == null ? DEFAULT_BACKOFF_TIMEOUT : backoffTimeout,
            threads == null ? DEFAULT_THREADS_COUNT : threads,
            partitionRefreshInterval == null ? DEFAULT_REFRESH_TIMEOUT : partitionRefreshInterval
        );
    }

    public KafkaConsumerConfig {
        if (topics == null && topicsPattern == null && partitions == null) {
            throw new IllegalArgumentException("`topics` or `topicsPattern` or `partitions` must be specified");
        }
        if (driverProperties.getProperty(CommonClientConfigs.GROUP_ID_CONFIG) == null) {
            if (topics == null) {
                throw new IllegalArgumentException("`topics` or `group.id` must be specified");
            }
            if (topics.size() != 1) {
                throw new IllegalArgumentException("`topics` must contain exactly one element if `group.id` is not specified");
            }
        }
    }

    public KafkaConsumerConfig withDriverPropertiesOverrides(Map<String, Object> overrides) {
        var props = new Properties();
        props.putAll(driverProperties);
        props.putAll(overrides);
        return new KafkaConsumerConfig(
            props,
            topics,
            topicsPattern,
            partitions,
            offset,
            pollTimeout,
            backoffTimeout,
            threads,
            partitionRefreshInterval
        );
    }
}
