package ru.tinkoff.kora.camunda.rest;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

@ConfigValueExtractor
public interface CamundaRestConfig {

    default boolean enabled() {
        return false;
    }

    default String path() {
        return "/engine-rest";
    }

    default Integer port() {
        return 8090;
    }

    default Duration shutdownWait() {
        return Duration.ofMillis(100);
    }
}
