package ru.tinkoff.kora.config.common;

import ru.tinkoff.kora.config.common.impl.ConfigResolver;
import ru.tinkoff.kora.config.common.origin.ConfigOrigin;

/**
 * <b>Русский</b>: Базовое предоставления конфигурации в Kora.
 * <hr>
 * <b>English</b>: Basic configuration representation in Kora.
 */
public interface Config {

    ConfigOrigin origin();

    ConfigValue.ObjectValue root();

    default Config resolve() {
        return ConfigResolver.resolve(this);
    }

    default ConfigValue<?> get(ConfigValuePath path) {
        return ConfigHelper.get(this, path);
    }

    default ConfigValue<?> get(String path) {
        return this.get(ConfigValuePath.parse(path));
    }
}
