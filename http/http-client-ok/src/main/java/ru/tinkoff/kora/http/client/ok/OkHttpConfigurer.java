package ru.tinkoff.kora.http.client.ok;

import okhttp3.OkHttpClient;

@FunctionalInterface
public interface OkHttpConfigurer {
    OkHttpClient.Builder configure(OkHttpClient.Builder builder);
}
