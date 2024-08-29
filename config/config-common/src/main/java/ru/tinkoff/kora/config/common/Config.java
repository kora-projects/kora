package ru.tinkoff.kora.config.common;

import ru.tinkoff.kora.config.common.impl.ConfigResolver;
import ru.tinkoff.kora.config.common.origin.ConfigOrigin;

/**
 * <b>Русский</b>: Базовое предоставления конфигурации в Kora.
 * <hr>
 * <b>English</b>: Basic configuration representation in Kora.
 */
public interface Config {

    /**
     * @return <b>Русский</b>: Описание источника происхождения конфигурации
     * <hr>
     * <b>English</b>: Description of the source of origin of the configuration
     */
    ConfigOrigin origin();

    /**
     * @return <b>Русский</b>: Корень древа конфигурации
     * <hr>
     * <b>English</b>: The root of the configuration tree
     */
    ConfigValue.ObjectValue root();

    default Config resolve() {
        return ConfigResolver.resolve(this);
    }

    default ConfigValue<?> get(ConfigValuePath path) {
        return ConfigHelper.get(this, path);
    }

    /**
     * @return <b>Русский</b>: Получение значения конфигурации используя путь как аргумент
     * <hr>
     * <b>English</b>: The root of the configuration tree
     * <br>
     * <br>
     * Пример / Example:
     * <pre>
     * {@code
     * @Json
     * var configValue = config.get("test.config.path")
     * }
     * </pre>
     */
    default ConfigValue<?> get(String path) {
        return this.get(ConfigValuePath.parse(path));
    }
}
