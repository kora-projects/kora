package io.koraframework.s3.client.aws;

import io.koraframework.http.client.common.HttpClient;

@FunctionalInterface
public interface AwsS3HttpClientProvider {

    HttpClient get();
}
