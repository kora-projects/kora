package ru.tinkoff.kora.config.common.origin;

import java.net.URL;

public record ResourceConfigOrigin(URL url) implements ConfigOrigin {
    @Override
    public String description() {
        return null;
    }
}
