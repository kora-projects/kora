package io.koraframework.config.common.mapper;

import java.util.ArrayList;
import java.util.List;

public final class ListConfigValueMapper<T> extends CollectionConfigValueMapper<T, List<T>> {

    public ListConfigValueMapper(ConfigValueMapper<T> elementValueMapper) {
        super(elementValueMapper);
    }

    @Override
    protected List<T> newCollection(int size) {
        return new ArrayList<>();
    }
}
