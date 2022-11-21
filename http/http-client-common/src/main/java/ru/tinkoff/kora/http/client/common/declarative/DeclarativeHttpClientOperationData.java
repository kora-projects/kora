package ru.tinkoff.kora.http.client.common.declarative;

import ru.tinkoff.kora.http.client.common.HttpClient;

public record DeclarativeHttpClientOperationData(HttpClient client, String url, int requestTimeout) {

}
