package ru.tinkoff.kora.http.client.common.request;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.MutableHttpHeaders;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

public record DefaultHttpClientRequest(
    String method,
    URI uri,
    String uriTemplate,
    MutableHttpHeaders headers,
    HttpBodyOutput body,
    @Nullable Duration requestTimeout
) implements HttpClientRequest {
    public DefaultHttpClientRequest {
        Objects.requireNonNull(method);
        Objects.requireNonNull(uri);
        Objects.requireNonNull(headers);
        Objects.requireNonNull(body);
    }
}
