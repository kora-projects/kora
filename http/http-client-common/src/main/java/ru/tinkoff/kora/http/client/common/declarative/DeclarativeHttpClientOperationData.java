package ru.tinkoff.kora.http.client.common.declarative;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.client.common.HttpClient;

import java.time.Duration;

public record DeclarativeHttpClientOperationData(HttpClient client, String url, @Nullable Duration requestTimeout) {

}
