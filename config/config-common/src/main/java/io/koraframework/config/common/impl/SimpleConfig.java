package io.koraframework.config.common.impl;

import io.koraframework.config.common.Config;
import io.koraframework.config.common.ConfigValue;
import io.koraframework.config.common.origin.ConfigOrigin;

public record SimpleConfig(ConfigOrigin origin, ConfigValue.ObjectValue root) implements Config {

}
