package ru.tinkoff.kora.soap.client.common;

import jakarta.annotation.Nullable;
import java.time.Duration;

public record SoapServiceConfig(String url, @Nullable Duration timeout) {
    public SoapServiceConfig {
        if (timeout == null) {
            timeout = Duration.ofSeconds(60);
        }
    }
}
