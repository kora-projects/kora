package ru.tinkoff.kora.config.common.impl;

import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.origin.ConfigOrigin;

public record SimpleConfig(ConfigOrigin origin, ConfigValue.ObjectValue root) implements Config {

}
