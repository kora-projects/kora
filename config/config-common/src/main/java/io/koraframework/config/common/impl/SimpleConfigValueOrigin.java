package io.koraframework.config.common.impl;

import io.koraframework.config.common.ConfigValueOrigin;
import io.koraframework.config.common.ConfigValuePath;
import io.koraframework.config.common.origin.ConfigOrigin;

public record SimpleConfigValueOrigin(ConfigOrigin config, ConfigValuePath path) implements ConfigValueOrigin {
}
