package ru.tinkoff.kora.camunda.rest;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;

@ConfigValueExtractor
public interface CamundaRestConfig {

    default boolean enabled() {
        return false;
    }

    default String path() {
        return "/engine-rest";
    }

    /**
     * @return Camunda Rest HttpServer port (by default same as {@link HttpServerConfig#publicApiHttpPort()}
     */
    @Nullable
    Integer port();
}
