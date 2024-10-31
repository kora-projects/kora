package ru.tinkoff.kora.camunda.rest.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.Collection;
import java.util.Map;

public interface CamundaRestTracer {

    interface CamundaRestSpan {

        void close(int statusCode, HttpResultCode resultCode, @Nullable Throwable exception);
    }

    interface HeadersSetter<H> {
        void set(H headers, String key, String value);
    }

    <T> void inject(Context context, T headers, HeadersSetter<T> headersSetter);

    CamundaRestSpan createSpan(String scheme,
                               String host,
                               String method,
                               String path,
                               String pathTemplate,
                               HttpHeaders headers,
                               Map<String, ? extends Collection<String>> queryParams,
                               HttpBodyInput body);
}
