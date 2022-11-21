package ru.tinkoff.kora.config.common.extractor;

import com.typesafe.config.ConfigValue;

import java.util.UUID;

public class UUIDConfigValueExtractor implements ConfigValueExtractor<UUID> {

    @Override
    public UUID extract(ConfigValue value) {
        return UUID.fromString(value.unwrapped().toString());
    }

}
