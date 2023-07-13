package ru.tinkoff.kora.config.common;

import ru.tinkoff.kora.config.common.impl.ConfigResolver;
import ru.tinkoff.kora.config.common.origin.ConfigOrigin;

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
