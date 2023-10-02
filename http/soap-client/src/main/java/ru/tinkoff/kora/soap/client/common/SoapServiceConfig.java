package ru.tinkoff.kora.soap.client.common;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

@ConfigValueExtractor
public interface SoapServiceConfig {

    String url();

    default Duration timeout() {
        return Duration.ofSeconds(60);
    }
}
