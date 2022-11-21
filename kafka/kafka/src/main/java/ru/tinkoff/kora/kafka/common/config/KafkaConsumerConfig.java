package ru.tinkoff.kora.kafka.common.config;

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
    int threads
) {
    private static final Duration DEFAULT_POLL_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_BACKOFF_TIMEOUT = Duration.ofSeconds(15);
    private static final int DEFAULT_THREADS_COUNT = 1;
    private static final Either<Duration, String> DEFAULT_OFFSET = Either.right("latest");

    public KafkaConsumerConfig(
        Properties driverProperties,
        @Nullable List<String> topics,
        @Nullable Pattern topicsPattern,
        @Nullable List<String> partitions,
        @Nullable Either<Duration, String> offset,
        @Nullable Duration pollTimeout,
        @Nullable Duration backoffTimeout,
        @Nullable Integer threads
    ) {
        this(
            driverProperties,
            topics,
            topicsPattern,
            partitions,
            offset == null ? DEFAULT_OFFSET : offset ,
            pollTimeout == null ? DEFAULT_POLL_TIMEOUT : pollTimeout,
            backoffTimeout == null ? DEFAULT_BACKOFF_TIMEOUT : backoffTimeout,
            threads == null ? DEFAULT_THREADS_COUNT : threads
        );

        if (topics == null && topicsPattern == null && partitions == null) throw new IllegalArgumentException("`topics` or `topicsPattern` or `partitions` must be specified");
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
            threads
        );
    }
}
