package io.koraframework.s3.client.kora;

import io.koraframework.http.client.common.HttpClient;

@FunctionalInterface
public interface S3HttpClientProvider {

    HttpClient get();
}
