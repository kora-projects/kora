package io.koraframework.config.common.origin;

import java.util.ArrayList;
import java.util.List;

public class ContainerConfigOrigin implements ConfigOrigin {
    private final List<ConfigOrigin> origins;
    private final String description;

    public ContainerConfigOrigin(ConfigOrigin origin1, ConfigOrigin origin2) {
        this(List.of(origin1, origin2));
    }

    public ContainerConfigOrigin(List<ConfigOrigin> origins) {
        this.origins = new ArrayList<>();
        for (var origin : origins) {
            if (origin instanceof ContainerConfigOrigin container) {
                this.origins.addAll(container.origins());
            } else {
                this.origins.add(origin);
            }
        }

        var sb = new StringBuilder("Container of:\n  ");
        for (int i = 0; i < this.origins.size(); i++) {
            if (i > 0) {
                sb.append(",\n  ");
            }
            sb.append(this.origins.get(i).description());
        }
        this.description = sb.toString();
    }

    @Override
    public String description() {
        return this.description;
    }

    @Override
    public String toString() {
        return this.description;
    }

    public List<ConfigOrigin> origins() {
        return this.origins;
    }
}
