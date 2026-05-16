package io.koraframework.http.client.common.declarative;

import io.koraframework.http.client.common.HttpClient;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record DeclarativeHttpClientOperationData(HttpClient client, String url, @Nullable Duration requestTimeout) {

}
