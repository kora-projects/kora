package ru.tinkoff.grpc.client.config;

import java.util.Map;
import java.util.Objects;

public final class DefaultServiceConfig {
    public final Map<String, Object> content;

    public DefaultServiceConfig(Map<String, Object> content) {
        this.content = Objects.requireNonNull(content);
    }
}
