package ru.tinkoff.kora.config.common.origin;

import java.util.List;

public interface ContainerConfigOrigin extends ConfigOrigin {

    List<ConfigOrigin> origins();
}
