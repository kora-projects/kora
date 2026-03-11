package io.koraframework.http.client.common.declarative;

import org.jspecify.annotations.Nullable;
import io.koraframework.http.client.common.HttpClient;

import java.time.Duration;

public record DeclarativeHttpClientOperationData(HttpClient client, String url, @Nullable Duration requestTimeout) {

}
