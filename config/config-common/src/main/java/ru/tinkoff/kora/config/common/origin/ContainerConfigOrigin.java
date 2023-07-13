package ru.tinkoff.kora.config.common.origin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ContainerConfigOrigin implements ConfigOrigin {
    private final List<ConfigOrigin> origins;
    private final String description;

    public ContainerConfigOrigin(ConfigOrigin origin1, ConfigOrigin origin2) {
        this.origins = new ArrayList<>();
        if (origin1 instanceof ru.tinkoff.kora.config.common.origin.ContainerConfigOrigin container) {
            this.origins.addAll(container.origins());
        } else {
            this.origins.add(origin1);
        }
        if (origin2 instanceof ru.tinkoff.kora.config.common.origin.ContainerConfigOrigin container) {
            this.origins.addAll(container.origins());
        } else {
            this.origins.add(origin2);
        }
        this.description = this.origins.stream()
            .map(ConfigOrigin::description)
            .collect(Collectors.joining(",\n  ", "Container of:\n  ", ""));
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
