package ru.tinkoff.kora.kafka.common.config;

import ru.tinkoff.kora.common.util.Either;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;

public record KafkaAssignConfig(
    @Nullable Either<Duration, String> offset,
    @Nullable List<String> partitions) { }
