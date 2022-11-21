package ru.tinkoff.kora.http.client.common.declarative;

import javax.annotation.Nullable;
import java.time.Duration;

public record HttpClientOperationConfig(int requestTimeout) {
    public HttpClientOperationConfig(
        @Nullable Duration requestTimeout
    ) {
        this(requestTimeout != null ? (int) requestTimeout.toMillis() : -1);
    }
}
