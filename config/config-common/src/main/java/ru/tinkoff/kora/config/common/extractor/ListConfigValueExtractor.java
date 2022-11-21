package ru.tinkoff.kora.config.common.extractor;

import java.util.ArrayList;
import java.util.List;

public final class ListConfigValueExtractor<T> extends CollectionConfigValueExtractor<T, List<T>> {

    public ListConfigValueExtractor(ConfigValueExtractor<T> elementValueExtractor) {
        super(elementValueExtractor);
    }

    @Override
    protected List<T> newCollection(int size) {
        return new ArrayList<>();
    }

}
