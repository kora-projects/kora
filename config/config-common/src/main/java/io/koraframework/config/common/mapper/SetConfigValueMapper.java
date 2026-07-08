package io.koraframework.config.common.mapper;

import java.util.LinkedHashSet;
import java.util.Set;

public final class SetConfigValueMapper<T> extends CollectionConfigValueMapper<T, Set<T>> {

    public SetConfigValueMapper(ConfigValueMapper<T> elementValueMapper) {
        super(elementValueMapper);
    }

    @Override
    protected Set<T> newCollection(int size) {
        return new LinkedHashSet<>();
    }
}
