package ru.tinkoff.kora.http.client.common.response;

import reactor.core.CorePublisher;
import ru.tinkoff.kora.common.Mapping;

public interface HttpClientResponseMapper<T, P extends CorePublisher<T>> extends Mapping.MappingFunction {
    P apply(HttpClientResponse response);
}
