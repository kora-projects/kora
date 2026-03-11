package io.koraframework.config.common;

import io.koraframework.config.common.impl.SimpleConfigValueOrigin;
import io.koraframework.config.common.origin.ConfigOrigin;

public interface ConfigValueOrigin {

    ConfigValuePath path();

    ConfigOrigin config();

    static ConfigValueOrigin of(ConfigOrigin config, ConfigValuePath path) {
        return new SimpleConfigValueOrigin(config, path);
    }

    default ConfigValueOrigin child(PathElement path) {
        return new SimpleConfigValueOrigin(this.config(), this.path().child(path));
    }

    default ConfigValueOrigin child(int path) {
        return new SimpleConfigValueOrigin(this.config(), this.path().child(path));
    }

    default ConfigValueOrigin child(String path) {
        return new SimpleConfigValueOrigin(this.config(), this.path().child(path));
    }
}
