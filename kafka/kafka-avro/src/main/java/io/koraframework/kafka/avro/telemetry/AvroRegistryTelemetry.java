package io.koraframework.kafka.avro.telemetry;

import org.jspecify.annotations.Nullable;

/**
 * <b>Русский</b>: Телеметрия слоя Avro сериализации/десериализации: обращения к реестру схем,
 * попадания/промахи кеша схем и ошибки (де)сериализации.
 * <hr>
 * <b>English</b>: Telemetry for the Avro serialization/deserialization layer: Schema Registry lookups,
 * schema cache hits/misses and (de)serialization errors.
 */
public interface AvroRegistryTelemetry {

    String OP_REGISTER = "register";
    String OP_GET_ID = "getId";
    String OP_GET_BY_ID = "getById";
    String OP_SERIALIZE = "serialize";
    String OP_DESERIALIZE = "deserialize";

    /**
     * <b>Русский</b>: Обращение к реестру схем завершено.
     * <hr>
     * <b>English</b>: A Schema Registry lookup finished.
     *
     * @param operation     {@link #OP_REGISTER}, {@link #OP_GET_ID} or {@link #OP_GET_BY_ID}
     * @param success       whether the lookup succeeded
     * @param durationNanos elapsed time in nanoseconds
     */
    void observeRegistryLookup(String operation, boolean success, long durationNanos);

    /**
     * <b>Русский</b>: Доступ к локальному кешу схем.
     * <hr>
     * <b>English</b>: Local schema cache access.
     *
     * @param operation {@link #OP_SERIALIZE} or {@link #OP_DESERIALIZE}
     * @param hit       {@code true} on cache hit, {@code false} on miss
     */
    void observeCache(String operation, boolean hit);

    /**
     * <b>Русский</b>: Ошибка (де)сериализации.
     * <hr>
     * <b>English</b>: A (de)serialization error occurred.
     *
     * @param operation {@link #OP_SERIALIZE} or {@link #OP_DESERIALIZE}
     * @param topic     the topic if known
     */
    void observeError(String operation, @Nullable String topic);
}
