package ru.tinkoff.kora.config.annotation.processor.cases;

import ru.tinkoff.kora.config.common.ConfigSource;

@ConfigSource("some.path")
public record ConfigWithConfigSource(String field1, int field2, boolean field3) {}
