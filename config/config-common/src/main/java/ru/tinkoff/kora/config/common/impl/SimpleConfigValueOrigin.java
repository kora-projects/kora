package ru.tinkoff.kora.config.common.impl;

import ru.tinkoff.kora.config.common.ConfigValueOrigin;
import ru.tinkoff.kora.config.common.ConfigValuePath;
import ru.tinkoff.kora.config.common.origin.ConfigOrigin;

public record SimpleConfigValueOrigin(ConfigOrigin config, ConfigValuePath path) implements ConfigValueOrigin {
}
