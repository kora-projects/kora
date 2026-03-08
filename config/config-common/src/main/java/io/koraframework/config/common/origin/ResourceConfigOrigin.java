package io.koraframework.config.common.origin;

import java.net.URL;

public record ResourceConfigOrigin(URL url) implements ConfigOrigin {

    @Override
    public String description() {
        return "Resource " + url;
    }
}
