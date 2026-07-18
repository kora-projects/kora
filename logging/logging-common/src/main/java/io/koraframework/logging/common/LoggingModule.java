package io.koraframework.logging.common;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Root;
import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.json.common.JsonWriter;
import io.koraframework.json.common.annotation.Json;
import io.koraframework.logging.common.annotation.Mask;
import io.koraframework.logging.common.arg.JsonStructuredArgumentMapper;
import io.koraframework.logging.common.arg.MaskedStructuredArgumentMapper;
import io.koraframework.logging.common.arg.StructuredArgumentMapper;
import io.koraframework.logging.common.masking.MaskingMetadata;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

public interface LoggingModule {

    @Root
    default LoggingLevelRefresher loggingLevelRefresher(LoggingConfig loggingConfig, LoggingLevelApplier loggingLevelApplier) {
        return new LoggingLevelRefresher(loggingConfig, loggingLevelApplier);
    }

    default LoggingConfig loggingConfig(Config config, ConfigValueMapper<LoggingConfig> mapper) {
        return mapper.mapOrThrow(config.get("logging"));
    }

    @DefaultComponent
    default ILoggerFactory loggerFactory() {
        return LoggerFactory.getILoggerFactory();
    }

    @Json
    @DefaultComponent
    default <T> StructuredArgumentMapper<T> jsonStructuredArgumentMapper(JsonWriter<T> writer) {
        return new JsonStructuredArgumentMapper<>(writer);
    }

    @Tag(Mask.class)
    @DefaultComponent
    default <T> StructuredArgumentMapper<T> maskedStructuredArgumentMapper(JsonWriter<T> writer, MaskingMetadata<T> metadata) {
        return new MaskedStructuredArgumentMapper<>(writer, metadata);
    }
}
