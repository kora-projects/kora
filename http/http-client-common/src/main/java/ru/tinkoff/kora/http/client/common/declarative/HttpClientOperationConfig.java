package ru.tinkoff.kora.http.client.common.declarative;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import javax.annotation.Nullable;
import java.time.Duration;

@ConfigValueExtractor
public interface HttpClientOperationConfig {
    @Nullable
    Duration requestTimeout();
}
