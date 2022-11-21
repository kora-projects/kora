package ru.tinkoff.kora.config.common.extractor;

import java.util.LinkedHashSet;
import java.util.Set;

public final class SetConfigValueExtractor<T> extends CollectionConfigValueExtractor<T, Set<T>> {

    public SetConfigValueExtractor(ConfigValueExtractor<T> elementValueExtractor) {
        super(elementValueExtractor);
    }

    @Override
    protected Set<T> newCollection(int size) {
        return new LinkedHashSet<>();
    }

}
